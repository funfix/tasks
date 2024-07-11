package org.funfix.tasks

import java.util.concurrent.ExecutionException

/**
 * Represents the outcome of a concurrent computation (e.g., `Fiber`).
 */
sealed interface Outcome<out T> {
    /**
     * Returns the value of the task if it was successful, or throws an exception.
     *
     * @return the successful value (in case the outcome is [Succeeded])
     *
     * @throws ExecutionException if the task failed with an exception ([Failed])
     * @throws CancellationException if the task was cancelled ([Cancelled])
     */
    @Throws(ExecutionException::class, CancellationException::class)
    fun getOrThrow(): T

    /**
     * The concurrent job completed successfully with a `value`.
     */
    @JvmRecord
    data class Succeeded<out T>(val value: T) : Outcome<T> {
        override fun getOrThrow(): T {
            return value
        }
    }

    /**
     * The concurrent job failed with an `exception`.
     */
    @JvmRecord
    data class Failed(val exception: Throwable) : Outcome<Nothing> {
        @Throws(ExecutionException::class)
        override fun getOrThrow(): Nothing {
            throw ExecutionException(exception)
        }
    }

    /**
     * The concurrent job was cancelled.
     */
    data object Cancelled: Outcome<Nothing> {
        @Throws(CancellationException::class)
        override fun getOrThrow(): Nothing {
            throw CancellationException()
        }
    }

    companion object {
        @JvmStatic fun <T> succeeded(value: T): Outcome<T> =
            Succeeded(value)
        @JvmStatic fun <T> failed(exception: Throwable): Outcome<T> =
            Failed(exception)
        @JvmStatic fun <T> cancelled(): Outcome<T> =
            Cancelled
    }
}
