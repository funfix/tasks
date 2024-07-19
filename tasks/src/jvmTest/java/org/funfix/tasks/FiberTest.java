package org.funfix.tasks;

import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

abstract class BaseFiberTest {
    final int repeatCount = 1000;

    protected @Nullable Executor executor = null;
    protected @Nullable AutoCloseable closeable = null;

    @AfterEach
    final void tearDown() throws Exception {
        if (closeable != null) {
            closeable.close();
            closeable = null;
        }
    }

    protected <T> Fiber<T> startFiber(Task<T> task) {
        return executor != null ? task.executeFiber(executor) : task.executeFiber();
    }

    protected <T> Fiber<T> startFiber(DelayedFun<T> builder) {
        final var task = Task.fromBlockingIO(builder);
        return startFiber(task);
    }

    @Test
    public void notCompletedException() throws TaskCancellationException, ExecutionException, InterruptedException {
        final var latch = new CountDownLatch(1);
        final var task = Task.fromBlockingIO(() -> {
            latch.await();
            return "Hello, world!";
        });

        final var fiber = startFiber(task);
        assertNull(fiber.getOutcome(), "fiber.getOutcome()");

        latch.countDown();
        fiber.joinBlocking();
        final var outcome = Objects.requireNonNull(fiber.getOutcome());
        assertEquals("Hello, world!", outcome.getOrThrow());
    }

    @Test
    public void joinAsync() throws InterruptedException, TaskCancellationException, ExecutionException {
        final var fiberStarted = new CountDownLatch(1);
        final var fiberGo = new CountDownLatch(1);
        final var awaitConsumers = new CountDownLatch(3);
        final var fiber = startFiber(
                Task.fromBlockingIO(() -> {
                    fiberStarted.countDown();
                    fiberGo.await();
                    return "Hello, world!";
                })
        );

        TimedAwait.latchAndExpectCompletion(fiberStarted, "fiberStarted");
        // Adding multiple consumers
        for (var i = 0; i < 3; i++) {
            fiber.joinAsync(awaitConsumers::countDown);
        }
        fiberGo.countDown();
        TimedAwait.latchAndExpectCompletion(awaitConsumers, "awaitConsumers");
        final var outcome = Objects.requireNonNull(fiber.getOutcome());
        assertEquals("Hello, world!", outcome.getOrThrow());
    }

    @Test
    public void canFail() throws InterruptedException, TaskCancellationException {
        final Fiber<?> fiber = startFiber(
                Task.fromBlockingIO(() -> {
                    throw new RuntimeException("My Error");
                })
        );

        fiber.joinBlocking();
        try {
            Objects.requireNonNull(fiber.getOutcome()).getOrThrow();
            fail("Should have thrown an exception");
        } catch (final ExecutionException ex) {
            assertEquals("My Error", ex.getCause().getMessage());
        }
    }

    @Test
    public void resultIsMemoized() throws InterruptedException, TaskCancellationException, ExecutionException {
        final var fiber = startFiber(
                Task.fromBlockingIO(() -> ThreadLocalRandom.current().nextInt())
        );

        fiber.joinBlocking();
        final int result = Objects.requireNonNull(fiber.getOutcome()).getOrThrow();

        fiber.joinBlocking();
        final int result2 = Objects.requireNonNull(fiber.getOutcome()).getOrThrow();

        assertEquals(result, result2);
    }

    @Test
    public void joinCanBeInterrupted() throws InterruptedException, ExecutionException, TaskCancellationException {
        final var latch = new CountDownLatch(1);
        final var started = new CountDownLatch(1);
        final var fiber = startFiber(
                Task.fromBlockingIO(() ->
                        TimedAwait.latchNoExpectations(latch)
                )
        );
        final var fiber2 = startFiber(
                Task.fromBlockingIO(() -> {
                    started.countDown();
                    fiber.joinBlocking();
                    return Objects.requireNonNull(fiber.getOutcome()).getOrThrow();
                })
        );

        TimedAwait.latchAndExpectCompletion(started, "started");
        fiber2.cancel();
        fiber2.joinBlocking();
        try {
            Objects.requireNonNull(fiber2.getOutcome()).getOrThrow();
            fail("Should have thrown a CancellationException");
        } catch (final TaskCancellationException ignored) {}

        latch.countDown();
        fiber.joinBlocking();
        assertTrue(Objects.requireNonNull(fiber.getOutcome()).getOrThrow());
    }

    @Test
    void concurrencyHappyPathExecuteThenJoinAsync() throws InterruptedException {
        for (int i = 0; i < repeatCount; i++) {
            final var latch = new CountDownLatch(1);
            final boolean[] wasExecuted = { false };
            final var fiber = startFiber(
                    Task.fromBlockingIO(() -> wasExecuted[0] = true)
            );
            fiber.joinAsync(latch::countDown);
            TimedAwait.latchAndExpectCompletion(latch);
            assertTrue(wasExecuted[0], "wasExecuted");
        }
    }

    @Test
    void happyPathExecuteThenJoinBlockingTimed() throws InterruptedException, TimeoutException {
        for (int i = 0; i < repeatCount; i++) {
            final boolean[] wasExecuted = { false };
            final var fiber = startFiber(Task.fromBlockingIO(() -> wasExecuted[0] = true));
            fiber.joinBlockingTimed(TimedAwait.TIMEOUT.toMillis());
            assertTrue(wasExecuted[0], "wasExecuted");
        }
    }

