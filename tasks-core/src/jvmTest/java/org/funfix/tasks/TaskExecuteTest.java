package org.funfix.tasks;

import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@NullMarked
public class TaskExecuteTest {
    @Test
    void executeAsyncWorksForSuccess() throws InterruptedException, CancellationException, ExecutionException {
        final var latch =
            new CountDownLatch(1);
        final var outcome =
            new AtomicReference<@Nullable Outcome<String>>(null);

        final var task = Task.fromBlockingIO(() -> "Hello!");
        task.executeAsync(new CompletionCallback<>() {
            @Override
            public void onSuccess(final String value) {
                outcome.set(Outcome.succeeded(value));
                latch.countDown();
            }

            @Override
            public void onFailure(@NotNull final Throwable e) {
                outcome.set(Outcome.failed(e));
                latch.countDown();
            }

            @Override
            public void onCancel() {
                outcome.set(Outcome.cancelled());
                latch.countDown();
            }
        });

        TimedAwait.latchAndExpectCompletion(latch, "latch");
        assertInstanceOf(Outcome.Succeeded.class, outcome.get());
        assertEquals("Hello!", Objects.requireNonNull(outcome.get()).getOrThrow());
    }

    @Test
    void executeAsyncWorksForFailure() throws InterruptedException, CancellationException {
        final var latch =
            new CountDownLatch(1);
        final var outcome =
            new AtomicReference<@Nullable Outcome<String>>(null);
        final var expectedError =
            new RuntimeException("Error");

        final var task = Task.<String>fromBlockingIO(() -> { throw expectedError; });
        task.executeAsync(new CompletionCallback<>() {
            @Override
            public void onSuccess(final String value) {
                outcome.set(Outcome.succeeded(value));
                latch.countDown();
            }

            @Override
            public void onFailure(@NotNull final Throwable e) {
                outcome.set(Outcome.failed(e));
                latch.countDown();
            }

            @Override
            public void onCancel() {
                outcome.set(Outcome.cancelled());
                latch.countDown();
            }
        });

        TimedAwait.latchAndExpectCompletion(latch, "latch");
        assertInstanceOf(Outcome.Failed.class, outcome.get());
        try {
            Objects.requireNonNull(outcome.get()).getOrThrow();
            fail("Should have thrown an exception");
        } catch (final ExecutionException e) {
            assertEquals(expectedError, e.getCause());
        }
    }

    @Test
    void executeAsyncWorksForCancellation() throws InterruptedException, CancellationException {
        final var nonTermination =
            new CountDownLatch(1);
        final var latch =
            new CountDownLatch(1);
        final var outcome =
            new AtomicReference<@Nullable Outcome<Object>>(null);

        final var task = Task.fromBlockingIO(() -> {
            nonTermination.await();
            return "Nooo";
        });
        final var token = task.executeAsync(new CompletionCallback<>() {
            @Override
            public void onSuccess(final String value) {
                outcome.set(Outcome.succeeded(value));
                latch.countDown();
            }

            @Override
            public void onFailure(@NotNull final Throwable e) {
                outcome.set(Outcome.failed(e));
                latch.countDown();
            }

            @Override
            public void onCancel() {
                outcome.set(Outcome.cancelled());
                latch.countDown();
            }
        });

        token.cancel();
        TimedAwait.latchAndExpectCompletion(latch, "latch");
        assertInstanceOf(Outcome.Cancelled.class, outcome.get());
    }

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
        TimedAwait.latchAndExpectCompletion(started, "started");

        fiber.cancel();
        fiber.joinBlocking();

        assertInstanceOf(Outcome.Cancelled.class, fiber.outcome());
        assertTrue(wasInterrupted.get(), "wasInterrupted.get");
    }
}
