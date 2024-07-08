package org.funfix.tasks;

import org.junit.jupiter.api.Test;
import java.util.Objects;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

public class TaskFiberTest {
    @Test
    public void notCompletedException() throws CancellationException, ExecutionException, InterruptedException {
        final var es = Executors.newCachedThreadPool();
        final var latch = new CountDownLatch(1);
        try {
            final var task = Task.fromBlockingIO(es, () -> {
                latch.await();
                return "Hello, world!";
            });

            final var fiber = task.executeConcurrently();
            assertNull(fiber.outcome(), "fiber.outcome()");

            latch.countDown();
            fiber.joinBlocking();
            final var outcome = Objects.requireNonNull(fiber.outcome());
            assertEquals("Hello, world!", outcome.getOrThrow());
        } finally {
            es.shutdown();
        }
    }

    @Test
    public void joinAsync() throws InterruptedException, CancellationException, ExecutionException {
        final var es = Executors.newCachedThreadPool();
        final var fiberStarted = new CountDownLatch(1);
        final var fiberGo = new CountDownLatch(1);
        final var awaitConsumers = new CountDownLatch(3);
        try {
            final var fiber = Task
                .fromBlockingIO(es, () -> {
                    fiberStarted.countDown();
                    fiberGo.await();
                    return "Hello, world!";
                }).executeConcurrently();

            TimedAwait.latchAndExpectCompletion(fiberStarted, "fiberStarted");
            // Adding multiple consumers
            for (var i = 0; i < 3; i++) {
                fiber.joinAsync(awaitConsumers::countDown);
            }
            fiberGo.countDown();
            TimedAwait.latchAndExpectCompletion(awaitConsumers, "awaitConsumers");
            final var outcome = Objects.requireNonNull(fiber.outcome());
            assertEquals("Hello, world!", outcome.getOrThrow());
        } finally {
            es.shutdown();
        }
    }

    @Test
    public void canFail() throws InterruptedException, CancellationException {
        final var es = Executors.newCachedThreadPool();
        try {
            final Fiber<?> fiber = Task
                .fromBlockingIO(es, () -> {
                    throw new RuntimeException("My Error");
                }).executeConcurrently();

            fiber.joinBlocking();
            try {
                Objects.requireNonNull(fiber.outcome()).getOrThrow();
                fail("Should have thrown an exception");
            } catch (final ExecutionException ex) {
                assertEquals("My Error", ex.getCause().getMessage());
            }
        } finally {
            es.shutdown();
        }
    }

    @Test
    public void resultIsMemoized() throws InterruptedException, CancellationException, ExecutionException {
        final var es = Executors.newCachedThreadPool();
        try {
            final var fiber = Task
                .fromBlockingIO(es, () -> ThreadLocalRandom.current().nextInt())
                .executeConcurrently();

            fiber.joinBlocking();
            final int result = Objects.requireNonNull(fiber.outcome()).getOrThrow();

            fiber.joinBlocking();
            final int result2 = Objects.requireNonNull(fiber.outcome()).getOrThrow();

            assertEquals(result, result2);
        } finally {
            es.shutdown();
        }
    }

    @Test
    public void joinCanBeInterrupted() throws InterruptedException, ExecutionException, CancellationException {
        final var es = Executors.newCachedThreadPool();
        final var latch = new CountDownLatch(1);
        final var started = new CountDownLatch(1);
        try {
            final var fiber = Task
                .fromBlockingIO(es, () -> TimedAwait.latchNoExpectations(latch))
                .executeConcurrently();

            final Fiber<Boolean> fiber2 = Task
                .fromBlockingIO(es, () -> {
                    started.countDown();
                    fiber.joinBlocking();
                    return Objects.requireNonNull(fiber.outcome()).getOrThrow();
                })
                .executeConcurrently();

            TimedAwait.latchAndExpectCompletion(started, "started");
            fiber2.cancel();
            fiber2.joinBlocking();
            try {
                Objects.requireNonNull(fiber2.outcome()).getOrThrow();
                fail("Should have thrown a CancellationException");
            } catch (final CancellationException ignored) {}

            latch.countDown();
            fiber.joinBlocking();
            assertTrue(Objects.requireNonNull(fiber.outcome()).getOrThrow());
        } finally {
            es.shutdown();
        }
    }
}
