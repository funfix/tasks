package org.funfix.tasks

/**
 * Represents a callback that will be invoked when a task completes.
 *
 * A task can complete either successfully with a value, or with an exception,
 * or it can be cancelled.
 *
 * MUST BE idempotent AND thread-safe.
 */
interface CompletionListener<in T> {
    /**
     * Must be called when the task completes successfully.
     */
    fun tryOnSuccess(value: T): Boolean

    /**
     * Must be called when the task completes with an exception.
     */
    fun tryOnFailure(e: Throwable): Boolean

    /**
     * Must be called when the task is cancelled.
     */
    fun tryOnCancel(): Boolean

    fun onSuccess(value: T) {
        if (!tryOnSuccess(value))
            throw IllegalStateException(
                "CompletionHandler was already completed, cannot call `success`"
            )
    }

    fun onFailure(e: Throwable) {
        if (!tryOnFailure(e))
            throw IllegalStateException(
                "CompletionHandler was already completed, cannot call `failure`",
                e
            )
    }

    fun onCancel() {
        if (!tryOnCancel())
            throw IllegalStateException(
                "CompletionHandler was already completed, cannot call `cancel`"
            )
    }
}
