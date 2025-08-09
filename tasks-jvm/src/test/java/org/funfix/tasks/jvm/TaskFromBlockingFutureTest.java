package org.funfix.tasks.jvm;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class TaskFromBlockingFutureTest {
    @Nullable
    ExecutorService es;

    @BeforeEach
    @SuppressWarnings("deprecation")
    void setup() {
        es = Executors.newCachedThreadPool(r -> {
            final var th = new Thread(r);
            th.setName("es-sample-" + th.getId());
            th.setDaemon(true);
            return th;
        });
    }

    @AfterEach
    void tearDown() {
        Objects.requireNonNull(es).shutdown();
    }

    @Test
    void runBlockingDoesNotFork() throws ExecutionException, InterruptedException {
        Objects.requireNonNull(es);

        final var name = new AtomicReference<>("");
        final var thisName = Thread.currentThread().getName();
        final var task = Task.fromBlockingFuture(() -> {
            name.set(Thread.currentThread().getName());
            return Objects.requireNonNull(es).submit(() -> "Hello, world!");
        });

        final var r = task.runBlocking();
        assertEquals("Hello, world!", r);
        assertEquals(thisName, name.get());
    }

    @Test
    void runBlockingTimedForks() throws ExecutionException, InterruptedException, TimeoutException {
        Objects.requireNonNull(es);

        final var name = new AtomicReference<>("");
        final var task = Task.fromBlockingFuture(() -> {
            name.set(Thread.currentThread().getName());
            return Objects.requireNonNull(es).submit(() -> "Hello, world!");
        });

        final var r = task.runBlockingTimed(es, TimedAwait.TIMEOUT);
        assertEquals("Hello, world!", r);
        assertTrue(
            Objects.requireNonNull(name.get()).startsWith("es-sample-"),
            "Expected name to start with 'es-sample-', but was: " + name.get()
        );
    }

    @Test
    void runFiberForks() throws ExecutionException, InterruptedException, TimeoutException, TaskCancellationException {
        Objects.requireNonNull(es);

        final var name = new AtomicReference<>("");
        final var task = Task.fromBlockingFuture(() -> {
            name.set(Thread.currentThread().getName());
            return Objects.requireNonNull(es).submit(() -> "Hello, world!");
        });

        final var r = task.runFiber(es).awaitBlockingTimed(TimedAwait.TIMEOUT);
        assertEquals("Hello, world!", r);
        assertTrue(
            Objects.requireNonNull(name.get()).startsWith("es-sample-"),
            "Expected name to start with 'es-sample-', but was: " + name.get()
        );
    }

    @Test
    void loomHappyPath() throws ExecutionException, InterruptedException, TimeoutException {
        assumeTrue(VirtualThreads.areVirtualThreadsSupported(), "Requires Java 21+");
        Objects.requireNonNull(es);

        final var name = new AtomicReference<>("");
        final var task = Task.fromBlockingFuture(() -> {
            name.set(Thread.currentThread().getName());
            return Objects.requireNonNull(es).submit(() -> "Hello, world!");
        });

        final var r = task.runBlockingTimed(TimedAwait.TIMEOUT);
        assertEquals("Hello, world!", r);
        assertTrue(Objects.requireNonNull(name.get()).startsWith("tasks-io-virtual-"));
    }

    @Test
    void throwExceptionInBuilder() throws InterruptedException {
        Objects.requireNonNull(es);

        try {
            Task.fromBlockingFuture(() -> {
                throw new RuntimeException("Error");
            }).runBlocking();
        } catch (final ExecutionException ex) {
            assertEquals("Error", Objects.requireNonNull(ex.getCause()).getMessage());
        }
    }

    @Test
    void throwExceptionInFuture() throws InterruptedException {
        Objects.requireNonNull(es);
        try {
            Task.fromBlockingFuture(() -> Objects.requireNonNull(es).submit(() -> {
                        throw new RuntimeException("Error");
                    }))
                    .runBlocking();
        } catch (final ExecutionException ex) {
            assertEquals("Error", Objects.requireNonNull(ex.getCause()).getMessage());
        }
    }

    @SuppressWarnings("ReturnOfNull")
    @Test
    void builderCanBeCancelled() throws InterruptedException, ExecutionException, TimeoutException, Fiber.NotCompletedException {
        Objects.requireNonNull(es);

        final var wasStarted = new CountDownLatch(1);
        final var latch = new CountDownLatch(1);

        @SuppressWarnings("NullAway")
        final var fiber = Task
                .fromBlockingFuture(() -> {
                    wasStarted.countDown();
                    try {
                        Thread.sleep(10000);
                        return null;
                    } catch (final InterruptedException e) {
                        latch.countDown();
                        throw e;
                    }
                }).runFiber();

        TimedAwait.latchAndExpectCompletion(wasStarted, "wasStarted");
        fiber.cancel();
        fiber.joinBlockingTimed(TimedAwait.TIMEOUT);

        try {
            fiber.getResultOrThrow();
            fail("Should have thrown a TaskCancellationException");
        } catch (final TaskCancellationException ignored) {
        }
        TimedAwait.latchAndExpectCompletion(latch, "latch");
    }

    @Test
    void futureCanBeCancelled() throws InterruptedException, ExecutionException, TimeoutException, Fiber.NotCompletedException {
        Objects.requireNonNull(es);

        final var latch = new CountDownLatch(1);
        final var wasStarted = new CountDownLatch(1);

        final var fiber = Task
                .fromBlockingFuture(() -> Objects.requireNonNull(es).submit(() -> {
                    wasStarted.countDown();
                    try {
                        Thread.sleep(10000);
                    } catch (final InterruptedException e) {
                        latch.countDown();
                    }
                })).runFiber();

        wasStarted.await();
        fiber.cancel();
        fiber.joinBlockingTimed(TimedAwait.TIMEOUT);

        try {
            fiber.getResultOrThrow();
            fail("Should have thrown a TaskCancellationException");
        } catch (final TaskCancellationException ignored) {
        }
        TimedAwait.latchAndExpectCompletion(latch, "latch");
    }
}
