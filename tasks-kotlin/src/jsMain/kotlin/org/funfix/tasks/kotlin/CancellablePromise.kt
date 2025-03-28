package org.funfix.tasks.kotlin

import kotlin.js.Promise

/**
 * This is a wrapper around a JavaScript [Promise] with a
 * [Cancellable] reference attached.
 *
 * A standard JavaScript [Promise] is not connected to its
 * asynchronous task and cannot be cancelled. Thus, if we want to cancel
 * a task, we need to keep a reference to a [Cancellable] object that
 * can do the job.
 */
public data class CancellablePromise<out T>(
    val promise: Promise<T>,
    val cancellable: Cancellable
)
