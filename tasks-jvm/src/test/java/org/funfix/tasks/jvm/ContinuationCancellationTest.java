package org.funfix.tasks.jvm;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class ContinuationCancellationTest {
    @Test
    void finalizerInvokedExactlyOnceWhenCancelledBeforeCompletion()
        throws InterruptedException {

        final var counter = new AtomicInteger();
        final var registered = new CountDownLatch(1);
        final var task = Task.<String>fromAsync((continuation) -> {
            continuation.invokeOnCancellation(() -> {
                counter.incrementAndGet();
                registered.countDown();
            });
        });

        final var fiber = task.runFiber();
        fiber.cancel();

        TimedAwait.latchAndExpectCompletion(registered, "registered");
        assertEquals(1, counter.get());
    }

    @Test
    void finalizerInvokedImmediatelyWhenRegisteredAfterCancellation() {
        final var continuation = newContinuation();
        final var counter = new AtomicInteger();

        continuation.cancel();
        continuation.invokeOnCancellation(counter::incrementAndGet);

        assertEquals(1, counter.get());
    }

    @Test
    void finalizerNotInvokedAfterSuccess() {
        final var continuation = newContinuation();
        final var counter = new AtomicInteger();

        continuation.onSuccess("done");
        continuation.invokeOnCancellation(counter::incrementAndGet);
        continuation.cancel();

        assertEquals(0, counter.get());
    }

    @Test
    void finalizerNotInvokedAfterFailure() {
        final var continuation = newContinuation();
        final var counter = new AtomicInteger();

        continuation.onFailure(new RuntimeException("boom"));
        continuation.invokeOnCancellation(counter::incrementAndGet);
        continuation.cancel();

        assertEquals(0, counter.get());
    }

    @Test
    void normalCompletionClearsFinalizersSoLaterCancelDoesNotRunCleanup()
        throws Exception {

        final var counter = new AtomicInteger();
        final var task = Task.<String>fromAsync((continuation) -> {
            continuation.invokeOnCancellation(counter::incrementAndGet);
            continuation.onSuccess("done");
        });

        final var fiber = task.runFiber();
        assertEquals("done", fiber.awaitBlockingTimed(TestSettings.TIMEOUT));
        fiber.cancel();

        assertEquals(0, counter.get());
    }

    @Test
    void registrationRacingWithCancellationNeverMissesCleanup()
        throws InterruptedException {

        final var counter = new AtomicInteger();
        final var finalizerInvoked = new CountDownLatch(1);
        final var backgroundStarted = new CountDownLatch(1);
        final var cancelIssued = new CountDownLatch(1);
        final var backgroundDone = new CountDownLatch(1);
        final var backgroundError = new AtomicReference<Throwable>();
        final var task = Task.<String>fromAsync((continuation) -> {
            final var thread = new Thread(() -> {
                try {
                    backgroundStarted.countDown();
                    TimedAwait.latchAndExpectCompletion(cancelIssued, "cancelIssued");
                    continuation.invokeOnCancellation(() -> {
                        counter.incrementAndGet();
                        finalizerInvoked.countDown();
                    });
                } catch (final Throwable e) {
                    backgroundError.set(e);
                } finally {
                    backgroundDone.countDown();
                }
            });
            thread.start();
        });

        final var fiber = task.runFiber();
        TimedAwait.latchAndExpectCompletion(backgroundStarted, "backgroundStarted");
        fiber.cancel();
        cancelIssued.countDown();

        TimedAwait.latchAndExpectCompletion(finalizerInvoked, "finalizerInvoked");
        TimedAwait.latchAndExpectCompletion(backgroundDone, "backgroundDone");
        assertNull(backgroundError.get());
        assertEquals(1, counter.get());
    }

    @Test
    void throwingFinalizerIsHandledWithoutPreventingOthers() {
        final var continuation = newContinuation();
        final var counter = new AtomicInteger();
        final var reportedException = new AtomicReference<Throwable>();
        final var currentThread = Thread.currentThread();
        final var previousHandler = currentThread.getUncaughtExceptionHandler();
        final var thrown = new RuntimeException("boom");

        currentThread.setUncaughtExceptionHandler((thread, error) -> reportedException.set(error));
        try {
            continuation.invokeOnCancellation(counter::incrementAndGet);
            continuation.invokeOnCancellation(() -> {
                throw thrown;
            });

            continuation.cancel();
        } finally {
            currentThread.setUncaughtExceptionHandler(previousHandler);
        }

        assertEquals(1, counter.get());
        assertEquals(thrown, reportedException.get());
    }

    private static CancellableContinuation<String> newContinuation() {
        return new CancellableContinuation<>(
            TaskExecutor.from(Runnable::run),
            new ContinuationCallback<>() {
                @Override
                public void onOutcome(Outcome<String> outcome) {}

                @Override
                public void registerExtraCallback(CompletionCallback<String> extraCallback) {}
            }
        );
    }
}
