package org.funfix.tasks.jvm;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class MutableCancellableTest {

    // ──────────────────────────────────────────────────────────────────
    // Basic cancel() behavior
    // ──────────────────────────────────────────────────────────────────

    @Test
    void cancelInvokesRegisteredToken() {
        final var called = new AtomicInteger(0);
        final var mc = new MutableCancellable(called::incrementAndGet);

        mc.cancel();
        assertEquals(1, called.get());
    }

    @Test
    void cancelIsIdempotent() {
        final var called = new AtomicInteger(0);
        final var mc = new MutableCancellable(called::incrementAndGet);

        mc.cancel();
        mc.cancel();
        mc.cancel();

        assertEquals(1, called.get());
    }

    @Test
    void cancelInvokesAllRegisteredTokens() {
        final var mc = new MutableCancellable();
        final var order = Collections.synchronizedList(new ArrayList<Integer>());

        mc.register(() -> order.add(1));
        mc.register(() -> order.add(2));
        mc.register(() -> order.add(3));

        mc.cancel();

        // Tokens are stored as a stack (prepend), so the cancellation order is
        // the reverse of registration: the last registered is cancelled first.
        assertEquals(List.of(3, 2, 1), order);
    }

    @Test
    void cancelInvokesInitialTokenToo() {
        final var order = Collections.synchronizedList(new ArrayList<Integer>());
        final var mc = new MutableCancellable(() -> order.add(0));

        mc.register(() -> order.add(1));
        mc.register(() -> order.add(2));

        mc.cancel();

        // Stack order: 2, 1, 0 (initial)
        assertEquals(List.of(2, 1, 0), order);
    }

    @Test
    void emptyMutableCancellableCanBeCancelledSafely() {
        final var mc = new MutableCancellable();
        // Should not throw
        mc.cancel();
        mc.cancel();
    }

    // ──────────────────────────────────────────────────────────────────
    // register() behavior
    // ──────────────────────────────────────────────────────────────────

    @Test
    void registerReturnsNonNullHandleInActiveState() {
        final var mc = new MutableCancellable();
        final var handle = mc.register(() -> {});
        assertNotNull(handle);
    }

    @Test
    void registerAfterCancelledInvokesTokenImmediately() {
        final var mc = new MutableCancellable();
        mc.cancel();

        final var called = new AtomicInteger(0);
        final var handle = mc.register(called::incrementAndGet);

        assertEquals(1, called.get());
        // Returns null when already cancelled
        assertNull(handle);
    }

    @Test
    void registerAfterCompletedReturnsNullAndDoesNotInvoke() {
        final var mc = new MutableCancellable();
        mc.complete();

        final var called = new AtomicInteger(0);
        final var handle = mc.register(called::incrementAndGet);

        assertEquals(0, called.get());
        assertNull(handle);
    }

    @Test
    void registerReturnsNullDuringCancelling() throws InterruptedException {
        // A slow token blocks the cancelling thread, allowing us to
        // observe the Cancelling state from another thread.
        final var slowTokenStarted = new CountDownLatch(1);
        final var slowTokenRelease = new CountDownLatch(1);
        final var registrationDone = new CountDownLatch(1);
        final var handleRef = new AtomicReference<@Nullable Cancellable>();
        final var errorRef = new AtomicReference<@Nullable Throwable>();

        final var mc = new MutableCancellable(() -> {
            slowTokenStarted.countDown();
            try {
                TimedAwait.latchAndExpectCompletion(slowTokenRelease, "slowTokenRelease");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // Start cancellation on a separate thread — it will block in the slow token
        final var cancelThread = new Thread(mc::cancel);
        cancelThread.start();

        // Wait until the slow token is actually running
        TimedAwait.latchAndExpectCompletion(slowTokenStarted, "slowTokenStarted");

        // Now register from another thread while in Cancelling state
        final var registerThread = new Thread(() -> {
            try {
                handleRef.set(mc.register(() -> {}));
            } catch (Throwable e) {
                errorRef.set(e);
            } finally {
                registrationDone.countDown();
            }
        });
        registerThread.start();

        // Let registrationDone complete (register should return quickly)
        TimedAwait.latchAndExpectCompletion(registrationDone, "registrationDone");
        assertNull(errorRef.get());
        // During Cancelling state, register returns null (no unregister handle)
        assertNull(handleRef.get());

        // Release the slow token so cancel() completes
        slowTokenRelease.countDown();
        cancelThread.join(TestSettings.TIMEOUT.toMillis());
    }

    // ──────────────────────────────────────────────────────────────────
    // unregister() behavior
    // ──────────────────────────────────────────────────────────────────

    @Test
    void unregisterPreventsTokenFromBeingCancelled() {
        final var mc = new MutableCancellable();
        final var called = new AtomicInteger(0);

        final var handle = mc.register(called::incrementAndGet);
        assertNotNull(handle);

        // Unregister before cancel
        handle.cancel();
        mc.cancel();

        assertEquals(0, called.get());
    }

    @Test
    void unregisterOnlyRemovesTargetToken() {
        final var mc = new MutableCancellable();
        final var calls = Collections.synchronizedList(new ArrayList<String>());

        mc.register(() -> calls.add("A"));
        final var handleB = mc.register(() -> calls.add("B"));
        mc.register(() -> calls.add("C"));

        assertNotNull(handleB);
        handleB.cancel();
        mc.cancel();

        // B should be absent
        assertEquals(List.of("C", "A"), calls);
    }

    @Test
    void unregisterIsIdempotent() {
        final var mc = new MutableCancellable();
        final var called = new AtomicInteger(0);
        final var handle = mc.register(called::incrementAndGet);
        assertNotNull(handle);

        handle.cancel();
        handle.cancel(); // idempotent, should not throw
        mc.cancel();

        assertEquals(0, called.get());
    }

    @Test
    void unregisterAfterCancelIsNoOp() {
        final var mc = new MutableCancellable();
        final var called = new AtomicInteger(0);
        final var handle = mc.register(called::incrementAndGet);
        assertNotNull(handle);

        mc.cancel();
        assertEquals(1, called.get());

        // unregister after cancel is a no-op
        handle.cancel();
        assertEquals(1, called.get());
    }

    @Test
    void unregisterAfterCompleteIsNoOp() {
        final var mc = new MutableCancellable();
        final var called = new AtomicInteger(0);
        final var handle = mc.register(called::incrementAndGet);
        assertNotNull(handle);

        mc.complete();

        // unregister after complete is a no-op
        handle.cancel();
        assertEquals(0, called.get());
    }

    // ──────────────────────────────────────────────────────────────────
    // complete() behavior
    // ──────────────────────────────────────────────────────────────────

    @Test
    void completePreventsCancelFromFiringTokens() {
        final var called = new AtomicInteger(0);
        final var mc = new MutableCancellable(called::incrementAndGet);

        mc.complete();
        mc.cancel();

        assertEquals(0, called.get());
    }

    @Test
    void completeIsIdempotent() {
        final var mc = new MutableCancellable();
        mc.complete();
        mc.complete(); // should not throw
    }

    @Test
    void completeAfterCancelIsNoOp() {
        final var mc = new MutableCancellable();
        mc.cancel();
        mc.complete(); // should not throw
    }

    // ──────────────────────────────────────────────────────────────────
    // Error handling
    // ──────────────────────────────────────────────────────────────────

    @Test
    void throwingTokenDoesNotPreventOtherTokensFromBeingCancelled() {
        final var mc = new MutableCancellable();
        final var order = Collections.synchronizedList(new ArrayList<Integer>());
        final var reportedErrors = Collections.synchronizedList(new ArrayList<Throwable>());

        final var currentThread = Thread.currentThread();
        final var previousHandler = currentThread.getUncaughtExceptionHandler();
        currentThread.setUncaughtExceptionHandler((t, e) -> reportedErrors.add(e));

        try {
            mc.register(() -> order.add(1));
            mc.register(() -> { throw new RuntimeException("boom"); });
            mc.register(() -> order.add(3));

            mc.cancel();
        } finally {
            currentThread.setUncaughtExceptionHandler(previousHandler);
        }

        // Both non-throwing tokens should have been cancelled
        assertEquals(List.of(3, 1), order);
        // The exception should have been reported
        assertEquals(1, reportedErrors.size());
        assertEquals("boom", reportedErrors.get(0).getMessage());
    }

    @Test
    void throwingTokenOnImmediateRegisterAfterCancelIsHandled() {
        final var mc = new MutableCancellable();
        mc.cancel();

        final var reportedErrors = Collections.synchronizedList(new ArrayList<Throwable>());
        final var currentThread = Thread.currentThread();
        final var previousHandler = currentThread.getUncaughtExceptionHandler();
        currentThread.setUncaughtExceptionHandler((t, e) -> reportedErrors.add(e));

        try {
            mc.register(() -> { throw new RuntimeException("late boom"); });
        } finally {
            currentThread.setUncaughtExceptionHandler(previousHandler);
        }

        assertEquals(1, reportedErrors.size());
        assertEquals("late boom", reportedErrors.get(0).getMessage());
    }

    // ──────────────────────────────────────────────────────────────────
    // Concurrent behavior: tokens registered during Cancelling state
    // are cancelled on the cancelling thread
    // ──────────────────────────────────────────────────────────────────

    @Test
    void tokenRegisteredDuringCancellingIsCancelledOnCancellingThread()
            throws InterruptedException {

        // We use latches to orchestrate the following sequence:
        //
        // Cancel thread                  Register thread
        // ─────────────                  ───────────────
        // 1. cancel() starts
        // 2. slow token begins
        //    -> signals slowStarted
        //                                3. waits for slowStarted
        //                                4. register(newToken)
        //                                   -> signals registerDone
        // 5. waits for registerDone
        // 6. slow token completes
        // 7. picks up newToken from
        //    Cancelling state
        // 8. cancels newToken on
        //    THIS (cancel) thread

        final var slowStarted = new CountDownLatch(1);
        final var registerDone = new CountDownLatch(1);
        final var allDone = new CountDownLatch(1);

        final var cancellingThreadRef = new AtomicReference<@Nullable Thread>();
        final var newTokenThreadRef = new AtomicReference<@Nullable Thread>();
        final var errorRef = new AtomicReference<@Nullable Throwable>();

        final var mc = new MutableCancellable(() -> {
            // Record which thread is doing the cancellation
            cancellingThreadRef.set(Thread.currentThread());
            slowStarted.countDown();
            try {
                // Wait until the register thread has registered a new token
                TimedAwait.latchAndExpectCompletion(registerDone, "registerDone");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // The cancelling thread
        final var cancelThread = new Thread(() -> {
            try {
                mc.cancel();
            } catch (Throwable e) {
                errorRef.set(e);
            } finally {
                allDone.countDown();
            }
        }, "cancel-thread");

        // The register thread
        final var registerThread = new Thread(() -> {
            try {
                TimedAwait.latchAndExpectCompletion(slowStarted, "slowStarted");
                mc.register(() -> {
                    // Record which thread invokes this new token's cancel
                    newTokenThreadRef.set(Thread.currentThread());
                });
            } catch (Throwable e) {
                errorRef.set(e);
            } finally {
                registerDone.countDown();
            }
        }, "register-thread");

        cancelThread.start();
        registerThread.start();

        TimedAwait.latchAndExpectCompletion(allDone, "allDone");
        cancelThread.join(TestSettings.TIMEOUT.toMillis());
        registerThread.join(TestSettings.TIMEOUT.toMillis());

        assertNull(errorRef.get(), () -> "Unexpected error: " + errorRef.get());
        assertNotNull(cancellingThreadRef.get());
        assertNotNull(newTokenThreadRef.get());

        // The key assertion: the new token is cancelled on the cancel thread,
        // NOT on the register thread
        assertSame(
            cancellingThreadRef.get(),
            newTokenThreadRef.get(),
            "New token should be cancelled on the cancelling thread"
        );
    }

    @Test
    void multipleTokensRegisteredDuringCancellingAreAllCancelled()
            throws InterruptedException {

        // Orchestration:
        // Cancel thread runs cancel(), hits slow token which blocks.
        // Register thread registers N new tokens while slow token is blocked.
        // When slow token completes, the cancel thread picks up all N tokens.

        final int extraTokenCount = 5;
        final var slowStarted = new CountDownLatch(1);
        final var registerDone = new CountDownLatch(1);
        final var allDone = new CountDownLatch(1);

        final var cancelledTokens = Collections.synchronizedList(new ArrayList<Integer>());
        final var errorRef = new AtomicReference<@Nullable Throwable>();

        final var mc = new MutableCancellable(() -> {
            slowStarted.countDown();
            try {
                TimedAwait.latchAndExpectCompletion(registerDone, "registerDone");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        final var cancelThread = new Thread(() -> {
            try {
                mc.cancel();
            } catch (Throwable e) {
                errorRef.set(e);
            } finally {
                allDone.countDown();
            }
        }, "cancel-thread");

        final var registerThread = new Thread(() -> {
            try {
                TimedAwait.latchAndExpectCompletion(slowStarted, "slowStarted");
                for (int i = 0; i < extraTokenCount; i++) {
                    final int idx = i;
                    mc.register(() -> cancelledTokens.add(idx));
                }
            } catch (Throwable e) {
                errorRef.set(e);
            } finally {
                registerDone.countDown();
            }
        }, "register-thread");

        cancelThread.start();
        registerThread.start();

        TimedAwait.latchAndExpectCompletion(allDone, "allDone");
        cancelThread.join(TestSettings.TIMEOUT.toMillis());
        registerThread.join(TestSettings.TIMEOUT.toMillis());

        assertNull(errorRef.get(), () -> "Unexpected error: " + errorRef.get());
        assertEquals(extraTokenCount, cancelledTokens.size(),
            "All tokens registered during Cancelling should be cancelled");
    }

    @Test
    void tokensRegisteredInWavesDuringCancellingAreAllCancelled()
            throws InterruptedException {

        // This test verifies the loop in startCancellationOfEverything:
        // Wave 1: slow token blocks, register thread adds token A
        // Wave 2: when slow token finishes and picks up A, A is also slow,
        //         and register thread adds token B during A's execution.
        //
        // All tokens (A, B) must be cancelled.

        final var wave1Started = new CountDownLatch(1);
        final var wave1RegisterDone = new CountDownLatch(1);
        final var wave2Started = new CountDownLatch(1);
        final var wave2RegisterDone = new CountDownLatch(1);
        final var allDone = new CountDownLatch(1);

        final var cancelledTokens = Collections.synchronizedList(new ArrayList<String>());
        final var cancellingThread = new AtomicReference<@Nullable Thread>();
        final var tokenAThread = new AtomicReference<@Nullable Thread>();
        final var tokenBThread = new AtomicReference<@Nullable Thread>();
        final var errorRef = new AtomicReference<@Nullable Throwable>();

        final var mc = new MutableCancellable(() -> {
            // Wave 1: initial slow token
            cancellingThread.set(Thread.currentThread());
            cancelledTokens.add("initial");
            wave1Started.countDown();
            try {
                TimedAwait.latchAndExpectCompletion(wave1RegisterDone, "wave1RegisterDone");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        final var cancelThread = new Thread(() -> {
            try {
                mc.cancel();
            } catch (Throwable e) {
                errorRef.set(e);
            } finally {
                allDone.countDown();
            }
        }, "cancel-thread");

        final var registerThread = new Thread(() -> {
            try {
                // Wave 1: register token A while initial token is slow
                TimedAwait.latchAndExpectCompletion(wave1Started, "wave1Started");
                mc.register(() -> {
                    tokenAThread.set(Thread.currentThread());
                    cancelledTokens.add("A");
                    // Token A is also slow — wave 2
                    wave2Started.countDown();
                    try {
                        TimedAwait.latchAndExpectCompletion(wave2RegisterDone, "wave2RegisterDone");
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
                wave1RegisterDone.countDown();

                // Wave 2: register token B while token A is slow
                TimedAwait.latchAndExpectCompletion(wave2Started, "wave2Started");
                mc.register(() -> {
                    tokenBThread.set(Thread.currentThread());
                    cancelledTokens.add("B");
                });
                wave2RegisterDone.countDown();
            } catch (Throwable e) {
                errorRef.set(e);
            }
        }, "register-thread");

        cancelThread.start();
        registerThread.start();

        TimedAwait.latchAndExpectCompletion(allDone, "allDone");
        cancelThread.join(TestSettings.TIMEOUT.toMillis());
        registerThread.join(TestSettings.TIMEOUT.toMillis());

        assertNull(errorRef.get(), () -> "Unexpected error: " + errorRef.get());
        assertEquals(List.of("initial", "A", "B"), cancelledTokens);

        // All tokens should be cancelled on the cancel thread
        assertSame(cancellingThread.get(), tokenAThread.get(),
            "Token A should be cancelled on the cancelling thread");
        assertSame(cancellingThread.get(), tokenBThread.get(),
            "Token B should be cancelled on the cancelling thread");
    }

    @Test
    void concurrentCancelAndRegisterNeverLosesToken()
            throws InterruptedException {

        // Stress-ish test: cancel and register race, but the token
        // must always be invoked (either immediately by register when
        // state is Cancelled, or picked up during Cancelling).

        for (int i = 0; i < TestSettings.CONCURRENCY_REPEATS; i++) {
            final var mc = new MutableCancellable();
            final var called = new CountDownLatch(1);

            final var cancelThread = new Thread(mc::cancel);
            final var registerThread = new Thread(() ->
                mc.register(called::countDown)
            );

            cancelThread.start();
            registerThread.start();

            TimedAwait.latchAndExpectCompletion(called, "called (iteration " + i + ")");
            cancelThread.join(TestSettings.TIMEOUT.toMillis());
            registerThread.join(TestSettings.TIMEOUT.toMillis());
        }
    }

    @Test
    void concurrentCancelAndCompleteDoNotDoubleInvoke()
            throws InterruptedException {

        // When cancel() and complete() race, the token should be invoked
        // at most once (by cancel if it wins, zero times if complete wins).

        for (int i = 0; i < TestSettings.CONCURRENCY_REPEATS; i++) {
            final var called = new AtomicInteger(0);
            final var mc = new MutableCancellable(called::incrementAndGet);

            final var cancelThread = new Thread(mc::cancel);
            final var completeThread = new Thread(mc::complete);

            cancelThread.start();
            completeThread.start();

            cancelThread.join(TestSettings.TIMEOUT.toMillis());
            completeThread.join(TestSettings.TIMEOUT.toMillis());

            assertTrue(called.get() <= 1,
                "Token should be invoked at most once, got: " + called.get());
        }
    }

    @Test
    void concurrentDoubleCancelDoesNotThrow()
            throws InterruptedException {

        // Verifies that cancel() is truly idempotent even when a second
        // cancel() call arrives while the first is still in the Cancelling
        // state (draining tokens). The second call should return harmlessly.

        for (int i = 0; i < TestSettings.CONCURRENCY_REPEATS; i++) {
            final var slowStarted = new CountDownLatch(1);
            final var slowRelease = new CountDownLatch(1);
            final var called = new AtomicInteger(0);
            final var errorRef = new AtomicReference<@Nullable Throwable>();

            final var mc = new MutableCancellable(() -> {
                called.incrementAndGet();
                slowStarted.countDown();
                try {
                    TimedAwait.latchAndExpectCompletion(slowRelease, "slowRelease");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            final var cancelThread1 = new Thread(mc::cancel, "cancel-1");
            cancelThread1.start();

            // Wait until the first cancel is in the middle of draining
            TimedAwait.latchAndExpectCompletion(slowStarted, "slowStarted (iteration " + i + ")");

            // Second cancel while in Cancelling state — must not throw
            final var cancelThread2 = new Thread(() -> {
                try {
                    mc.cancel();
                } catch (Throwable e) {
                    errorRef.set(e);
                }
            }, "cancel-2");
            cancelThread2.start();
            cancelThread2.join(TestSettings.TIMEOUT.toMillis());

            assertNull(errorRef.get(),
                "Second cancel() should not throw, got: " + errorRef.get());

            // Let the first cancel finish
            slowRelease.countDown();
            cancelThread1.join(TestSettings.TIMEOUT.toMillis());

            // Token invoked exactly once
            assertEquals(1, called.get());
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // State transition: Cancelling -> Cancelled
    // ──────────────────────────────────────────────────────────────────

    @Test
    void afterCancelCompletesRegisterInvokesImmediately() throws InterruptedException {
        // Ensure that once cancellation finishes (state = Cancelled),
        // any subsequent register() invokes the token immediately.

        final var mc = new MutableCancellable();
        mc.cancel();

        final var called = new AtomicInteger(0);
        mc.register(called::incrementAndGet);
        assertEquals(1, called.get());

        // And again
        mc.register(called::incrementAndGet);
        assertEquals(2, called.get());
    }

    // ──────────────────────────────────────────────────────────────────
    // Edge cases
    // ──────────────────────────────────────────────────────────────────

    @Test
    void registerThrowsOnNullToken() {
        final var mc = new MutableCancellable();
        assertThrows(NullPointerException.class, () -> mc.register(null));
    }

    @Test
    void throwingTokenDuringCancellingDoesNotPreventNewTokensFromBeingCancelled()
            throws InterruptedException {

        // Initial token throws, but a token registered during Cancelling
        // should still be cancelled.

        final var throwingStarted = new CountDownLatch(1);
        final var registerDone = new CountDownLatch(1);
        final var allDone = new CountDownLatch(1);
        final var newTokenCalled = new AtomicInteger(0);
        final var errorRef = new AtomicReference<@Nullable Throwable>();
        final var reportedErrors = Collections.synchronizedList(new ArrayList<Throwable>());

        final var mc = new MutableCancellable(() -> {
            throwingStarted.countDown();
            try {
                TimedAwait.latchAndExpectCompletion(registerDone, "registerDone");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            throw new RuntimeException("initial boom");
        });

        final var cancelThread = new Thread(() -> {
            final var th = Thread.currentThread();
            final var prev = th.getUncaughtExceptionHandler();
            th.setUncaughtExceptionHandler((t, e) -> reportedErrors.add(e));
            try {
                mc.cancel();
            } catch (Throwable e) {
                errorRef.set(e);
            } finally {
                th.setUncaughtExceptionHandler(prev);
                allDone.countDown();
            }
        }, "cancel-thread");

        final var registerThread = new Thread(() -> {
            try {
                TimedAwait.latchAndExpectCompletion(throwingStarted, "throwingStarted");
                mc.register(newTokenCalled::incrementAndGet);
            } catch (Throwable e) {
                errorRef.set(e);
            } finally {
                registerDone.countDown();
            }
        }, "register-thread");

        cancelThread.start();
        registerThread.start();

        TimedAwait.latchAndExpectCompletion(allDone, "allDone");
        cancelThread.join(TestSettings.TIMEOUT.toMillis());
        registerThread.join(TestSettings.TIMEOUT.toMillis());

        assertNull(errorRef.get(), () -> "Unexpected error: " + errorRef.get());
        assertEquals(1, newTokenCalled.get(),
            "New token should still be cancelled despite initial token throwing");
        assertEquals(1, reportedErrors.size());
        assertEquals("initial boom", reportedErrors.get(0).getMessage());
    }
}
