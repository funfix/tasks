package org.funfix.tasks.jvm;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Objects;
import java.util.concurrent.*;
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
    public void isCancellable() throws InterruptedException, ExecutionException, Fiber.NotCompletedException {
        Objects.requireNonNull(executor);
        final var latch = new CountDownLatch(1);
        @SuppressWarnings("NullAway")
        final var task = Task.fromBlockingIO(() -> {
            latch.countDown();
            Thread.sleep(30000);
            return null;
        });
        final var fiber = task.runFiber(executor);
        TimedAwait.latchAndExpectCompletion(latch, "latch");

        fiber.cancel();
        fiber.joinBlocking();
        try {
            fiber.getResultOrThrow();
            fail("Should have thrown a CancellationException");
        } catch (final TaskCancellationException ignored) {}
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
