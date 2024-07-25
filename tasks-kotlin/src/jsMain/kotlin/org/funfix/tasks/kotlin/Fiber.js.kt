@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.funfix.tasks.kotlin

public actual interface PlatformFiber<T>: Cancellable {
    public val resultOrThrow: T

    public fun joinAsync(onComplete: Runnable): Cancellable
}

public actual value class Fiber<out T> public actual constructor(
    public actual val asPlatform: PlatformFiber<out T>
): Cancellable {
    actual override fun cancel(): Unit =
        asPlatform.cancel()
}

public actual val <T> Fiber<T>.resultOrThrow: T get() =
    asPlatform.resultOrThrow

public actual val <T> Fiber<T>.outcomeOrNull: Outcome<T>? get() =
    try {
        Outcome.Success(asPlatform.resultOrThrow)
    } catch (e: TaskCancellationException) {
        Outcome.Cancellation
    } catch (e: Throwable) {
        UncaughtExceptionHandler.rethrowIfFatal(e)
        Outcome.Failure(e)
    }

public actual fun <T> Fiber<T>.joinAsync(onComplete: Runnable): Cancellable =
    asPlatform.joinAsync(onComplete)

public actual fun <T> Fiber<T>.awaitAsync(callback: Callback<T>): Cancellable =
    joinAsync {
        try {
            callback(Outcome.Success(resultOrThrow))
        } catch (e: TaskCancellationException) {
            callback(Outcome.Cancellation)
        } catch (e: Throwable) {
            UncaughtExceptionHandler.rethrowIfFatal(e)
            callback(Outcome.Failure(e))
        }
    }
