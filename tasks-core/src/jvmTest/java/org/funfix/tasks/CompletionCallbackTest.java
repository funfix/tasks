package org.funfix.tasks;

import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CompletionCallbackTest {
    @Test
    void emptyLogsRuntimeFailure() throws InterruptedException {
        final var cb = CompletionCallback.<String>empty();

        cb.complete(Outcome.succeeded("Hello, world!"));
        cb.complete(Outcome.cancelled());

        final var logged = new AtomicReference<@Nullable Throwable>(null);
        final var error = new RuntimeException("Sample exception");
        final var th = new Thread(() -> cb.complete(Outcome.failed(error)));

        th.setUncaughtExceptionHandler((t, e) -> logged.set(e));
        th.start();
        th.join();

        final var received = logged.get();
        assertEquals(error, received);
    }

    @Test
    void protectedCallbackForSuccess() {
        final var called = new AtomicInteger(0);
        final var outcomeRef = new AtomicReference<@Nullable Outcome<? extends String>>(null);
        final var cb = ProtectedCompletionCallback.invoke(
                (CompletionCallback<String>) outcome -> {
                    called.incrementAndGet();
                    outcomeRef.set(outcome);
                }
        );

        cb.complete(Outcome.succeeded("Hello, world!"));
        cb.complete(Outcome.succeeded("Hello, world! (2)"));
        cb.complete(Outcome.succeeded("Hello, world! (3)"));

        assertEquals(1, called.get());
        assertEquals(Outcome.succeeded("Hello, world!"), outcomeRef.get());
    }

    @Test
    void protectedCallbackForRuntimeFailure() throws InterruptedException {
        final var called = new AtomicInteger(0);
        final var outcomeRef = new AtomicReference<@Nullable Outcome<? extends String>>(null);
        final var cb = ProtectedCompletionCallback.invoke(
                (CompletionCallback<String>) outcome -> {
                    called.incrementAndGet();
                    outcomeRef.set(outcome);
                }
        );

        final var e = new RuntimeException("Boom!");
        cb.complete(Outcome.failed(e));

        assertEquals(1, called.get());
        assertEquals(Outcome.failed(e), outcomeRef.get());

        final var logged = new AtomicReference<@Nullable Throwable>(null);
        final var th = new Thread(() -> cb.complete(Outcome.failed(e)));
        th.setUncaughtExceptionHandler((t, ex) -> logged.set(ex));
        th.start();
        th.join();

        assertEquals(1, called.get());
        assertEquals(e, logged.get());
    }


    @Test
    void protectedCallbackForCancellation() {
        final var called = new AtomicInteger(0);
        final var outcomeRef = new AtomicReference<@Nullable Outcome<? extends String>>(null);
        final var cb = ProtectedCompletionCallback.invoke(
                (CompletionCallback<String>) outcome -> {
                    called.incrementAndGet();
                    outcomeRef.set(outcome);
                }
        );

        cb.complete(Outcome.cancelled());
        cb.complete(Outcome.cancelled());
        cb.complete(Outcome.cancelled());

        assertEquals(1, called.get());
        assertEquals(Outcome.cancelled(), outcomeRef.get());
    }
}
