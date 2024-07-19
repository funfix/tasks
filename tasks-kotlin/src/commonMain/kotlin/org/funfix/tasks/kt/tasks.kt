@file:kotlin.jvm.JvmName("CoroutinesKt")
@file:kotlin.jvm.JvmMultifileClass

package org.funfix.tasks.kt

import kotlinx.coroutines.*
import org.funfix.tasks.*
import org.funfix.tasks.support.Executor
import org.funfix.tasks.support.InterruptedException

/**
 * Similar with [Task.executeBlocking], however this is a "suspended" function,
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
public suspend fun <T> Task<T>.executeSuspended(executor: Executor? = null): T = run {
    val dispatcher = currentDispatcher()
    val executorNonNull = executor ?: buildExecutor(dispatcher)
    suspendCancellableCoroutine { cont ->
        val contCallback = CoroutineAsCompletionCallback(cont)
        try {
            val token = this.executeAsync(executorNonNull, contCallback)
            cont.invokeOnCancellation {
                token.cancel()
            }
        } catch (e: Throwable) {
            UncaughtExceptionHandler.rethrowIfFatal(e)
            contCallback.onFailure(e)
        }
    }
}

/**
 * Converts a suspended function into a [Task].
 *
 * NOTES:
 * - The [kotlinx.coroutines.CoroutineDispatcher], made available via the
 *   "coroutine context", will be created from the `Executor` injected
 *   by the [Task] implementation.
 * - The [Task] cancellation protocol cooperates with that of the coroutine,
 *   so cancelling the task will also cleanly cancel the coroutine
 *   (including the possibility for back-pressuring on the coroutine's completion
 *   after cancellation).
 */
@OptIn(DelicateCoroutinesApi::class)
public fun <T> Task.Companion.fromSuspended(block: suspend () -> T): Task<T> =
    create { executor, callback ->
        val context = buildCoroutineDispatcher(executor)
        val job = GlobalScope.launch(context) {
            try {
                val r = block()
                callback.onSuccess(r)
            } catch (e: Throwable) {
                UncaughtExceptionHandler.rethrowIfFatal(e)
                when (e) {
                    is CancellationException,
                        is TaskCancellationException,
                        is InterruptedException ->
                            callback.onCancellation()
                    else ->
                        callback.onFailure(e)
                }
            }
        }
        Cancellable {
            job.cancel()
        }
    }

