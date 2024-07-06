package org.funfix.tasks

import java.io.Serializable

/**
 * Represents a callback that will be invoked when a task completes.
 *
 * A task can complete either successfully with a value, or with an exception,
 * or it can be cancelled.
 *
 * MUST BE idempotent AND thread-safe.
 *
 * @param T is the type of the value that the task will complete with
 */
interface CompletionCallback<in T> : Serializable {
    /**
     * Must be called when the task completes successfully.
     *
     * @param value is the successful result of the task, to be signaled
     */
    fun onSuccess(value: T)

    /**
     * Must be called when the task completes with an exception.
     *
     * @param e is the exception that the task failed with
     */
    fun onFailure(e: Throwable)

    /**
     * Must be called when the task is cancelled.
     */
    fun onCancel()

    /**
     * Signals a final [Outcome] to this listener.
     */
    fun onCompletion(outcome: Outcome<T>) {
        when (outcome) {
            is Outcome.Succeeded -> onSuccess(outcome.value)
            is Outcome.Failed -> onFailure(outcome.exception)
            is Outcome.Cancelled -> onCancel()
        }
    }

    companion object {
        /**
         * @return a [CompletionCallback] that does nothing.
         */
        @JvmStatic
        fun <T> empty(): CompletionCallback<T> = object : CompletionCallback<T> {
            override fun onSuccess(value: T) {}
            override fun onCancel() {}
            override fun onFailure(e: Throwable) {
                UncaughtExceptionHandler.logException(e)
            }
        }

        @JvmStatic
        fun <T> protect(listener: CompletionCallback<T>): CompletionCallback<T> =
            ProtectedCompletionListener(listener)
    }
}

/**
 * Protects a given [CompletionCallback], by:
 * 1. Ensuring that the underlying listener is called at most once.
 * 2. Trampolining the call, such that stack overflows are avoided.
 */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
internal expect object ProtectedCompletionListener {
    @JvmStatic
    operator fun <T> invoke(listener: CompletionCallback<T>): CompletionCallback<T>
}
