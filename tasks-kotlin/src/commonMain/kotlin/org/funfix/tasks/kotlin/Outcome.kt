package org.funfix.tasks.kotlin

/**
 * Represents the result of a computation.
 *
 * This is a union type that can signal:
 * - a successful result, via [Outcome.Success]
 * - a failure (with an exception), via [Outcome.Failure]
 * - a cancelled computation, via [Outcome.Cancellation]
 */
public sealed interface Outcome<out T> {
    public val orThrow: T
        /**
         * Returns the successful result of a computation, or throws an exception
         * if the computation failed or was cancelled.
         *
         * @throws TaskCancellationException in case this is an [Outcome.Cancellation]
         * @throws Throwable in case this is an [Outcome.Failure]
         */
        @Throws(TaskCancellationException::class)
        get() =
            when (this) {
                is Success -> value
                is Failure -> throw exception
                is Cancellation -> throw TaskCancellationException("Task was cancelled")
            }

    /**
     * Returned in case the task was successful.
     */
    public data class Success<out T>(val value: T): Outcome<T>

    /**
     * Returned in case the task failed with an exception.
     */
    public data class Failure(val exception: Throwable): Outcome<Nothing>

    /**
     * Returned in case the task was cancelled.
     */
    public data object Cancellation: Outcome<Nothing>

    public companion object {
        /**
         * Constructs a successful [Outcome] with the given value.
         */
        public fun <T> success(value: T): Outcome<T> = Success(value)

        /**
         * Constructs a failed [Outcome] with the given exception.
         */
        public fun <T> failure(e: Throwable): Outcome<T> = Failure(e)

        /**
         * Constructs a cancelled [Outcome].
         */
        public fun <T> cancellation(): Outcome<T> = Cancellation
    }
}
