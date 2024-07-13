package org.funfix.tasks;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.funfix.tasks.internals.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SuppressWarnings("KotlinInternalInJava")
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
        final var outcome = new AtomicReference<@Nullable Outcome<String>>(null);
        final var cb = ProtectedCompletionCallback.invoke(
                new CompletionCallback<String>() {
                    @Override
                    public void onSuccess(String value) {
                        called.incrementAndGet();
                        outcome.set(Outcome.succeeded(value));
                    }

                    @Override
                    public void onFailure(Throwable e) {
                        throw new IllegalStateException("Should not be called");
                    }

                    @Override
                    public void onCancel() {
                        throw new IllegalStateException("Should not be called");
                    }
                }
        );

        cb.onCompletion(Outcome.succeeded("Hello, world!"));
        cb.onSuccess("Hello, world! (2)");
        cb.onSuccess("Hello, world! (3)");

        assertEquals(1, called.get());
        assertEquals(Outcome.succeeded("Hello, world!"), outcome.get());
    }

    @Test
    void protectedCallbackForRuntimeFailure() throws InterruptedException {
        final var called = new AtomicInteger(0);
        final var outcome = new AtomicReference<@Nullable Outcome<String>>(null);
        final var cb = ProtectedCompletionCallback.invoke(
                new CompletionCallback<String>() {
                    @Override
                    public void onSuccess(String value) {
                        throw new IllegalStateException("Should not be called");
                    }

                    @Override
                    public void onFailure(Throwable e) {
                        called.incrementAndGet();
                        outcome.set(Outcome.failed(e));
                    }

                    @Override
                    public void onCancel() {
                        throw new IllegalStateException("Should not be called");
                    }
                }
        );

        final var e = new RuntimeException("Boom!");
        cb.onCompletion(Outcome.failed(e));

        assertEquals(1, called.get());
        assertEquals(Outcome.failed(e), outcome.get());

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
        final var outcome = new AtomicReference<@Nullable Outcome<String>>(null);
        final var cb = ProtectedCompletionCallback.invoke(
                new CompletionCallback<String>() {
                    @Override
                    public void onSuccess(String value) {
                        throw new IllegalStateException("Should not be called");
                    }

                    @Override
                    public void onFailure(Throwable e) {
                        throw new IllegalStateException("Should not be called");
                    }

                    @Override
                    public void onCancel() {
                        called.incrementAndGet();
                        outcome.set(Outcome.cancelled());
                    }
                }
        );

        cb.onCompletion(Outcome.cancelled());
        cb.onCancel();
        cb.onCancel();

        assertEquals(1, called.get());
        assertEquals(Outcome.cancelled(), outcome.get());
    }
}
