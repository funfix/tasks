package org.funfix.tasks;

import org.junit.jupiter.api.Test;
import java.util.Objects;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

public class TaskFiberTest {
    @Test
    public void notCompletedException() throws TaskCancellationException, ExecutionException, InterruptedException {
        final var latch = new CountDownLatch(1);
        final var task = Task.fromBlockingIO(() -> {
            latch.await();
            return "Hello, world!";
        });

        final var fiber = task.executeFiber();
        assertNull(fiber.outcome(), "fiber.outcome()");

        latch.countDown();
        fiber.joinBlocking();
        final var outcome = Objects.requireNonNull(fiber.outcome());
        assertEquals("Hello, world!", outcome.getOrThrow());
    }

    @Test
    public void joinAsync() throws InterruptedException, TaskCancellationException, ExecutionException {
        final var fiberStarted = new CountDownLatch(1);
        final var fiberGo = new CountDownLatch(1);
        final var awaitConsumers = new CountDownLatch(3);
        final var fiber = Task
                .fromBlockingIO(() -> {
                    fiberStarted.countDown();
                    fiberGo.await();
                    return "Hello, world!";
                }).executeFiber();

        TimedAwait.latchAndExpectCompletion(fiberStarted, "fiberStarted");
        // Adding multiple consumers
        for (var i = 0; i < 3; i++) {
            fiber.joinAsync(awaitConsumers::countDown);
        }
        fiberGo.countDown();
        TimedAwait.latchAndExpectCompletion(awaitConsumers, "awaitConsumers");
        final var outcome = Objects.requireNonNull(fiber.outcome());
        assertEquals("Hello, world!", outcome.getOrThrow());
    }

    @Test
    public void canFail() throws InterruptedException, TaskCancellationException {
        final TaskFiber<?> fiber = Task
                .fromBlockingIO(() -> {
                    throw new RuntimeException("My Error");
                }).executeFiber();

        fiber.joinBlocking();
        try {
            Objects.requireNonNull(fiber.outcome()).getOrThrow();
            fail("Should have thrown an exception");
        } catch (final ExecutionException ex) {
            assertEquals("My Error", ex.getCause().getMessage());
        }
    }

    @Test
    public void resultIsMemoized() throws InterruptedException, TaskCancellationException, ExecutionException {
        final var fiber = Task
                .fromBlockingIO(() -> ThreadLocalRandom.current().nextInt())
                .executeFiber();

        fiber.joinBlocking();
        final int result = Objects.requireNonNull(fiber.outcome()).getOrThrow();

        fiber.joinBlocking();
        final int result2 = Objects.requireNonNull(fiber.outcome()).getOrThrow();

        assertEquals(result, result2);
    }

    @Test
    public void joinCanBeInterrupted() throws InterruptedException, ExecutionException, TaskCancellationException {
        final var latch = new CountDownLatch(1);
        final var started = new CountDownLatch(1);
        final var fiber = Task
                .fromBlockingIO(() -> TimedAwait.latchNoExpectations(latch))
                .executeFiber();

        final var fiber2 = Task
                .fromBlockingIO(() -> {
                    started.countDown();
                    fiber.joinBlocking();
                    return Objects.requireNonNull(fiber.outcome()).getOrThrow();
                })
                .executeFiber();

        TimedAwait.latchAndExpectCompletion(started, "started");
        fiber2.cancel();
        fiber2.joinBlocking();
        try {
            Objects.requireNonNull(fiber2.outcome()).getOrThrow();
            fail("Should have thrown a CancellationException");
        } catch (final TaskCancellationException ignored) {}

        latch.countDown();
        fiber.joinBlocking();
        assertTrue(Objects.requireNonNull(fiber.outcome()).getOrThrow());
    }
}
