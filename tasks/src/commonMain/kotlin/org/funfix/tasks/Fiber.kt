@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.funfix.tasks

import org.funfix.tasks.support.NonBlocking
import org.funfix.tasks.support.Runnable

/**
 * Represents a [Task] that has started execution and is running concurrently.
 *
 * @param T is the type of the value that the task will complete with
 */
public expect interface Fiber<out T> : Cancellable {
    /**
     * @return the [Outcome] of the task, if it has completed, or `null` if the
     * task is still running.
     */
    public val outcome: Outcome<T>?

    /**
     * Registers a callback that will be invoked when the task completes.
     *
     * @return a [Cancellable] reference that can be used to unregister
     * the callback. Note — cancelling the token will not cancel the running
     * task, it will only unregister the callback.
     */
    @NonBlocking
    public fun joinAsync(onComplete: Runnable): Cancellable

    /**
     * Tries cancelling the running task.
     */
    @NonBlocking
    override fun cancel()
}
