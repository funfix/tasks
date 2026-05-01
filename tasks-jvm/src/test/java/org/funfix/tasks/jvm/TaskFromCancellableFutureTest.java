package org.funfix.tasks.jvm;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.funfix.tasks.jvm.TestSettings.TIMEOUT;
import static org.junit.jupiter.api.Assertions.*;

public class TaskFromCancellableFutureTest {
    @Test
    void cancellationInvokesFutureCancelAndTokenCancel()
        throws InterruptedException, ExecutionException, Fiber.NotCompletedException, TimeoutException {

        final var future = new CompletableFuture<String>();
        final var tokenCancelCount = new AtomicInteger();
        final var tokenCancelled = new CountDownLatch(1);
        final Cancellable token = () -> {
            tokenCancelCount.incrementAndGet();
            tokenCancelled.countDown();
        };

        final var fiber = Task.fromCancellableFuture(() -> new CancellableFuture<>(future, token))
            .runFiber();

        fiber.cancel();
        fiber.joinBlockingTimed(TIMEOUT);

        try {
            fiber.getResultOrThrow();
            fail("Should have thrown a TaskCancellationException");
        } catch (final TaskCancellationException ignored) {}
        TimedAwait.latchAndExpectCompletion(tokenCancelled, "tokenCancelled");
        assertTrue(future.isCancelled(), "Future should have been cancelled");
        assertEquals(1, tokenCancelCount.get());
    }

    @Test
    void cancellationBeforeFutureRegistrationInvokesCleanupImmediately()
        throws InterruptedException, ExecutionException, Fiber.NotCompletedException, TimeoutException {

        final var future = new CompletableFuture<String>();
        final var tokenCancelCount = new AtomicInteger();
        final var tokenCancelled = new CountDownLatch(1);
        final var builderStarted = new CountDownLatch(1);
        final var cancellationIssued = new CountDownLatch(1);
        final var builderReturned = new AtomicBoolean(false);
        final Cancellable token = () -> {
            tokenCancelCount.incrementAndGet();
            tokenCancelled.countDown();
        };
        final ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            final var fiber = Task.fromCancellableFuture(() -> {
                builderStarted.countDown();
                TimedAwait.latchAndExpectCompletion(cancellationIssued, "cancellationIssued");
                builderReturned.set(true);
                return new CancellableFuture<>(future, token);
            }).runFiber(executor);

            TimedAwait.latchAndExpectCompletion(builderStarted, "builderStarted");
            fiber.cancel();
            cancellationIssued.countDown();
            TimedAwait.latchAndExpectCompletion(tokenCancelled, "tokenCancelled");
            fiber.joinBlockingTimed(TIMEOUT);

            try {
                fiber.getResultOrThrow();
                fail("Should have thrown a TaskCancellationException");
            } catch (final TaskCancellationException ignored) {}
            assertTrue(builderReturned.get(), "Builder should have returned after cancellation");
            assertTrue(future.isCancelled(), "Future should have been cancelled");
            assertEquals(1, tokenCancelCount.get());
        } finally {
            executor.shutdownNow();
        }
    }
}
