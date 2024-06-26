package org.funfix.tasks

sealed interface Outcome<out T> {
    @JvmRecord
    data class Success<out T>(val value: T) : Outcome<T>

    @JvmRecord
    data class Failure<out T>(val exception: Throwable) : Outcome<T>

    @JvmRecord
    data class Cancellation<out T>(val exception: CancellationException?) : Outcome<T>

    companion object {
        @JvmStatic fun <T> success(value: T): Outcome<T> =
            Success(value)
        @JvmStatic fun <T> failure(exception: Throwable): Outcome<T> =
            Failure(exception)
        @JvmStatic fun <T> cancellation(exception: CancellationException? = null): Outcome<T> =
            Cancellation(exception)
    }
}
