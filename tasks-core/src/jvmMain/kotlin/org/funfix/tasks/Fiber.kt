package org.funfix.tasks

import java.util.concurrent.ExecutionException

/**
 * Represents a [Task] that was started concurrently and that can be
 * waited on for its result, or cancelled.
 */
interface Fiber<out T> : Cancellable {
    /**
     * For a concurrent job that finished, returns the result.
     *
     * @throws ExecutionException if the concurrent computation threw an exception
     *
     * @throws CancellationException if the concurrent computation was cancelled
     *
     * @throws NotCompletedException if the computation hasn't completed yet — this
     * is a protocol violation, a bug, as the caller should have waited for
     * `join` to complete before calling `resultOrThrow`.
     */
    @Throws(
        ExecutionException::class,
        CancellationException::class,
        NotCompletedException::class
    )
    fun resultOrThrow(): T

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
     * Invokes the given `Runnable` when the task completes.
     */
    fun joinAsync(onComplete: Runnable): Cancellable
}
