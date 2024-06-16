package org.funfix.tasks

/**
 * Represents a callback that will be invoked when a task completes.
 *
 * A task can complete either successfully with a value, or with an exception,
 * or it can be cancelled.
 *
 * MUST BE idempotent AND thread-safe.
 */
interface CompletionHandler<in T> {
    /**
     * Must be called when the task completes successfully.
     */
    fun trySuccess(value: T): Boolean

    /**
     * Must be called when the task completes with an exception.
     */
    fun tryFailure(e: Throwable): Boolean

    /**
     * Must be called when the task is cancelled.
     */
    fun tryCancel(): Boolean

    fun success(value: T) {
        if (!trySuccess(value))
            throw IllegalStateException(
                "CompletionHandler was already completed, cannot call `success`"
            )
    }

    fun failure(e: Throwable) {
        if (!tryFailure(e))
            throw IllegalStateException(
                "CompletionHandler was already completed, cannot call `failure`",
                e
            )
    }

    fun cancel() {
        if (!tryCancel())
            throw IllegalStateException(
                "CompletionHandler was already completed, cannot call `cancel`"
            )
    }
}
