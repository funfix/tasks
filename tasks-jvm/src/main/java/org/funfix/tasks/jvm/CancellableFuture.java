package org.funfix.tasks.jvm;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * This is a wrapper around a {@link CompletableFuture} with a
 * {@link Cancellable} reference attached.
 * <p>
 * A standard Java {@link CompletableFuture} is not connected to its
 * asynchronous task and cannot be cancelled. Thus, if we want to cancel
 * a task, we need to keep a reference to a {@link Cancellable} object that
 * can do the job.
 * <p>
 * {@code CancellableFuture} is similar to {@link Fiber}, which should
 * be preferred because it's more principled. {@code CancellableFuture}
 * is useful more for interoperability with Java code.
 */
public record CancellableFuture<T extends @Nullable Object>(
    CompletableFuture<? extends T> future,
    Cancellable cancellable
) {
    public <U> CancellableFuture<U> transform(
        Function<? super CompletableFuture<? extends T>, ? extends CompletableFuture<? extends U>> fn
    ) {
        return new CancellableFuture<>(fn.apply(future), cancellable);
    }
}
