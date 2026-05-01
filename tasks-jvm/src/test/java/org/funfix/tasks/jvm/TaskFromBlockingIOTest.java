package org.funfix.tasks.jvm;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.funfix.tasks.jvm.TestSettings.TIMEOUT;
import static org.junit.jupiter.api.Assertions.*;

abstract class TaskFromBlockingIOTestBase {
    @Nullable protected Executor executor;
    @Nullable protected AutoCloseable closeable;

    @AfterEach
    void tearDown() throws Exception {
        final AutoCloseable closeable = this.closeable;
        if (closeable != null) {
            this.closeable = null;
            closeable.close();
        }
    }

    abstract void testThreadName(String name);

    @Test
    public void runBlockingDoesNotFork() throws ExecutionException, InterruptedException {
        Objects.requireNonNull(executor);
        final var name = new AtomicReference<>("");
        final var thisName = Thread.currentThread().getName();
        final var r =
            Task.fromBlockingIO(() -> {
                name.set(Thread.currentThread().getName());
                return "Hello, world!";
            }).runBlocking(executor);
        assertEquals("Hello, world!", r);
        assertEquals(thisName, name.get());
    }

    @Test
    public void runBlockingTimedForks() throws ExecutionException, InterruptedException, TimeoutException {
        Objects.requireNonNull(executor);
        final var name = new AtomicReference<>("");
        final var r =
                Task.fromBlockingIO(() -> {
                    name.set(Thread.currentThread().getName());
                    return "Hello, world!";
                }).runBlockingTimed(executor, TIMEOUT);
        assertEquals("Hello, world!", r);
        testThreadName(Objects.requireNonNull(name.get()));
    }

    @Test
    public void canFail() throws InterruptedException {
        Objects.requireNonNull(executor);
        try {
            Task.fromBlockingIO(() -> { throw new RuntimeException("Error"); })
                .runBlocking(executor);
            fail("Should have thrown an exception");
        } catch (final ExecutionException ex) {
            assertEquals("Error", Objects.requireNonNull(ex.getCause()).getMessage());
        }
    }

    @Test
    public void isCancellable() throws InterruptedException, ExecutionException, Fiber.NotCompletedException, TimeoutException {
        Objects.requireNonNull(executor);
        final var wasStarted = new CountDownLatch(1);
        final var wasInterrupted = new CountDownLatch(1);
        final var interrupted = new AtomicBoolean(false);
        @SuppressWarnings("NullAway")
        final var task = Task.fromBlockingIO(() -> {
            wasStarted.countDown();
            try {
                Thread.sleep(5000);
                return null;
            } catch (final InterruptedException e) {
                interrupted.set(true);
                wasInterrupted.countDown();
                throw e;
            }
        });
        final var fiber = task.runFiber(executor);
        TimedAwait.latchAndExpectCompletion(wasStarted, "wasStarted");

        fiber.cancel();
        fiber.joinBlockingTimed(TIMEOUT);
        try {
            fiber.getResultOrThrow();
            fail("Should have thrown a CancellationException");
        } catch (final TaskCancellationException ignored) {}
        TimedAwait.latchAndExpectCompletion(wasInterrupted, "wasInterrupted");
        assertTrue(interrupted.get(), "Blocking thread should have been interrupted");
    }

    @Test
    public void runBlockingSuccessDoesNotLeaveCurrentThreadInterrupted()
        throws ExecutionException, InterruptedException {

        Objects.requireNonNull(executor);
        Thread.interrupted();

        final var result = Task.fromBlockingIO(() -> "Hello, world!")
            .runBlocking(executor);

        assertEquals("Hello, world!", result);
        assertFalse(Thread.currentThread().isInterrupted());
    }

    @Test
    public void cancellationBeforeBlockingWorkStartsIsHandled()
        throws InterruptedException, ExecutionException, Fiber.NotCompletedException, TimeoutException {

        final var gate = new CountDownLatch(1);
        final var blockingWorkStarted = new AtomicBoolean(false);
        final ExecutorService queuedExecutor = Executors.newSingleThreadExecutor();
        try {
            final Future<?> blocker = queuedExecutor.submit(() -> {
                try {
                    TimedAwait.latchAndExpectCompletion(gate, "gate");
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            final var fiber = Task.fromBlockingIO(() -> {
                blockingWorkStarted.set(true);
                return "unexpected";
            }).runFiber(queuedExecutor);

            fiber.cancel();
            gate.countDown();
            TimedAwait.future(blocker);
            fiber.joinBlockingTimed(TIMEOUT);

            try {
                fiber.getResultOrThrow();
                fail("Should have thrown a TaskCancellationException");
            } catch (final TaskCancellationException ignored) {}
            assertFalse(blockingWorkStarted.get(), "Blocking work should not have started");
        } finally {
            queuedExecutor.shutdownNow();
        }
    }
}

final class TaskFromBlockingWithExecutorIOTest extends TaskFromBlockingIOTestBase {
    @Override
    void testThreadName(final String name) {
        assertTrue(
                name.matches("^es-sample-\\d+$"),
                "name.matches(es-sample-)"
        );
    }

    @BeforeEach
    @SuppressWarnings("deprecation")
    void setup() {
        final ExecutorService es = Executors.newCachedThreadPool(r -> {
            final var th = new Thread(r);
            th.setName("es-sample-" + th.getId());
            return th;
        });
        this.closeable = es::shutdown;
        this.executor = es;
    }
}

final class TaskFromBlockingWithSharedExecutorTest extends TaskFromBlockingIOTestBase {
    @Override
    void testThreadName(String name) {}

    @BeforeEach
    void setup() {
        this.executor = TaskExecutors.sharedBlockingIO();
    }
}
