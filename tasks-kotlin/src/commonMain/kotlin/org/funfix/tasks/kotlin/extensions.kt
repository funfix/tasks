@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

/**
 * Extensions for blah blah.
 */
package org.funfix.tasks.kotlin

import kotlinx.coroutines.CoroutineDispatcher
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

public expect class Task<T>

public expect fun <T> Task<out T>.executeAsync(
    executor: Executor? = null,
    callback: (Outcome<T>) -> Unit
): Cancellable

/**
 * Similar with `executeBlocking`, however this is a "suspended" function,
 * to be executed in the context of [kotlinx.coroutines].
 *
 * NOTES:
 * - The [CoroutineDispatcher], made available via the "coroutine context", is
 *   used to execute the task, being passed to the task's implementation as an
 *   `Executor`.
 * - The coroutine's cancellation protocol cooperates with that of [Task],
 *   so cancelling the coroutine will also cancel the task (including the
 *   possibility for back-pressuring on the fiber's completion after
 *   cancellation).
 *
 * @param executor is an override of the `Executor` to be used for executing
 *       the task. If `null`, the `Executor` will be derived from the
 *       `CoroutineDispatcher`
 */
public expect suspend fun <T> Task<out T>.executeSuspended(
    executor: Executor? = null
): T

public expect fun <T> taskFromAsync(
    start: (Executor, (Outcome<T>) -> Unit) -> Cancellable
): Task<T>

public expect fun <T> taskFromSuspended(
    coroutineContext: CoroutineContext = EmptyCoroutineContext,
    block: suspend () -> T
): Task<T>
