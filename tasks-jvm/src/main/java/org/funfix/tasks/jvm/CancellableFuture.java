package org.funfix.tasks.jvm;

import lombok.Data;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NullMarked;
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
@Data
public class CancellableFuture<T extends @Nullable Object> {
    public final CompletableFuture<? extends T> future;
    public final Cancellable cancellable;
}
