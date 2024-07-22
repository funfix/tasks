package org.funfix.tasks.jvm;

import org.jspecify.annotations.NullMarked;

import java.time.Duration;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertTrue;

@NullMarked
public class TimedAwait {
    public static Duration TIMEOUT;

    static {
        if (System.getenv("CI") != null)
            TIMEOUT = Duration.ofSeconds(20);
        else
            TIMEOUT = Duration.ofSeconds(10);
    }

    static boolean latchNoExpectations(final CountDownLatch latch) throws InterruptedException {
        return latch.await(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
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
}
