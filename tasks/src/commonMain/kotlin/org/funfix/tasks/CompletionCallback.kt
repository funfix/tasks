package org.funfix.tasks

import org.funfix.tasks.support.NonBlocking
import org.funfix.tasks.support.Serializable
import kotlin.jvm.JvmStatic

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
@NonBlocking
public interface CompletionCallback<in T> : Serializable {
    /**
     * Signals a successful completion.
     */
    public fun onSuccess(value: T)

    /**
     * Signals a failed completion.
     */
    public fun onFailure(e: Throwable)

    /**
     * Signals a cancelled completion.
     */
    public fun onCancellation()

    /**
     * Signals a final [Outcome].
     */
    public fun onOutcome(outcome: Outcome<T>) {
        when (outcome) {
            is Outcome.Success -> onSuccess(outcome.value)
            is Outcome.Failure -> onFailure(outcome.exception)
            is Outcome.Cancellation -> onCancellation()
        }
    }

    /**
     * Helper for creating [CompletionCallback] instances based just
     * on the [onOutcome] method.
     */
    public fun interface OutcomeBased<in T> : CompletionCallback<T> {
        override fun onOutcome(outcome: Outcome<T>)

        override fun onSuccess(value: T) {
            onOutcome(Outcome.success(value))
        }

        override fun onFailure(e: Throwable) {
            onOutcome(Outcome.failure(e))
        }

        override fun onCancellation() {
            onOutcome(Outcome.cancellation())
        }
    }

    public companion object {
        /**
         * @return a [CompletionCallback] that does nothing.
         */
        @JvmStatic
        public fun <T> empty(): CompletionCallback<T> =
            emptyRef

        /**
         * Convenience function that builds a [CompletionCallback] from
         * the given lambdas.
         */
        internal inline operator fun <T> invoke(
            crossinline onSuccess: (T) -> Unit,
            crossinline onFailure: (Throwable) -> Unit,
            crossinline onCancellation: () -> Unit
        ): CompletionCallback<T> =
            object : CompletionCallback<T> {
                override fun onSuccess(value: T) { onSuccess(value) }
                override fun onFailure(e: Throwable) { onFailure(e) }
                override fun onCancellation() { onCancellation() }
            }

        private val emptyRef: CompletionCallback<Any?> =
            object : CompletionCallback<Any?> {
                override fun onSuccess(value: Any?) {}
                override fun onCancellation() {}
                override fun onFailure(e: Throwable) {
                    UncaughtExceptionHandler.logOrRethrow(e)
                }
            }
    }
}
