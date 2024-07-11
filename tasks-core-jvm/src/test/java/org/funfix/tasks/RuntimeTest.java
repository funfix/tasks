package org.funfix.tasks;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@NullMarked
class FiberExecutorFromExecutorTest {
    final int repeatCount = 1000;
    @Nullable AutoCloseable closeable = null;
    @Nullable
    FiberExecutor fiberExecutor = null;

    @BeforeEach
    void setUp() {
        final var es = Executors.newCachedThreadPool(r -> {
            final var t = new Thread(r);
            t.setDaemon(true);
            t.setName("test-thread-" + t.getId());
            return t;
        });
        closeable = es::shutdown;
        fiberExecutor = FiberExecutor.fromExecutor(es);
    }

    @AfterEach
    void tearDown() throws Exception {
        final var c = closeable;
        closeable = null;
        fiberExecutor = null;
        if (c != null) c.close();
    }

    @Test
    void happyPathExecuteThenJoinAsync() throws InterruptedException, TimeoutException {
        final var runtime = Objects.requireNonNull(this.fiberExecutor);

        for (int i = 0; i < repeatCount; i++) {
            final var latch = new CountDownLatch(1);
            final boolean[] wasExecuted = { false };
            final var fiber = runtime.execute(() -> {
                wasExecuted[0] = true;
            });
            fiber.joinAsync(latch::countDown);
            TimedAwait.latchAndExpectCompletion(latch);
            assertTrue(wasExecuted[0], "wasExecuted");
        }
    }

    @Test
    void happyPathExecuteThenJoinTimed() throws InterruptedException, TimeoutException {
        final var runtime = Objects.requireNonNull(this.fiberExecutor);

        for (int i = 0; i < repeatCount; i++) {
            final boolean[] wasExecuted = { false };
            final var fiber = runtime.execute(() -> {
                wasExecuted[0] = true;
            });
            fiber.joinTimed(TimedAwait.TIMEOUT);
            assertTrue(wasExecuted[0], "wasExecuted");
        }
    }

    @Test
    void happyPathExecuteThenJoin() throws InterruptedException, TimeoutException {
        final var runtime = Objects.requireNonNull(this.fiberExecutor);

        for (int i = 0; i < repeatCount; i++) {
            final boolean[] wasExecuted = { false };
            final var fiber = runtime.execute(() -> {
                wasExecuted[0] = true;
            });
            fiber.join();
            assertTrue(wasExecuted[0], "wasExecuted");
        }
    }

    @Test
    void happyPathExecuteThenJoinFuture() throws InterruptedException, TimeoutException {
        final var runtime = Objects.requireNonNull(this.fiberExecutor);

        for (int i = 0; i < repeatCount; i++) {
            final boolean[] wasExecuted = { false };
            final var fiber = runtime.execute(() -> {
                wasExecuted[0] = true;
            });
            TimedAwait.future(fiber.joinAsync().future());
            assertTrue(wasExecuted[0], "wasExecuted");
        }
    }

    @Test
    void interruptedThenJoinAsync() throws InterruptedException, TimeoutException {
        final var runtime = Objects.requireNonNull(this.fiberExecutor);

        for (int i = 0; i < repeatCount; i++) {
            final var awaitCancellation = new CountDownLatch(1);
            final var onCompletion = new CountDownLatch(1);
            final var hits = new AtomicInteger(0);
            final var fiber = runtime.execute(() -> {
                hits.incrementAndGet();
                try {
                    TimedAwait.latchNoExpectations(awaitCancellation);
                } catch (InterruptedException e) {
                    hits.incrementAndGet();
                }
            });
            fiber.joinAsync(onCompletion::countDown);
            fiber.cancel();
            TimedAwait.latchAndExpectCompletion(onCompletion);
            assertTrue(hits.get() == 0 || hits.get() == 2, "hits");
        }
    }

    @Test
    void joinAsyncIsInterruptible() throws InterruptedException, TimeoutException {
        final var runtime = Objects.requireNonNull(this.fiberExecutor);

        for (int i = 0; i < repeatCount; i++) {
            final var onComplete = new CountDownLatch(2);
            final var awaitCancellation = new CountDownLatch(1);
            final var fiber = runtime.execute(() -> {
                try {
                    TimedAwait.latchNoExpectations(awaitCancellation);
                } catch (InterruptedException ignored) {}
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
        final var runtime = Objects.requireNonNull(this.fiberExecutor);

        for (int i = 0; i < repeatCount; i++) {
            final var onComplete = new CountDownLatch(2);
            final var awaitCancellation = new CountDownLatch(1);
            final var fiber = runtime.execute(() -> {
                try {
                    TimedAwait.latchNoExpectations(awaitCancellation);
                } catch (InterruptedException ignored) {}
            });

            final var listener = new AtomicInteger(0);
            final var token = fiber.joinAsync();
            fiber.joinAsync(onComplete::countDown);
            fiber.joinAsync(onComplete::countDown);

            token.cancellable().cancel();
            try {
                token.future().get(TimedAwait.TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
                fail("Should have been interrupted");
            } catch (java.util.concurrent.CancellationException ignored) {}

            fiber.cancel();
            TimedAwait.latchAndExpectCompletion(onComplete, "onComplete");
            assertEquals(0, listener.get(), "listener");
        }
    }

    @Test
    void cancelAfterExecution() throws InterruptedException, TimeoutException {
        final var runtime = Objects.requireNonNull(this.fiberExecutor);

        for (int i = 0; i < repeatCount; i++) {
            final var latch = new CountDownLatch(1);
            final var fiber = runtime.execute(() -> {});
            fiber.joinAsync(latch::countDown);
            TimedAwait.latchAndExpectCompletion(latch);
            fiber.cancel();
        }
    }

}

class FiberExecutorFromThreadFactoryTest extends FiberExecutorFromExecutorTest {
    @BeforeEach
    @Override
    void setUp() {
        this.closeable = null;
        this.fiberExecutor = FiberExecutor.fromThreadFactory(r -> {
            final var t = new Thread(r);
            t.setDaemon(true);
            t.setName("test-thread-factory-" + t.getId());
            return t;
        });
    }

    @AfterEach
    @Override
    void tearDown() {}
}

class FiberExecutorDefaultOlderJavaTest extends FiberExecutorFromExecutorTest {
    @BeforeEach
    @Override
    void setUp() {
        this.closeable = SysProp.withVirtualThreads(false);
        this.fiberExecutor = FiberExecutor.shared();
        assertFalse(VirtualThreads.areVirtualThreadsSupported());
    }
}

class FiberExecutorDefaultJava21Test extends FiberExecutorFromExecutorTest {
    @BeforeEach
    @Override
    void setUp() {
        this.closeable = SysProp.withVirtualThreads(true);
        this.fiberExecutor = FiberExecutor.shared();
        assumeTrue(VirtualThreads.areVirtualThreadsSupported());
    }
}
