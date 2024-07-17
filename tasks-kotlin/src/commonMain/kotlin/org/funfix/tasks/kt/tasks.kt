@file:kotlin.jvm.JvmName("CoroutinesKt")
@file:kotlin.jvm.JvmMultifileClass

package org.funfix.tasks.kt

import kotlinx.coroutines.*
import org.funfix.tasks.*
import org.funfix.tasks.support.Executor
import org.funfix.tasks.support.InterruptedException
import org.funfix.tasks.support.Runnable
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resumeWithException

/**
 * Similar with [Task.executeBlocking], however this is a "suspended" function,
 * to be executed in the context of [kotlinx.coroutines].
 *
 * NOTES:
 * - The [kotlinx.coroutines.CoroutineDispatcher], made available via the
 *   "coroutine context", is used to execute the task, being passed to the
 *   task's implementation as an `Executor`.
 * - The coroutine's cancellation protocol cooperates with that of [Task],
 *   so cancelling the coroutine will also cancel the task (including the
 *   possibility for back-pressuring on the fiber's completion after
 *   cancellation).
 */
public suspend fun <T> Task<T>.executeSuspended(): T = run {
    val dispatcher = currentDispatcher()
    val executor = object : Executor {
        override fun execute(command: Runnable) {
            dispatcher.dispatch(
                EmptyCoroutineContext,
                kotlinx.coroutines.Runnable { command.run() }
            )
        }
    }
    suspendCancellableCoroutine { cont ->
        val callback = CompletionCallback<T> { outcome ->
            when (outcome) {
                is Outcome.Succeeded ->
                    cont.resume(outcome.value) { _, _, _ ->
                        // on cancellation?
                    }
                is Outcome.Failed ->
                    cont.resumeWithException(outcome.exception)
                is Outcome.Cancelled ->
                    cont.cancel()
            }
        }
        try {
            val token = this.executeAsync(executor, callback)
            cont.invokeOnCancellation {
                token.cancel()
            }
        } catch (e: Exception) {
            cont.resumeWithException(e)
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
        val context = coroutineDispatcherAsExecutor(executor)
        val job = GlobalScope.launch(context) {
            try {
                val r = block()
                callback.complete(Outcome.succeeded(r))
            } catch (e: Throwable) {
                UncaughtExceptionHandler.rethrowIfFatal(e)
                when (e) {
                    is CancellationException,
                        is TaskCancellationException,
                        is InterruptedException ->
                        callback.complete(Outcome.cancelled())
                    else ->
                        callback.complete(Outcome.failed(e))
                }
            }
        }
        Cancellable {
            job.cancel()
        }
    }

internal suspend fun currentDispatcher(): CoroutineDispatcher {
    // Access the coroutineContext to get the ContinuationInterceptor
    val continuationInterceptor = coroutineContext[ContinuationInterceptor]
    return continuationInterceptor as? CoroutineDispatcher ?: Dispatchers.Default
}

