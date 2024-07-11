package org.funfix.tasks

import java.util.concurrent.CompletableFuture

/**
 * This is a wrapper around a [CompletableFuture] with a
 * [Cancellable] reference attached.
 *
 * A standard Java [CompletableFuture] is not connected to its
 * asynchronous task and cannot be cancelled. Thus, if we want to cancel
 * a task, we need to keep a reference to a [Cancellable] object that
 * can do the job.
 */
@JvmRecord
data class CancellableFuture<out T>(
    val future: CompletableFuture<out T>,
    val cancellable: Cancellable
)
