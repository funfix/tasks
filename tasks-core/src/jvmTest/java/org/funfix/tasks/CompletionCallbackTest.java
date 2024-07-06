package org.funfix.tasks;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CompletionCallbackTest {
    @Test
    void empty() throws InterruptedException {
        final var cb = CompletionCallback.empty();

        cb.onSuccess("Hello, world!");
        cb.onCancel();

        final var logged = new AtomicReference<Throwable>(null);
        final var error = new RuntimeException("Sample exception");
        final var th = new Thread(() -> {
            cb.onFailure(error);
        });

        th.setUncaughtExceptionHandler((t, e) -> logged.set(e));
        th.start();
        th.join();

        final var received = logged.get();
        assertEquals(error, received);
    }

    @Test
    void protectedCallbackForSuccess() throws InterruptedException {
        final var called = new AtomicInteger(0);
        final var outcome = new AtomicReference<Outcome<String>>(null);
        final var cb = CompletionCallback.protect(
                new CompletionCallback<String>() {
                    @Override
                    public void onSuccess(String value) {
                        called.incrementAndGet();
                        outcome.set(Outcome.succeeded(value));
                    }

                    @Override
                    public void onFailure(@NotNull Throwable e) {
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
    void protectedCallbackForFailure() throws InterruptedException {
        final var called = new AtomicInteger(0);
        final var outcome = new AtomicReference<Outcome<String>>(null);
        final var cb = CompletionCallback.protect(
                new CompletionCallback<String>() {
                    @Override
                    public void onSuccess(String value) {
                        throw new IllegalStateException("Should not be called");
                    }

                    @Override
                    public void onFailure(@NotNull Throwable e) {
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

        final var logged = new AtomicReference<Throwable>(null);
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
        final var outcome = new AtomicReference<Outcome<String>>(null);
        final var cb = CompletionCallback.protect(
                new CompletionCallback<String>() {
                    @Override
                    public void onSuccess(String value) {
                        throw new IllegalStateException("Should not be called");
                    }

                    @Override
                    public void onFailure(@NotNull Throwable e) {
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
