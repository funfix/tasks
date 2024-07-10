package org.funfix.tasks;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@NullMarked
public abstract class RuntimeExecuteTest {
    @Nullable
    protected RuntimeExecute runtimeExecute;

    protected int repeatCount = 100;

    @Test
    void testHappy() throws InterruptedException {
        final var runtimeExecute = Objects.requireNonNull(this.runtimeExecute);
        for (int i = 0; i < repeatCount; i++) {
            final boolean[] wasExecuted = { false };
            final var latch = new CountDownLatch(1);
            runtimeExecute.invoke(
                    () -> wasExecuted[0] = true,
                    latch::countDown
            );
            TimedAwait.latchAndExpectCompletion(latch);
            assertTrue(wasExecuted[0], "wasExecuted");
        }
    }

    @Test
    void canBeInterruptedAfterStart() throws InterruptedException {
        final var runtimeExecute = Objects.requireNonNull(this.runtimeExecute);
        for (int i = 0; i < repeatCount; i++) {
            final var wasStarted = new CountDownLatch(1);
            final var awaitCancellation = new CountDownLatch(1);
            final var wasInterrupted = new AtomicBoolean(false);
            final var wasCompleted = new CountDownLatch(1);

            final var token = runtimeExecute.invoke(
                    () -> {
                        try {
                            wasStarted.countDown();
                            TimedAwait.latchNoExpectations(awaitCancellation);
                            fail("Should have been interrupted");
                        } catch (InterruptedException e) {
                            wasInterrupted.set(true);
                        }
                    },
                    wasCompleted::countDown
            );

            TimedAwait.latchAndExpectCompletion(wasStarted);
            token.cancel();
            TimedAwait.latchAndExpectCompletion(wasCompleted);
            assertTrue(wasInterrupted.get(), "wasInterrupted");
        }
    }

    @Test
    void canBeInterruptedConcurrentlyWithStart() throws InterruptedException {
        final var runtimeExecute = Objects.requireNonNull(this.runtimeExecute);
        for (int i = 0; i < repeatCount; i++) {
            final var hits = new AtomicInteger(0);
            final var awaitCancellation = new CountDownLatch(1);
            final var wasCompleted = new CountDownLatch(1);

            final var token = runtimeExecute.invoke(
                    () -> {
                        hits.incrementAndGet();
                        try {
                            TimedAwait.latchNoExpectations(awaitCancellation);
                            fail("Should have been interrupted");
                        } catch (InterruptedException ignored) {
                            hits.incrementAndGet();
                        }
                    },
                    wasCompleted::countDown
            );

            token.cancel();
            TimedAwait.latchAndExpectCompletion(wasCompleted);
            assertTrue(hits.get() == 0 || hits.get() == 2);
        }
    }

}

@NullMarked
class RuntimeExecuteViaThreadFactoryTest extends RuntimeExecuteTest {
    public RuntimeExecuteViaThreadFactoryTest() {
        runtimeExecute = new RuntimeExecuteViaThreadFactory(
                r -> {
                    final var t = new Thread(r);
                    t.setDaemon(true);
                    t.setName("test-thread-" + t.getId());
                    return t;
                }
        );
    }
}

@NullMarked
class RuntimeExecuteViaExecutorTest extends RuntimeExecuteTest {
    @Nullable
    private ExecutorService service;

    @BeforeEach
    void setUp() {
        service = Executors.newCachedThreadPool(r -> {
            final var t = new Thread(r);
            t.setDaemon(true);
            t.setName("test-thread-" + t.getId());
            return t;
        });
        runtimeExecute = new RuntimeExecuteViaExecutor(service);
    }

    @AfterEach
    void tearDown() {
        final var s = service;
        service = null;
        runtimeExecute = null;
        if (s != null) s.shutdown();
    }
}
