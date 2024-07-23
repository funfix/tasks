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
): Cancellable {
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
     * @throws NotCompletedException if the fiber is not completed yet.
     * @throws ExecutionException if the task finished with an exception.
     */
    public fun resultOrThrow(): T

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
    public fun joinAsync(onComplete: Runnable): Cancellable

    /**
     * Cancels the running fiber, starting its interruption process.
     */
    public override fun cancel()
}
