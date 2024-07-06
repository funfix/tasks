package org.funfix.tasks;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

public class TaskExecuteTest {
    @Test
    void executeBlockingIsCancellable() throws InterruptedException {
        final var started = new CountDownLatch(1);
        final var latch = new CountDownLatch(1);
        final var wasInterrupted = new AtomicBoolean(false);

        final var task = Task.fromBlockingIO(() -> {
            final var innerTask = Task.fromBlockingIO(() -> {
                started.countDown();
                try {
                    latch.await();
                    return "Nooo";
                } catch (final InterruptedException e) {
                    wasInterrupted.set(true);
                    throw e;
                }
            });
            return innerTask.executeBlocking();
        });

        final var fiber = task.executeConcurrently();
        assertTrue(started.await(10, TimeUnit.SECONDS), "started.await");

        fiber.cancel();
        fiber.joinBlocking();

        assertInstanceOf(Outcome.Cancelled.class, fiber.outcome());
        assertTrue(wasInterrupted.get(), "wasInterrupted.get");
    }
}
