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
        final var cb = CompletionCallback.<String, SampleException>empty();

        cb.onSuccess("Hello, world!");
        cb.onCancellation();

        final var logged = new AtomicReference<@Nullable Throwable>(null);
        final var error = new RuntimeException("Sample exception");
        final var th = new Thread(() -> cb.onRuntimeFailure(error));

        th.setUncaughtExceptionHandler((t, e) -> logged.set(e));
        th.start();
        th.join();

        final var received = logged.get();
        assertEquals(error, received);
    }

    @Test
    void emptyLogsTypedFailure() throws InterruptedException {
        final var cb = CompletionCallback.<String, SampleException>empty();

        cb.onSuccess("Hello, world!");
        cb.onCancellation();

        final var logged = new AtomicReference<@Nullable Throwable>(null);
        final var error = new SampleException("Sample exception");
        final var th = new Thread(() -> cb.onTypedFailure(error));

        th.setUncaughtExceptionHandler((t, e) -> logged.set(e));
        th.start();
        th.join();

        final var received = logged.get();
        assertEquals(error, received);
    }

    @Test
    void protectedCallbackForSuccess() {
        final var called = new AtomicInteger(0);
        final var outcome = new AtomicReference<@Nullable Outcome<String, ?>>(null);
        final var cb = CompletionCallback.protect(
                new CompletionCallback<String, Exception>() {
                    @Override
                    public void onSuccess(String value) {
                        called.incrementAndGet();
                        outcome.set(Outcome.success(value));
                    }

                    @Override
                    public void onTypedFailure(Exception e) {
                        throw new IllegalStateException("Should not be called");
                    }

                    @Override
                    public void onRuntimeFailure(RuntimeException e) {
                        throw new IllegalStateException("Should not be called");
                    }

                    @Override
                    public void onCancellation() {
                        throw new IllegalStateException("Should not be called");
                    }
                }
        );

        cb.onCompletion(Outcome.success("Hello, world!"));
        cb.onSuccess("Hello, world! (2)");
        cb.onSuccess("Hello, world! (3)");

        assertEquals(1, called.get());
        assertEquals(Outcome.success("Hello, world!"), outcome.get());
    }

    @Test
    void protectedCallbackForTypedFailure() throws InterruptedException {
        final var called = new AtomicInteger(0);
        final var outcome = new AtomicReference<@Nullable Outcome<String, SampleException>>(null);
        final var cb = CompletionCallback.protect(
                new CompletionCallback<String, SampleException>() {
                    @Override
                    public void onSuccess(String value) {
                        throw new IllegalStateException("Should not be called");
                    }

                    @Override
                    public void onTypedFailure(SampleException e) {
                        called.incrementAndGet();
                        outcome.set(Outcome.typedFailure(e));
                    }

                    @Override
                    public void onRuntimeFailure(RuntimeException e) {
                        throw new IllegalStateException("Should not be called");
                    }

                    @Override
                    public void onCancellation() {
                        throw new IllegalStateException("Should not be called");
                    }
                }
        );

        final var e = new SampleException("Boom!");
        cb.onCompletion(Outcome.typedFailure(e));

        assertEquals(1, called.get());
        assertEquals(Outcome.typedFailure(e), outcome.get());

        final var logged = new AtomicReference<@Nullable Throwable>(null);
        final var th = new Thread(() -> cb.onTypedFailure(e));
        th.setUncaughtExceptionHandler((t, ex) -> logged.set(ex));
        th.start();
        th.join();

        assertEquals(1, called.get());
        assertEquals(e, logged.get());
    }

    @Test
    void protectedCallbackForRuntimeFailure() throws InterruptedException {
        final var called = new AtomicInteger(0);
        final var outcome = new AtomicReference<@Nullable Outcome<String, SampleException>>(null);
        final var cb = CompletionCallback.protect(
                new CompletionCallback<String, SampleException>() {
                    @Override
                    public void onSuccess(String value) {
                        throw new IllegalStateException("Should not be called");
                    }

                    @Override
                    public void onTypedFailure(SampleException e) {
                        throw new IllegalStateException("Should not be called");
                    }

                    @Override
                    public void onRuntimeFailure(RuntimeException e) {
                        called.incrementAndGet();
                        outcome.set(Outcome.runtimeFailure(e));
                    }

                    @Override
                    public void onCancellation() {
                        throw new IllegalStateException("Should not be called");
                    }
                }
        );

        final var e = new RuntimeException("Boom!");
        cb.onCompletion(Outcome.runtimeFailure(e));

        assertEquals(1, called.get());
        assertEquals(Outcome.runtimeFailure(e), outcome.get());

        final var logged = new AtomicReference<@Nullable Throwable>(null);
        final var th = new Thread(() -> cb.onRuntimeFailure(e));
        th.setUncaughtExceptionHandler((t, ex) -> logged.set(ex));
        th.start();
        th.join();

        assertEquals(1, called.get());
        assertEquals(e, logged.get());
    }


    @Test
    void protectedCallbackForCancellation() {
        final var called = new AtomicInteger(0);
        final var outcome = new AtomicReference<@Nullable Outcome<String, SampleException>>(null);
        final var cb = CompletionCallback.protect(
                new CompletionCallback<String, SampleException>() {
                    @Override
                    public void onSuccess(String value) {
                        throw new IllegalStateException("Should not be called");
                    }

                    @Override
                    public void onTypedFailure(SampleException e) {
                        throw new IllegalStateException("Should not be called");
                    }

                    @Override
                    public void onRuntimeFailure(RuntimeException e) {
                        throw new IllegalStateException("Should not be called");
                    }

                    @Override
                    public void onCancellation() {
                        called.incrementAndGet();
                        outcome.set(Outcome.cancellation());
                    }
                }
        );

        cb.onCompletion(Outcome.cancellation());
        cb.onCancellation();
        cb.onCancellation();

        assertEquals(1, called.get());
        assertEquals(Outcome.cancellation(), outcome.get());
    }
}
