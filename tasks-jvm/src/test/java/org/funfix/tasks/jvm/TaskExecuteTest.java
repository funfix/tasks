package org.funfix.tasks.jvm;

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
    void runAsyncWorksForSuccess() throws InterruptedException, TaskCancellationException, ExecutionException {
        final var latch =
            new CountDownLatch(1);
        final var outcomeRef =
            new AtomicReference<@Nullable Outcome<String>>(null);

        final var task = Task.fromBlockingIO(() -> "Hello!");
        task.runAsync(new CompletionCallback<>() {
            @Override
            public void onSuccess(String value) {
                outcomeRef.set(Outcome.success(value));
                latch.countDown();
            }

            @Override
            public void onFailure(Throwable e) {
                outcomeRef.set(Outcome.failure(e));
                latch.countDown();
            }

            @Override
            public void onCancellation() {
                outcomeRef.set(Outcome.cancellation());
                latch.countDown();
            }
        });

        TimedAwait.latchAndExpectCompletion(latch, "latch");
        assertInstanceOf(Outcome.Success.class, outcomeRef.get());
        assertEquals("Hello!", Objects.requireNonNull(outcomeRef.get()).getOrThrow());
    }

    @Test
    void runAsyncWorksForFailure() throws InterruptedException, TaskCancellationException {
        final var latch =
            new CountDownLatch(1);
        final var outcomeRef =
            new AtomicReference<@Nullable Outcome<String>>(null);
        final var expectedError =
            new RuntimeException("Error");

        final var task = Task.<String>fromBlockingIO(() -> {
            throw expectedError;
        });
        task.runAsync(new CompletionCallback<>() {
            @Override
            public void onSuccess(String value) {
                outcomeRef.set(Outcome.success(value));
                latch.countDown();
            }

            @Override
            public void onFailure(Throwable e) {
                outcomeRef.set(Outcome.failure(e));
                latch.countDown();
            }

            @Override
            public void onCancellation() {
                outcomeRef.set(Outcome.cancellation());
                latch.countDown();
            }
        });

        TimedAwait.latchAndExpectCompletion(latch, "latch");
        assertInstanceOf(Outcome.Failure.class, outcomeRef.get());
        try {
            Objects.requireNonNull(outcomeRef.get()).getOrThrow();
            fail("Should have thrown an exception");
        } catch (final ExecutionException e) {
            assertEquals(expectedError, e.getCause());
        }
    }

    @Test
    void runAsyncWorksForCancellation() throws InterruptedException {
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
        final var token = task.runAsync(
            new CompletionCallback<>() {
                @Override
                public void onSuccess(String value) {
                    outcomeRef.set(Outcome.success(value));
                    latch.countDown();
                }

                @Override
                public void onFailure(Throwable e) {
                    outcomeRef.set(Outcome.failure(e));
                    latch.countDown();
                }

                @Override
                public void onCancellation() {
                    outcomeRef.set(Outcome.cancellation());
                    latch.countDown();
                }
            });

        token.cancel();
        TimedAwait.latchAndExpectCompletion(latch, "latch");
        assertInstanceOf(Outcome.Cancellation.class, outcomeRef.get());
    }

    @Test
    void runBlockingStackedIsCancellable() throws InterruptedException, ExecutionException, Fiber.NotCompletedException {
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
                return innerTask.runBlocking();
            } catch (final InterruptedException e) {
                interruptedHits.incrementAndGet();
                throw e;
            }
        });

        final var fiber = task.runFiber();
        TimedAwait.latchAndExpectCompletion(started, "started");

        fiber.cancel();
        fiber.joinBlocking();

        try {
            fiber.getResultOrThrow();
            fail("Should have thrown a CancellationException");
        } catch (TaskCancellationException ignored) {
        }
        assertEquals(2, interruptedHits.get(), "interruptedHits.get");
    }
}
