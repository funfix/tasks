@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.funfix.tasks.kotlin

public actual interface PlatformFiber<T>: Cancellable {
    public fun resultOrThrow(): T

    public fun joinAsync(onComplete: Runnable): Cancellable
}

public actual value class Fiber<out T> public actual constructor(
    public actual val asPlatform: PlatformFiber<out T>
)

public actual fun <T> Fiber<T>.resultOrThrow(): T =
    asPlatform.resultOrThrow()

public actual fun <T> Fiber<T>.joinAsync(onComplete: Runnable): Cancellable =
    asPlatform.joinAsync(onComplete)

public actual fun <T> Fiber<T>.awaitAsync(callback: Callback<T>): Cancellable =
    joinAsync {
        try {
            val r = resultOrThrow()
            callback(Outcome.Success(r))
        } catch (e: TaskCancellationException) {
            callback(Outcome.Cancellation)
        } catch (e: Throwable) {
            UncaughtExceptionHandler.rethrowIfFatal(e)
            callback(Outcome.Failure(e))
        }
    }

public actual fun <T> Fiber<T>.cancel(): Unit =
    asPlatform.cancel()

