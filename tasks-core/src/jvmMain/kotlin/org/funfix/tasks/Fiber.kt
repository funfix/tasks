package org.funfix.tasks

import java.time.Duration

/**
 * Represents a [Task] that has started execution and is running concurrently.
 *
 * @param T is the type of the value that the task will complete with
 */
interface Fiber<out T> : Cancellable {
    /**
     * @return the [Outcome] of the task, if it has completed, or `null` if the
     * task is still running.
     */
    fun outcome(): Outcome<T>?

    /**
     * Blocks the current thread until the result is available.
     *
     * @throws InterruptedException if the current thread was interrupted; however,
     * the concurrent task will still be running. Interrupting a `join` does not
     * cancel the task. For that, use [cancel].
     */
    @Throws(InterruptedException::class)
    fun joinBlocking()

    /**
     * Blocks the current thread until the result is available or
     * the given [timeout] is reached.
     *
     * @param timeout the maximum time to wait
     *
     * @return `true` if the task has completed, `false` if the timeout was reached
     *
     * @throws InterruptedException if the current thread was interrupted; however, the
     *         interruption of the current thread does not interrupt the fiber.
     */
    @Throws(InterruptedException::class)
    fun tryJoinBlockingTimed(timeout: Duration?): Boolean

    /**
     * Invokes the given [Runnable] when the task completes.
     */
    fun joinAsync(onComplete: Runnable): Cancellable
}
