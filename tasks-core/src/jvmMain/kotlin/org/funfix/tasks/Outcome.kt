@file:Suppress("DataClassPrivateConstructor")

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
     * Value signalling the concurrent job completed successfully with a result.
     *
     * To get an instance of this class, use [Outcome.succeeded]:
     * ```java
     * Outcome<String> outcome = Outcome.succeeded("Hello");
     * // ... alternatively:
     * Outcome<String> outcome = Outcome.Succeeded.instance("Hello");
     * ```
     *
     * Starting with Java 16 this can be used in pattern-matching:
     * ```java
     * if (outcome instanceof Outcome.Succeeded<String> succeeded) {
     *   System.out.println("Value is: " + succeeded.value());
     * }
     * ```
     */
    data class Succeeded<out T> private constructor(
        private val _value: T
    ) : Outcome<T> {
        val value: T
            @JvmName("value")
            get() = _value

        override fun getOrThrow(): T {
            return value
        }

        companion object {
            @JvmStatic
            @JvmName("instance")
            operator fun <T> invoke(value: T): Outcome<T> =
                Succeeded(value)
        }
    }

    /**
     * Value signaling that the concurrent job failed with an `exception`.
     *
     * To get an instance of this class, use [Outcome.failed]:
     * ```java
     * Outcome<String> outcome = Outcome.failed(new RuntimeException("Boom!"));
     * // ... alternatively:
     * Outcome<String> outcome = Outcome.Failed.instance(new RuntimeException("Boom!"));
     * ```
     *
     * Starting with Java 16 this can be used in pattern-matching:
     * ```java
     * if (outcome instanceof Outcome.Failed failed) {
     *    failed.exception().printStackTrace();
     * }
     * ```
     */
    data class Failed private constructor(
        private val _exception: Throwable
    ) : Outcome<Nothing> {
        val exception: Throwable
            @JvmName("exception")
            get() = _exception

        @Throws(ExecutionException::class)
        override fun getOrThrow(): Nothing {
            throw ExecutionException(exception)
        }

        companion object {
            @JvmStatic
            @JvmName("instance")
            operator fun <T> invoke(exception: Throwable): Outcome<T> =
                Failed(exception)
        }
    }

    /**
     * Value signalling that the concurrent job was cancelled.
     *
     * To get an instance of this class, in Java, use [Outcome.cancelled]:
     * ```java
     * Outcome<String> outcome = Outcome.cancelled()
     * // ... alternatively:
     * Outcome<String> outcome = Outcome.Cancelled.instance();
     * ```
     *
     * Starting with Java 16 this can be used in pattern-matching:
     * ```java
     * if (outcome instanceof Outcome.Cancelled cancelled) {
     *    // *cancelled* is the instance of type *Outcome.Cancelled*
     * }
     * ```
     */
    data object Cancelled: Outcome<Nothing> {
        @Throws(CancellationException::class)
        override fun getOrThrow(): Nothing {
            throw CancellationException()
        }

        @JvmStatic
        @JvmName("instance")
        operator fun <T> invoke(): Outcome<T> =
            Cancelled
    }

    companion object {
        @JvmStatic fun <T> succeeded(value: T): Outcome<T> =
            Succeeded(value)
        @JvmStatic fun <T> failed(exception: Throwable): Outcome<T> =
            Failed(exception)
        @JvmStatic fun <T> cancelled(): Outcome<T> =
            Cancelled()
    }
}
