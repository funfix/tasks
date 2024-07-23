package org.funfix.tasks.kotlin

import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

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
public expect suspend fun <T> Task<T>.runSuspended(
    executor: Executor? = null
): T

/**
 * See documentation for [Task.runSuspended].
 */
public expect suspend fun <T> PlatformTask<out T>.runSuspended(
    executor: Executor? = null
): T

/**
 * Creates a [Task] from a suspended block of code.
 */
public expect suspend fun <T> Task.Companion.fromSuspended(
    coroutineContext: CoroutineContext = EmptyCoroutineContext,
    block: suspend () -> T
): Task<T>
