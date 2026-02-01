package org.funfix.tasks.jvm;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.funfix.tasks.jvm.TestSettings.CONCURRENCY_REPEATS;
import static org.funfix.tasks.jvm.TestSettings.TIMEOUT;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AwaitSignalTest {
    @Test
    void singleThreaded() throws InterruptedException, TimeoutException {
        for (int i = 0; i < CONCURRENCY_REPEATS; i++) {
            final var latch = new AwaitSignal();
            latch.signal();
            latch.await(TimeUnit.SECONDS.toMillis(5));
        }
    }

    @Test
    void multiThreaded() throws InterruptedException {
        for (int i = 0; i < CONCURRENCY_REPEATS; i++) {
            final var wasStarted = new CountDownLatch(1);
            final var latch = new AwaitSignal();
            final var hasError = new AtomicBoolean(false);
            final var t = new Thread(() -> {
                try {
                    wasStarted.countDown();
                    latch.await(TimeUnit.SECONDS.toMillis(5));
                } catch (InterruptedException | TimeoutException e) {
                    hasError.set(true);
                    throw new RuntimeException(e);
                }
            });
            t.start();
            wasStarted.await();
            latch.signal();
            t.join(TIMEOUT.toMillis());
            assertFalse(t.isAlive(), "isAlive");
            assertFalse(hasError.get());
        }
    }

    @Test
    void canBeInterrupted() throws InterruptedException {
        for (int i = 0; i < CONCURRENCY_REPEATS; i++) {
            final var latch = new AwaitSignal();
            final var wasInterrupted = new AtomicBoolean(false);
            final var t = new Thread(() -> {
                try {
                    latch.await();
                } catch (InterruptedException e) {
                    wasInterrupted.set(true);
                }
            });
            t.start();
            t.interrupt();
            t.join(TIMEOUT.toMillis());
            assertFalse(t.isAlive(), "isAlive");
            assertTrue(wasInterrupted.get());
        }
    }
}
