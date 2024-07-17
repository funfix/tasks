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
public fun interface CompletionCallback<in T> : Serializable {
    /**
     * Signals a final [Outcome].
     */
    public fun complete(outcome: Outcome<T>)

    public companion object {
        /**
         * @return a [CompletionCallback] that does nothing.
         */
        @JvmStatic
        public fun <T> empty(): CompletionCallback<T> =
            CompletionCallback { outcome ->
                if (outcome is Outcome.Failed) {
                    UncaughtExceptionHandler.logOrRethrow(outcome.exception)
                }
            }

    }
}
