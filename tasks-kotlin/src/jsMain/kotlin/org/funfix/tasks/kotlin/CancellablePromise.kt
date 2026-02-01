package org.funfix.tasks.kotlin

import kotlin.js.Promise

/**
 * This is a wrapper around a JavaScript [Promise] with a
 * [Cancellable] reference attached.
 *
 * A standard JavaScript [Promise] is not connected to its
 * asynchronous task and cannot be cancelled. Thus, if we want to cancel
 * a task, we need to keep a reference to a [Cancellable] object that
 * can do the job.
 *
 * Contract:
 * - [join] completes regardless of how the underlying computation ends,
 *   including when it gets cancelled via [cancellable].
 * - [join] does not reveal any outcome; it is only a completion signal.
 *
 * Example:
 * ```kotlin
 * val promise = Promise { resolve, _ -> resolve(1) }
 * val join = Promise<Unit> { resolve, _ ->
 *     promise.then({ resolve(Unit) }, { resolve(Unit) })
 * }
 * val cp = CancellablePromise(promise, join, Cancellable.empty)
 * cp.cancelAndJoin().then { /* completion observed */ }
 * ```
 */
public data class CancellablePromise<out T>(
    val promise: Promise<T>,
    val join: Promise<Unit>,
    val cancellable: Cancellable
) {
    /**
     * Triggers cancellation and then waits for [join] to complete.
     *
     * Note that [join] completes regardless of whether [promise]
     * resolves, rejects, or the computation is cancelled.
     */
    public fun cancelAndJoin(): Promise<Unit> {
        cancellable.cancel()
        return join
    }
}
