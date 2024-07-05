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
     *
     * @param value is the successful result of the task, to be signaled
     */
    fun onSuccess(value: T)

    /**
     * Must be called when the task completes with an exception.
     *
     * @param e is the exception that the task has failed with
     */
    fun onFailure(e: Throwable)

    /**
     * Must be called when the task is cancelled.
     */
    fun onCancel()
}
