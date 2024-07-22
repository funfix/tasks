package org.funfix.tasks.kotlin

public sealed interface Outcome<out T> {
    public fun getOrThrow(): T =
        when (this) {
            is Success -> value
            is Failure -> throw exception
            is Cancellation -> throw TaskCancellationException("Task was cancelled")
        }

    public data class Success<out T>(val value: T): Outcome<T>
    public data class Failure(val exception: Throwable): Outcome<Nothing>
    public data object Cancellation: Outcome<Nothing>
}
