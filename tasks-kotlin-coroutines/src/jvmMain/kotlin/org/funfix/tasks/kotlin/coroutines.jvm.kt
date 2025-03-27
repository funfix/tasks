@file:JvmName("CoroutinesJvmKt")
@file:OptIn(DelicateCoroutinesApi::class)

package org.funfix.tasks.kotlin

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.funfix.tasks.jvm.CompletionCallback
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resumeWithException
import org.funfix.tasks.jvm.Outcome

public actual suspend fun <T> PlatformTask<out T>.runSuspended(executor: Executor?): T =
    run {
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
            is Outcome.Success -> onSuccess(outcome.value)
            is Outcome.Failure -> onFailure(outcome.exception)
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
            cont.resumeWithException(kotlinx.coroutines.CancellationException())
        }
    }
}

public actual suspend fun <T> Task.Companion.fromSuspended(
    coroutineContext: CoroutineContext,
    block: suspend () -> T
): Task<T> = Task(
    PlatformTask.fromAsync { executor, callback ->
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
)

public actual suspend fun <T> Task<T>.runSuspended(executor: Executor?): T =
    asPlatform.runSuspended(executor)
