package org.funfix.tasks.jvm;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@NullMarked
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

    protected <T extends @Nullable Object> Fiber<T> startFiber(Task<T> task) {
        return executor != null ? task.runFiber(executor) : task.runFiber();
    }

    protected <T extends @Nullable Object> Fiber<T> startFiber(DelayedFun<T> builder) {
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
        try {
            fiber.getResultOrThrow();
            fail("Should have thrown a NotCompletedException");
        } catch (Fiber.NotCompletedException ignored) {
        }

        latch.countDown();
        fiber.joinBlocking();
        try {
            final var result = fiber.getResultOrThrow();
            assertEquals("Hello, world!", result);
        } catch (Fiber.NotCompletedException e) {
            throw new RuntimeException(e);
        }
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
        try {
            final var outcome = fiber.getResultOrThrow();
            assertEquals("Hello, world!", outcome);
        } catch (Fiber.NotCompletedException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void canFail() throws InterruptedException, TaskCancellationException, Fiber.NotCompletedException {
        final Fiber<?> fiber = startFiber(
            Task.fromBlockingIO(() -> {
                throw new RuntimeException("My Error");
            })
        );

        fiber.joinBlocking();
        try {
            fiber.getResultOrThrow();
            fail("Should have thrown an exception");
        } catch (final ExecutionException ex) {
            assertEquals("My Error", ex.getCause().getMessage());
        }
    }

    @Test
    public void resultIsMemoized() throws InterruptedException, TaskCancellationException, ExecutionException, Fiber.NotCompletedException {
        final Fiber<@NonNull Integer> fiber = startFiber(
            Task.fromBlockingIO(() -> ThreadLocalRandom.current().nextInt())
        );

        fiber.joinBlocking();
        final int result = fiber.getResultOrThrow();

        fiber.joinBlocking();
        final int result2 = fiber.getResultOrThrow();

        assertEquals(result, result2);
    }

    @Test
    public void joinCanBeInterrupted() throws InterruptedException, ExecutionException, TaskCancellationException, Fiber.NotCompletedException {
        final var latch = new CountDownLatch(1);
        final var started = new CountDownLatch(1);
        final Fiber<@NonNull Boolean> fiber = startFiber(
            Task.fromBlockingIO(() -> {
                TimedAwait.latchNoExpectations(latch);
                return true;
            }));

        final var fiber2 = startFiber(
            Task.fromBlockingIO(() -> {
                started.countDown();
                fiber.joinBlocking();
                return fiber.getResultOrThrow();
            })
        );

        TimedAwait.latchAndExpectCompletion(started, "started");
        fiber2.cancel();
        fiber2.joinBlocking();
        try {
            fiber2.getResultOrThrow();
            fail("Should have thrown a CancellationException");
        } catch (final TaskCancellationException ignored) {
        }

        latch.countDown();
        fiber.joinBlocking();
        assertTrue(fiber.getResultOrThrow());
    }

    @Test
    void concurrencyHappyPathExecuteThenJoinAsync() throws InterruptedException {
        for (int i = 0; i < repeatCount; i++) {
            final var latch = new CountDownLatch(1);
            final boolean[] wasExecuted = {false};
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
            final boolean[] wasExecuted = {false};
            final var fiber = startFiber(Task.fromBlockingIO(() -> wasExecuted[0] = true));
            fiber.joinBlockingTimed(TimedAwait.TIMEOUT);
            assertTrue(wasExecuted[0], "wasExecuted");
        }
    }

    @Test
    void concurrencyHappyPathExecuteThenJoin() throws InterruptedException {
        for (int i = 0; i < repeatCount; i++) {
            final boolean[] wasExecuted = {false};
            final var fiber = startFiber(() -> wasExecuted[0] = true);
            fiber.joinBlocking();
            assertTrue(wasExecuted[0], "wasExecuted");
        }
    }

    @Test
    void happyPathExecuteThenJoinFuture() throws InterruptedException, TimeoutException {
        for (int i = 0; i < repeatCount; i++) {
            final boolean[] wasExecuted = {false};
            final var fiber = startFiber(() -> wasExecuted[0] = true);
            TimedAwait.future(fiber.joinAsync().future());
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
                return 0;
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
                } catch (InterruptedException ignored) {
                }
                return 0;
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
                } catch (InterruptedException ignored) {
                }
                return 0;
            });

            final var listener = new AtomicInteger(0);
            final var token = fiber.joinAsync();
            fiber.joinAsync(onComplete::countDown);
            fiber.joinAsync(onComplete::countDown);

            token.cancellable().cancel();
            try {
                token.future().get(TimedAwait.TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
                fail("Should have been interrupted");
            } catch (java.util.concurrent.CancellationException ignored) {
            }

            fiber.cancel();
            TimedAwait.latchAndExpectCompletion(onComplete, "onComplete");
            assertEquals(0, listener.get(), "listener");
        }
    }

    @Test
    void cancelAfterExecution() throws InterruptedException {
        for (int i = 0; i < repeatCount; i++) {
            final var latch = new CountDownLatch(1);
            final var fiber = startFiber(() -> 0);
            fiber.joinAsync(latch::countDown);
            TimedAwait.latchAndExpectCompletion(latch);
            fiber.cancel();
        }
    }
}

@NullMarked
class FiberWithDefaultExecutorTest extends BaseFiberTest {
}

@NullMarked
class FiberWithVirtualThreadsTest extends BaseFiberTest {
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
            Objects.requireNonNull(task.runBlocking()).matches("tasks-io-virtual-\\d+"),
            "currentThread.name.matches(\"tasks-io-virtual-\\\\d+\")"
        );
    }
}

@NullMarked
class FiberWithPlatformThreadsTest extends BaseFiberTest {
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
            Objects.requireNonNull(task.runBlocking()).matches("tasks-io-platform-\\d+"),
            "currentThread.name.matches(\"tasks-io-platform-\\\\d+\")"
        );
    }
}
