package org.funfix.tasks

import java.util.concurrent.CompletionStage

/**
 * This is a wrapper around a [CompletionStage] with a
 * [Cancellable] reference attached.
 *
 * A standard Java [CompletionStage] is not connected to its
 * asynchronous task and cannot be cancelled. Thus, if we want to cancel
 * a task, we need to keep a reference to a [Cancellable] object that
 * can do the job.
 */
@JvmRecord
data class CancellableCompletionStage<out T>(
    val completionStage: CompletionStage<out T>,
    val cancellable: Cancellable
)