    @Test
    void concurrencyHappyPathExecuteThenJoin() throws InterruptedException {
        for (int i = 0; i < repeatCount; i++) {
            final boolean[] wasExecuted = { false };
            final var fiber = startFiber(() -> wasExecuted[0] = true);
            fiber.joinBlocking();
            assertTrue(wasExecuted[0], "wasExecuted");
        }
    }

    @Test
    void happyPathExecuteThenJoinFuture() throws InterruptedException, TimeoutException {
        for (int i = 0; i < repeatCount; i++) {
            final boolean[] wasExecuted = { false };
            final var fiber = startFiber(() -> wasExecuted[0] = true);
            TimedAwait.future(fiber.joinAsync().getFuture());
            assertTrue(wasExecuted[0], "wasExecuted");
        }
    }

    @Test
    void concurrencyInterruptedThenJoinAsync() throws InterruptedException {
        for (int i = 0; i < repeatCount; i++) {
            final var awaitCancellation = new CountDownLatch(1);
            final var onCompletion = new CountDownLatch(1);
            final var hits = new AtomicInteger(0);
            final var fiber = startFiber(() -> {
                hits.incrementAndGet();
                try {
                    TimedAwait.latchNoExpectations(awaitCancellation);
                } catch (InterruptedException e) {
                    hits.incrementAndGet();
                }
                return null;
            });
            fiber.joinAsync(onCompletion::countDown);
            fiber.cancel();
            TimedAwait.latchAndExpectCompletion(onCompletion);
            assertTrue(hits.get() == 0 || hits.get() == 2, "hits");
        }
    }

    @Test
    void joinAsyncIsInterruptible() throws InterruptedException {
        for (int i = 0; i < repeatCount; i++) {
            final var onComplete = new CountDownLatch(2);
            final var awaitCancellation = new CountDownLatch(1);
            final var fiber = startFiber(() -> {
                try {
                    TimedAwait.latchNoExpectations(awaitCancellation);
                } catch (InterruptedException ignored) {}
                return null;
            });

            final var listener = new AtomicInteger(0);
            final var token = fiber.joinAsync(listener::incrementAndGet);
            fiber.joinAsync(onComplete::countDown);
            fiber.joinAsync(onComplete::countDown);

            token.cancel();
            fiber.cancel();
            TimedAwait.latchAndExpectCompletion(onComplete, "onComplete");
            assertEquals(0, listener.get(), "listener");
        }
    }

    @Test
    void joinFutureIsInterruptible() throws InterruptedException, TimeoutException, ExecutionException {
        for (int i = 0; i < repeatCount; i++) {
            final var onComplete = new CountDownLatch(2);
            final var awaitCancellation = new CountDownLatch(1);
            final var fiber = startFiber(() -> {
                try {
                    TimedAwait.latchNoExpectations(awaitCancellation);
                } catch (InterruptedException ignored) {}
                return null;
            });

            final var listener = new AtomicInteger(0);
            final var token = fiber.joinAsync();
            fiber.joinAsync(onComplete::countDown);
            fiber.joinAsync(onComplete::countDown);

            token.getCancellable().cancel();
            try {
                token.getFuture().get(TimedAwait.TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
                fail("Should have been interrupted");
            } catch (java.util.concurrent.CancellationException ignored) {}

            fiber.cancel();
            TimedAwait.latchAndExpectCompletion(onComplete, "onComplete");
            assertEquals(0, listener.get(), "listener");
        }
    }

    @Test
    void cancelAfterExecution() throws InterruptedException {
        for (int i = 0; i < repeatCount; i++) {
            final var latch = new CountDownLatch(1);
            final var fiber = startFiber(() -> null);
            fiber.joinAsync(latch::countDown);
            TimedAwait.latchAndExpectCompletion(latch);
            fiber.cancel();
        }
    }
}

class FiberTestWithDefaultExecutor extends BaseFiberTest {}

class FiberTestWithVirtualThreads extends BaseFiberTest {
    @BeforeEach
    void setUp() {
        closeable = SysProp.withVirtualThreads(true);
        assumeTrue(VirtualThreads.areVirtualThreadsSupported(), "Java 21+ required");
        executor = TaskExecutors.global();
    }

    @Test
    void testVirtualThreads() throws ExecutionException, InterruptedException {
        final var task =
                Task.fromBlockingIO(() -> Thread.currentThread().getName());
        assertTrue(
                task.executeBlocking().matches("tasks-io-virtual-\\d+"),
                "currentThread.name.matches(\"tasks-io-virtual-\\\\d+\")"
        );
    }
}

class FiberTestWithPlatformThreads extends BaseFiberTest {
    @BeforeEach
    void setUp() {
        closeable = SysProp.withVirtualThreads(false);
        assumeFalse(VirtualThreads.areVirtualThreadsSupported(), "Java older than 21 required");
        executor = TaskExecutors.global();
    }

    @Test
    void testPlatformThreads() throws ExecutionException, InterruptedException {
        final var task =
                Task.fromBlockingIO(() -> Thread.currentThread().getName());
        assertTrue(
                task.executeBlocking().matches("tasks-io-platform-\\d+"),
                "currentThread.name.matches(\"tasks-io-platform-\\\\d+\")"
        );
    }
}
