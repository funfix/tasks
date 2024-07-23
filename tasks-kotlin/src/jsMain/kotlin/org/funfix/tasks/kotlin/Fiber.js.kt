@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.funfix.tasks.kotlin

public actual interface PlatformFiber<T>: Cancellable {
    public fun resultOrThrow(): T

    public fun joinAsync(onComplete: Runnable): Cancellable
}

public actual value class Fiber<out T> internal actual constructor(
    public actual val asPlatform: PlatformFiber<out T>
): Cancellable {
    public actual fun resultOrThrow(): T =
        asPlatform.resultOrThrow()

    public actual fun joinAsync(onComplete: Runnable): Cancellable =
        asPlatform.joinAsync(onComplete)

    public actual override fun cancel() {
        asPlatform.cancel()
    }
}
