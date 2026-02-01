@file:JvmName("CoroutinesJvmKt")
@file:OptIn(DelicateCoroutinesApi::class)

package org.funfix.tasks.kotlin

import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellableContinuation
import org.funfix.tasks.jvm.*

/**
 * Similar with `runBlocking`, however this is a "suspended" function,
 * to be executed in the context of kotlinx.coroutines.
 *
 * NOTES:
 * - The `kotlinx.coroutines.CoroutineDispatcher`, made available via the
 *   "coroutine context", is used to execute the task, being passed to
 *   the task's implementation as an `Executor`.
 * - The coroutine's cancellation protocol cooperates with that of [Task],
 *   so cancelling the coroutine will also cancel the task (including the
 *   possibility for back-pressuring on the fiber's completion after
 *   cancellation).
 *
 * @param executor is an override of the `Executor` to be used for executing
 *       the task. If `null`, the `Executor` will be derived from the
 *       `CoroutineDispatcher`
 */
public suspend fun <T> Task<T>.runSuspending(
    executor: Executor? = null
): T = run {
    val executorOrDefault = executor ?: currentDispatcher().asExecutor()
    suspendCancellableCoroutine { cont ->
        val contCallback = CoroutineAsCompletionCallback(cont)
        try {
            val token = runAsync(executorOrDefault, contCallback)
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
 * Creates a [Task] from a suspended block of code.
 */
public fun <T> suspendAsTask(
    coroutineContext: CoroutineContext = EmptyCoroutineContext,
    block: suspend () -> T
): Task<T> = Task.fromAsync { executor, callback ->
    val job = GlobalScope.launch(
        executor.asCoroutineDispatcher() + coroutineContext
    ) {
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

/**
 * Internal API: wraps a [CancellableContinuation] into a [CompletionCallback].
 */
internal class CoroutineAsCompletionCallback<T>(
    private val cont: CancellableContinuation<T>
) : CompletionCallback<T> {
    private val isActive = AtomicBoolean(true)

    private inline fun completeWith(crossinline block: () -> Unit): Boolean =
        if (isActive.getAndSet(false)) {
            block()
            true
        } else {
            false
        }

    override fun onOutcome(outcome: Outcome<T>) {
        when (outcome) {
            is Outcome.Success -> onSuccess(outcome.value())
            is Outcome.Failure -> onFailure(outcome.exception())
            is Outcome.Cancellation -> onCancellation()
        }
    }

    override fun onSuccess(value: T) {
        completeWith {
            cont.resume(value) { _, _, _ ->
                // on cancellation?
            }
        }
    }

    override fun onFailure(e: Throwable) {
        if (!completeWith {
                cont.resumeWithException(e)
            }) {
            UncaughtExceptionHandler.logOrRethrow(e)
        }
    }

    override fun onCancellation() {
        completeWith {
            cont.resumeWithException(CancellationException())
        }
    }
}

/**
 * Internal API: gets the current [kotlinx.coroutines.CoroutineDispatcher] from the coroutine context.
 */
internal suspend fun currentDispatcher(): CoroutineDispatcher {
    val continuationInterceptor = currentCoroutineContext()[ContinuationInterceptor]
    return continuationInterceptor as? CoroutineDispatcher ?: Dispatchers.Default
}
