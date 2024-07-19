@file:kotlin.jvm.JvmName("CoroutinesKt")
@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.funfix.tasks.kt

import kotlinx.coroutines.*
import org.funfix.tasks.*
import org.funfix.tasks.support.Executor
import org.funfix.tasks.support.InterruptedException
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume

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

public suspend fun <T> Fiber<T>.cancelAndJoinSuspended() {
    cancel()
    joinSuspended()
}

public suspend fun <T> Fiber<T>.joinSuspended() {
    suspendCancellableCoroutine { cont ->
        val token = joinAsync {
            cont.resume(Unit)
        }
        cont.invokeOnCancellation {
            token.cancel()
        }
    }
}

public suspend fun <T> Fiber<T>.awaitSuspended(): T {
    joinSuspended()
    return when (val o = outcome!!) {
        is Outcome.Success ->
            o.value
        is Outcome.Failure ->
            throw o.exception
        is Outcome.Cancellation ->
            throw CancellationException("Fiber was cancelled")
    }
}

internal expect fun buildExecutor(dispatcher: CoroutineDispatcher): Executor

internal expect fun buildCoroutineDispatcher(executor: Executor): CoroutineDispatcher

internal suspend fun currentDispatcher(): CoroutineDispatcher {
    // Access the coroutineContext to get the ContinuationInterceptor
    val continuationInterceptor = coroutineContext[ContinuationInterceptor]
    return continuationInterceptor as? CoroutineDispatcher ?: Dispatchers.Default
}

/**
 * Internal API: wraps a [CancellableContinuation] into a [CompletionCallback].
 */
internal expect class CoroutineAsCompletionCallback<T>(
    cont: CancellableContinuation<T>
) : CompletionCallback<T> {
    override fun onSuccess(value: T)
    override fun onFailure(e: Throwable)
    override fun onCancellation()
    override fun onOutcome(outcome: Outcome<T>)
}
