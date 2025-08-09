package org.funfix.tasks.jvm;

import java.util.concurrent.*;

import static org.funfix.tasks.jvm.TestSettings.TIMEOUT;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class TimedAwait {
    @SuppressWarnings("ResultOfMethodCallIgnored")
    static void latchNoExpectations(final CountDownLatch latch) throws InterruptedException {
        latch.await(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    }

    static void latchAndExpectCompletion(final CountDownLatch latch) throws InterruptedException {
        latchAndExpectCompletion(latch, "latch");
    }

    static void latchAndExpectCompletion(final CountDownLatch latch, final String name) throws InterruptedException {
        assertTrue(
                latch.await(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS),
                name + ".await"
        );
    }

    static void future(final Future<?> future) throws InterruptedException, TimeoutException {
        try {
            future.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    static void fiberAndExpectCancellation(final Fiber<?> fiber)
        throws InterruptedException {
        try {
            fiber.awaitBlockingTimed(TIMEOUT);
            fail("Fiber should have been cancelled");
        } catch (final TaskCancellationException ignored) {
        } catch (final TimeoutException e) {
            fail("Fiber should have been cancelled", e);
        } catch (final ExecutionException e) {
            throw new RuntimeException(e);
        }
    }
}
