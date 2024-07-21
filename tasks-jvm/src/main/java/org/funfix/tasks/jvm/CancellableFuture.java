package org.funfix.tasks.jvm;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NullMarked;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * This is a wrapper around a [CompletableFuture] with a
 * [Cancellable] reference attached.
 * <p>
 * A standard Java [CompletableFuture] is not connected to its
 * asynchronous task and cannot be cancelled. Thus, if we want to cancel
 * a task, we need to keep a reference to a [Cancellable] object that
 * can do the job.
 */
@NullMarked
@ToString
@EqualsAndHashCode(callSuper = false)
public class CancellableFuture<T extends @Nullable Object> {
    private final CompletableFuture<? extends T> future;
    private final Cancellable cancellable;

    public CancellableFuture(
        CompletableFuture<? extends T> future,
        Cancellable cancellable
    ) {
        this.future = future;
        this.cancellable = cancellable;
    }

    public CompletableFuture<? extends T> future() {
        return future;
    }

    public Cancellable cancellable() {
        return cancellable;
    }

    public <U> CancellableFuture<U> transform(
        Function<? super CompletableFuture<? extends T>, ? extends CompletableFuture<? extends U>> fn
    ) {
        return new CancellableFuture<>(fn.apply(future), cancellable);
    }
}
