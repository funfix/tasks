@file:Suppress("DataClassPrivateConstructor")

package org.funfix.tasks

import org.funfix.tasks.support.ExecutionException
import org.funfix.tasks.support.NonBlocking
import kotlin.jvm.JvmName
import kotlin.jvm.JvmStatic

/**
 * Represents the outcome of a concurrent computation (e.g., `Fiber`).
 */
@NonBlocking
public sealed interface Outcome<out T> {
    /**
     * Returns the value of the task if it was successful, or throws an exception.
     *
     * @return the successful value (in case the outcome is [Success])
     *
     * @throws ExecutionException if the task failed with an exception ([Failure])
     * @throws TaskCancellationException if the task was cancelled ([Cancellation])
     */
    @Throws(ExecutionException::class, TaskCancellationException::class)
    public fun getOrThrow(): T

    /**
     * Value signalling the concurrent job completed successfully with a result.
     *
     * To get an instance of this class, use [Outcome.success]:
     * ```java
     * Outcome<String> outcome = Outcome.success("Hello");
     * // ... alternatively:
     * Outcome<String> outcome = Outcome.Success.instance("Hello");
     * ```
     *
     * Starting with Java 16 this can be used in pattern-matching:
     * ```java
     * if (outcome instanceof Outcome.Success<String> success) {
     *   System.out.println("Value is: " + success.value());
     * }
     * ```
     */
    public data class Success<out T> private constructor(
        private val _value: T
    ) : Outcome<T> {
        val value: T
            @JvmName("value")
            get() = _value

        override fun getOrThrow(): T {
            return value
        }

        public companion object {
            @JvmStatic
            @JvmName("instance")
            public operator fun <T> invoke(value: T): Outcome<T> =
                Success(value)
        }
    }

    /**
     * Value signaling that the concurrent job failed with an `exception`.
     *
     * To get an instance of this class, use [Outcome.failure]:
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
    public data class Failure private constructor(
        private val _exception: Throwable
    ) : Outcome<Nothing> {
        val exception: Throwable
            @JvmName("exception")
            get() = _exception

        @Throws(ExecutionException::class)
        override fun getOrThrow(): Nothing {
            throw ExecutionException(exception)
        }

        public companion object {
            @JvmStatic
            @JvmName("instance")
            public operator fun <T> invoke(exception: Throwable): Outcome<T> =
                Failure(exception)
        }
    }

    /**
     * Value signalling that the concurrent job was cancelled.
     *
     * To get an instance of this class, in Java, use [Outcome.cancellation]:
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
    public data object Cancellation: Outcome<Nothing> {
        @Throws(TaskCancellationException::class)
        override fun getOrThrow(): Nothing {
            throw TaskCancellationException()
        }

        @JvmStatic
        @JvmName("instance")
        public operator fun <T> invoke(): Outcome<T> =
            Cancellation
    }

    public companion object {
        @JvmStatic
        public fun <T> success(value: T): Outcome<T> =
            Success(value)

        @JvmStatic
        public fun <T> failure(exception: Throwable): Outcome<T> =
            Failure(exception)

        @JvmStatic
        public fun <T> cancellation(): Outcome<T> =
            Cancellation()
    }
}
