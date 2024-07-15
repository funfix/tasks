package org.funfix.tasks;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@NullMarked
public class TaskExecuteTest {
    @Test
    void executeAsyncWorksForSuccess() throws InterruptedException, CancellationException, ExecutionException {
        final var latch =
            new CountDownLatch(1);
        final var outcomeRef =
            new AtomicReference<@Nullable Outcome<String>>(null);

        final var task = Task.fromBlockingIO(() -> "Hello!");
        task.executeAsync((CompletionCallback<String>) outcome -> {
            outcomeRef.set(outcome);
            latch.countDown();
        });

        TimedAwait.latchAndExpectCompletion(latch, "latch");
        assertInstanceOf(Outcome.Succeeded.class, outcomeRef.get());
        assertEquals("Hello!", Objects.requireNonNull(outcomeRef.get()).getOrThrow());
    }

    @Test
    void executeAsyncWorksForFailure() throws InterruptedException, CancellationException {
        final var latch =
            new CountDownLatch(1);
        final var outcomeRef =
            new AtomicReference<@Nullable Outcome<String>>(null);
        final var expectedError =
            new RuntimeException("Error");

        final var task = Task.<String>fromBlockingIO(() -> { throw expectedError; });
        task.executeAsync((CompletionCallback<String>) outcome -> {
            outcomeRef.set(outcome);
            latch.countDown();
        });

        TimedAwait.latchAndExpectCompletion(latch, "latch");
        assertInstanceOf(Outcome.Failed.class, outcomeRef.get());
        try {
            Objects.requireNonNull(outcomeRef.get()).getOrThrow();
            fail("Should have thrown an exception");
        } catch (final ExecutionException e) {
            assertEquals(expectedError, e.getCause());
        }
    }

    @Test
    void executeAsyncWorksForCancellation() throws InterruptedException {
        final var nonTermination =
            new CountDownLatch(1);
        final var latch =
            new CountDownLatch(1);
        final var outcomeRef =
            new AtomicReference<@Nullable Outcome<?>>(null);

        final var task = Task.fromBlockingIO(() -> {
            nonTermination.await();
            return "Nooo";
        });
        final var token = task.executeAsync((CompletionCallback<String>) outcome -> {
            outcomeRef.set(outcome);
            latch.countDown();
        });

        token.cancel();
        TimedAwait.latchAndExpectCompletion(latch, "latch");
        assertInstanceOf(Outcome.Cancelled.class, outcomeRef.get());
    }

    @Test
    void executeBlockingStackedIsCancellable() throws InterruptedException {
        final var started = new CountDownLatch(1);
        final var latch = new CountDownLatch(1);
        final var interruptedHits = new AtomicInteger(0);

        final var task = Task.fromBlockingIO(() -> {
            final var innerTask = Task.fromBlockingIO(() -> {
                started.countDown();
                try {
                    latch.await();
                    return "Nooo";
                } catch (final InterruptedException e) {
                    interruptedHits.incrementAndGet();
                    throw e;
                }
            });
            try {
                return innerTask.executeBlocking();
            } catch (final InterruptedException e) {
                interruptedHits.incrementAndGet();
                throw e;
            }
        });

        final var fiber = task.executeConcurrently();
        TimedAwait.latchAndExpectCompletion(started, "started");

        fiber.cancel();
        fiber.joinBlocking();

        assertInstanceOf(Outcome.Cancelled.class, fiber.outcome());
        assertEquals(2, interruptedHits.get(), "interruptedHits.get");
    }
}
