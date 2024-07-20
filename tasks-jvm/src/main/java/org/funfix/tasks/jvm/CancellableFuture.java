package org.funfix.tasks.jvm;

import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

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
public record CancellableFuture<T  extends @Nullable Object>(
        CompletableFuture<? extends T> future,
        Cancellable cancellable
) {
    public CancellableFuture {
        Objects.requireNonNull(future, "future");
        Objects.requireNonNull(cancellable, "cancellable");
    }
}
