@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.funfix.tasks.kotlin

public expect interface PlatformFiber<T>

/**
 * A fiber is a running task being executed concurrently.
 *
 * This is the equivalent of Kotlin's `Deferred` type.
 */
public expect value class Fiber<out T> internal constructor(
    public val asPlatform: PlatformFiber<out T>
)

public fun <T> PlatformFiber<T>.asKotlin(): Fiber<T> =
    Fiber(this);

/**
 * Returns the result of the completed fiber.
 *
 * This method does not block for the result. In case the fiber is not
 * completed, it throws [NotCompletedException]. Therefore, by contract,
 * it should be called only after the fiber was "joined".
 *
 * @return the result of the concurrent task, if successful.
 * @throws TaskCancellationException if the task was cancelled concurrently,
 * thus being completed via cancellation.
 * @throws FiberNotCompletedException if the fiber is not completed yet.
 * @throws Throwable if the task finished with an exception.
 */
public expect fun <T> Fiber<T>.resultOrThrow(): T

/**
 * Waits until the fiber completes, and then runs the given callback to
 * signal its completion.
 *
 * Completion includes cancellation. Triggering [cancel] before
 * [joinAsync] will cause the fiber to get cancelled, and then the
 * "join" back-pressures on cancellation.
 *
 * @param onComplete is the callback to run when the fiber completes
 *                   (successfully, or with failure, or cancellation)
 */
public expect fun <T> Fiber<T>.joinAsync(onComplete: Runnable): Cancellable

/**
 * Cancels the fiber, which will eventually stop the running fiber (if
 * it's still running), completing it via "cancellation".
 * <p>
 * This manifests either in a [TaskCancellationException] being thrown by
 * [resultOrThrow], or in the completion callback being triggered.
 */
public expect fun <T> Fiber<T>.cancel()

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
