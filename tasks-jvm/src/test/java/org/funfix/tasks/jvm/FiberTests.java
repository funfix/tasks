package org.funfix.tasks.jvm;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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
            assertEquals("My Error", Objects.requireNonNull(ex.getCause()).getMessage());
        }
    }

    @Test
    public void resultIsMemoized() throws InterruptedException, TaskCancellationException, ExecutionException, Fiber.NotCompletedException {
        final Fiber<Integer> fiber = startFiber(
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
        final Fiber<Boolean> fiber = startFiber(
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

    @Test
    void awaitAsyncHappyPath() throws InterruptedException {
        for (int i = 0; i < repeatCount; i++) {
            final var latch = new CountDownLatch(1);
            final var fiber = startFiber(() -> 1);
            final var result = new AtomicInteger(0);
            fiber.awaitAsync(outcome -> {
                if (outcome instanceof Outcome.Success<Integer> success) {
                    //noinspection DataFlowIssue
                    result.set(success.value());
                    latch.countDown();
                } else {
                    throw new IllegalStateException("Unexpected outcome: " + outcome);
                }
            });

            TimedAwait.latchAndExpectCompletion(latch);
            assertEquals(1, result.get());
        }
    }

    @Test
    void awaitAsyncCanFail() throws InterruptedException {
        for (int i = 0; i < repeatCount; i++) {
            final var latch = new CountDownLatch(1);
            final var ex = new RuntimeException("My Error");
            final Fiber<Integer> fiber = startFiber(() -> {
                throw ex;
            });
            final var result = new AtomicReference<@Nullable Throwable>(null);
            fiber.awaitAsync(outcome -> {
                if (outcome instanceof Outcome.Failure<Integer> failure) {
                    result.set(failure.exception());
                    latch.countDown();
                } else {
                    throw new IllegalStateException("Unexpected outcome: " + outcome);
                }
            });

            TimedAwait.latchAndExpectCompletion(latch);
            assertEquals(ex, result.get());
        }
    }

    @Test
    void awaitAsyncSignalsCancellation() throws InterruptedException {
        for (int i = 0; i < repeatCount; i++) {
            final var fiberLatch = new CountDownLatch(1);
            final var wasInterrupted = new AtomicInteger(0);
            final var wasStarted = new CountDownLatch(1);

            final Fiber<String> fiber = startFiber(() -> {
                wasStarted.countDown();
                try {
                    TimedAwait.latchNoExpectations(fiberLatch);
                    throw new IllegalStateException("Should have been interrupted");
                } catch (InterruptedException e) {
                    wasInterrupted.incrementAndGet();
                    throw e;
                }
            });

            final var awaitLatch = new CountDownLatch(1);
            fiber.awaitAsync(outcome -> {
                if (outcome instanceof Outcome.Cancellation<String>) {
                    wasInterrupted.incrementAndGet();
                } else if (outcome instanceof Outcome.Failure<String> failure) {
                    UncaughtExceptionHandler.logOrRethrow(failure.exception());
                }
                awaitLatch.countDown();
            });

            TimedAwait.latchAndExpectCompletion(wasStarted, "wasStarted");
            fiber.cancel();
            TimedAwait.latchAndExpectCompletion(awaitLatch, "awaitLatch");
            assertEquals(2, wasInterrupted.get());
        }
    }

    @Test
    void awaitBlockingHappyPath() throws TaskCancellationException, ExecutionException, InterruptedException {
        for (int i = 0; i < repeatCount; i++) {
            final var fiber = startFiber(() -> 1);
            final var r = fiber.awaitBlocking();
            assertEquals(1, r);
        }
    }

    @Test
    void awaitBlockingFailure() throws TaskCancellationException, InterruptedException {
        for (int i = 0; i < repeatCount; i++) {
            final var ex = new RuntimeException("My Error");
            final Fiber<String> fiber = startFiber(() -> { throw ex; });
            try {
                fiber.awaitBlocking();
                fail("Should have failed");
            } catch (ExecutionException e) {
                assertEquals(ex, e.getCause());
            }
        }
    }

    @Test
    void awaitBlockingSignalsCancellation() throws InterruptedException, ExecutionException {
        for (int i = 0; i < repeatCount; i++) {
            final var latch = new CountDownLatch(1);
            final Fiber<String> fiber = startFiber(() -> {
                TimedAwait.latchNoExpectations(latch);
                return "Boo!";
            });

            fiber.cancel();
            try {
                fiber.awaitBlocking();
                fail("Should have failed");
            } catch (TaskCancellationException ignored) {
            }
        }
    }

    @Test
    void awaitBlockingTimedHappyPath() throws TaskCancellationException, ExecutionException,
        InterruptedException, TimeoutException {

        for (int i = 0; i < repeatCount; i++) {
            final var fiber = startFiber(() -> 1);
            final var r = fiber.awaitBlockingTimed(TimedAwait.TIMEOUT);
            assertEquals(1, r);
        }
    }

    @Test
    void awaitBlockingTimedFailure() throws TaskCancellationException, InterruptedException, TimeoutException {
        for (int i = 0; i < repeatCount; i++) {
            final var ex = new RuntimeException("My Error");
            final Fiber<String> fiber = startFiber(() -> { throw ex; });
            try {
                fiber.awaitBlockingTimed(TimedAwait.TIMEOUT);
                fail("Should have failed");
            } catch (ExecutionException e) {
                assertEquals(ex, e.getCause());
            }
        }
    }


    @Test
    void awaitBlockingTimedSignalsCancellation() throws InterruptedException, ExecutionException, TimeoutException {
        for (int i = 0; i < repeatCount; i++) {
            final var latch = new CountDownLatch(1);
            final Fiber<String> fiber = startFiber(() -> {
                TimedAwait.latchNoExpectations(latch);
                return "Boo!";
            });

            fiber.cancel();
            try {
                fiber.awaitBlockingTimed(TimedAwait.TIMEOUT);
                fail("Should have failed");
            } catch (TaskCancellationException ignored) {
            }
        }
    }

    @Test
    void awaitBlockingTimesOut() throws TaskCancellationException, ExecutionException, InterruptedException {
        final var fiber = startFiber(() -> {
            Thread.sleep(10000);
            return 1;
        });

        try {
            fiber.awaitBlockingTimed(Duration.ofMillis(10));
            fail("Should have timed out");
        } catch (TimeoutException ignored) {
        } finally {
            fiber.cancel();
            try {
                fiber.awaitBlockingTimed(TimedAwait.TIMEOUT);
                fail("Should have been cancelled");
            } catch (TimeoutException e) {
                //noinspection ThrowFromFinallyBlock
                throw new RuntimeException(e);
            } catch (TaskCancellationException ignored) {
            }
        }
    }

    @Test
    void awaitAsyncFutureHappyPath() {
        for (int i = 0; i < repeatCount; i++) {
            final var fiber = startFiber(() -> 1);
            final var future = fiber.awaitAsync().future();
            try {
                assertEquals(1, future.get());
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Test
    void awaitAsyncFutureFailure() throws InterruptedException {
        for (int i = 0; i < repeatCount; i++) {
            final var ex = new RuntimeException("My Error");
            final var fiber = startFiber(() -> {
                throw ex;
            });
            final var future = fiber.awaitAsync().future();
            try {
                future.get();
                fail("Should have failed");
            } catch (ExecutionException e) {
                assertEquals(ex, e.getCause());
            }
        }
    }

    @Test
    void awaitAsyncFutureCanSignalCancellation() throws InterruptedException {
        for (int i = 0; i < repeatCount; i++) {
            final var latch = new CountDownLatch(1);
            final var fiber = startFiber(() -> {
                TimedAwait.latchNoExpectations(latch);
                return "Boo!";
            });

            fiber.cancel();
            final var future = fiber.awaitAsync().future();
            try {
                future.get();
                fail("Should have failed");
            } catch (ExecutionException e) {
                assertInstanceOf(TaskCancellationException.class, e.getCause());
            }
        }
    }
}

class FiberWithDefaultExecutorTest extends BaseFiberTest {
}

class FiberWithVirtualThreadsTest extends BaseFiberTest {
    @BeforeEach
    void setUp() {
        closeable = SysProp.withVirtualThreads(true);
        assumeTrue(VirtualThreads.areVirtualThreadsSupported(), "Java 21+ required");
        executor = TaskExecutors.sharedBlockingIO();
    }

    @Test
    void testVirtualThreads() throws ExecutionException, InterruptedException, TimeoutException {
        final var task = Task
            .fromBlockingIO(() -> Thread.currentThread().getName());
        assertTrue(
            Objects.requireNonNull(task.runBlockingTimed(TimedAwait.TIMEOUT))
                .matches("tasks-io-virtual-\\d+"),
            "currentThread.name.matches(\"tasks-io-virtual-\\\\d+\")"
        );
    }
}

class FiberWithPlatformThreadsTest extends BaseFiberTest {
    @BeforeEach
    void setUp() {
        closeable = SysProp.withVirtualThreads(false);
        assumeFalse(VirtualThreads.areVirtualThreadsSupported(), "Java older than 21 required");
        executor = TaskExecutors.sharedBlockingIO();
    }

    @Test
    void testPlatformThreads() throws ExecutionException, InterruptedException, TimeoutException {
        final var task =
            Task.fromBlockingIO(() -> Thread.currentThread().getName());
        assertTrue(
            Objects.requireNonNull(task.runBlockingTimed(TimedAwait.TIMEOUT))
                .matches("tasks-io-platform-\\d+"),
            "currentThread.name.matches(\"tasks-io-platform-\\\\d+\")"
        );
    }
}
