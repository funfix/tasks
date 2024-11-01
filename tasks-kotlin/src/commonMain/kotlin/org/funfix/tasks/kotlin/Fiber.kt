@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.funfix.tasks.kotlin

/**
 * An alias for a platform-specific implementation that powers [Fiber].
 */
public expect interface PlatformFiber<T>

/**
 * A fiber is a running task being executed concurrently, and that can be
 * joined/awaited or cancelled.
 *
 * This is the equivalent of Kotlin's `Deferred` type.
 *
 * This is designed to be a compile-time type that's going to be erased at
 * runtime. Therefore, for the JVM at least, when using it in your APIs, it
 * won't pollute it with Kotlin-specific wrappers.
 */
public expect value class Fiber<out T> internal constructor(
    public val asPlatform: PlatformFiber<out T>
): Cancellable {
    /**
     * Cancels the fiber, which will eventually stop the running fiber (if
     * it's still running), completing it via "cancellation".
     *
     * This manifests either in a [TaskCancellationException] being thrown by
     * [resultOrThrow], or in the completion callback being triggered.
     */
    public override fun cancel()
}

/**
 * Converts the source to a [Kotlin Fiber][Fiber].
 *
 * E.g., can convert from a `jvm.Fiber` to a `kotlin.Fiber`.
 */
public fun <T> PlatformFiber<out T>.asKotlin(): Fiber<T> =
    Fiber(this)

/**
 * Returns the result of the completed fiber.
 *
 * This method does not block for the result. In case the fiber is not
 * completed, it throws [FiberNotCompletedException]. Therefore, by contract,
 * it should be called only after the fiber was "joined".
 *
 * @return the result of the concurrent task, if successful.
 * @throws TaskCancellationException if the task was cancelled concurrently,
 * thus being completed via cancellation.
 * @throws FiberNotCompletedException if the fiber is not completed yet.
 * @throws Throwable if the task finished with an exception.
 */
public expect val <T> Fiber<T>.resultOrThrow: T

/**
 * Returns the [Outcome] of the completed fiber, or `null` in case the
 * fiber is not completed yet.
 *
 * This method does not block for the result. In case the fiber is not
 * completed, it returns `null`. Therefore, it should be called after
 * the fiber was "joined".
 */
public expect val <T> Fiber<T>.outcomeOrNull: Outcome<T>?

/**
 * Waits until the fiber completes, and then runs the given callback to
 * signal its completion.
 *
 * Completion includes cancellation. Triggering [Fiber.cancel] before
 * [joinAsync] will cause the fiber to get cancelled, and then the
 * "join" back-pressures on cancellation.
 *
 * @param onComplete is the callback to run when the fiber completes
 *                   (successfully, or with failure, or cancellation)
 */
public expect fun <T> Fiber<T>.joinAsync(onComplete: Runnable): Cancellable

/**
 * Waits until the fiber completes, and then runs the given callback
 * to signal its completion.
 *
 * This method can be executed as many times as necessary, with the
 * result of the `Fiber` being memoized. It can also be executed
 * after the fiber has completed, in which case the callback will be
 * executed immediately.
 *
 * @param callback will be called with the result when the fiber completes.
 *
 * @return a [Cancellable] that can be used to unregister the callback,
 * in case the caller is no longer interested in the result. Note this
 * does not cancel the fiber itself.
 */
public expect fun <T> Fiber<T>.awaitAsync(callback: Callback<T>): Cancellable
