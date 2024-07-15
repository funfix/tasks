package org.funfix.tasks;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

@NullMarked
public class CompletionCallbackTest {
    @Test
    void emptyLogsRuntimeFailure() throws InterruptedException {
        final var cb = CompletionCallback.<String>empty();

        cb.onSuccess("Hello, world!");
        cb.onCancel();

        final var logged = new AtomicReference<@Nullable Throwable>(null);
        final var error = new RuntimeException("Sample exception");
        final var th = new Thread(() -> cb.onFailure(error));

        th.setUncaughtExceptionHandler((t, e) -> logged.set(e));
        th.start();
        th.join();

        final var received = logged.get();
        assertEquals(error, received);
    }

    @Test
    void protectedCallbackForSuccess() {
        final var called = new AtomicInteger(0);
        final var outcomeRef = new AtomicReference<@Nullable Outcome<String>>(null);
        final var cb = ProtectedCompletionCallback.protect(
                (CompletionCallback<String>) outcome -> {
                    called.incrementAndGet();
                    outcomeRef.set(outcome);
                }
        );

        cb.onCompletion(Outcome.succeeded("Hello, world!"));
        cb.onSuccess("Hello, world! (2)");
        cb.onSuccess("Hello, world! (3)");

        assertEquals(1, called.get());
        assertEquals(Outcome.succeeded("Hello, world!"), outcomeRef.get());
    }

    @Test
    void protectedCallbackForRuntimeFailure() throws InterruptedException {
        final var called = new AtomicInteger(0);
        final var outcomeRef = new AtomicReference<@Nullable Outcome<String>>(null);
        final var cb = ProtectedCompletionCallback.protect(
                (CompletionCallback<String>) outcome -> {
                    called.incrementAndGet();
                    outcomeRef.set(outcome);
                }
        );

        final var e = new RuntimeException("Boom!");
        cb.onCompletion(Outcome.failed(e));

        assertEquals(1, called.get());
        assertEquals(Outcome.failed(e), outcomeRef.get());

        final var logged = new AtomicReference<@Nullable Throwable>(null);
        final var th = new Thread(() -> cb.onFailure(e));
        th.setUncaughtExceptionHandler((t, ex) -> logged.set(ex));
        th.start();
        th.join();

        assertEquals(1, called.get());
        assertEquals(e, logged.get());
    }


    @Test
    void protectedCallbackForCancellation() {
        final var called = new AtomicInteger(0);
        final var outcomeRef = new AtomicReference<@Nullable Outcome<String>>(null);
        final var cb = ProtectedCompletionCallback.protect(
                (CompletionCallback<String>) outcome -> {
                    called.incrementAndGet();
                    outcomeRef.set(outcome);
                }
        );

        cb.onCompletion(Outcome.cancelled());
        cb.onCancel();
        cb.onCancel();

        assertEquals(1, called.get());
        assertEquals(Outcome.cancelled(), outcomeRef.get());
    }
}
