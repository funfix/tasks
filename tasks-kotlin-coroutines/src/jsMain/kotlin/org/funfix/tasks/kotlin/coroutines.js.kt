@file:OptIn(DelicateCoroutinesApi::class)

package org.funfix.tasks.kotlin

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resumeWithException

public actual suspend fun <T> PlatformTask<out T>.runSuspended(
    executor: Executor?
): T = run {
    val executorOrDefault = executor ?: buildExecutor(currentDispatcher())
    suspendCancellableCoroutine { cont ->
        val contCallback = cont.asCompletionCallback()
        try {
            val token = this.invoke(executorOrDefault, contCallback)
            cont.invokeOnCancellation {
                token.cancel()
            }
        } catch (e: Throwable) {
            UncaughtExceptionHandler.rethrowIfFatal(e)
            contCallback(Outcome.Failure(e))
        }
    }
}

internal fun buildExecutor(dispatcher: CoroutineDispatcher): Executor =
    DispatcherExecutor(dispatcher)

internal fun buildCoroutineDispatcher(
    @Suppress("UNUSED_PARAMETER") executor: Executor
): CoroutineDispatcher =
    // Building this CoroutineDispatcher from an Executor is problematic, and there's no
    // point in even trying on top of JS engines.
    Dispatchers.Default

private class DispatcherExecutor(val dispatcher: CoroutineDispatcher) : Executor {
    override fun execute(command: Runnable) {
        if (dispatcher.isDispatchNeeded(EmptyCoroutineContext)) {
            dispatcher.dispatch(
                EmptyCoroutineContext,
                kotlinx.coroutines.Runnable { command.run() }
            )
        } else {
            command.run()
        }
    }

    override fun toString(): String =
        dispatcher.toString()
}

internal fun <T> CancellableContinuation<T>.asCompletionCallback(): Callback<T> {
    var isActive = true
    return { outcome ->
        if (outcome is Outcome.Failure) {
            UncaughtExceptionHandler.rethrowIfFatal(outcome.exception)
        }
        if (isActive) {
            isActive = false
            when (outcome) {
                is Outcome.Success ->
                    resume(outcome.value) { _, _, _ ->
                        // on cancellation?
                    }
                is Outcome.Failure ->
                    resumeWithException(outcome.exception)
                is Outcome.Cancellation ->
                    resumeWithException(kotlinx.coroutines.CancellationException())
            }
        } else if (outcome is Outcome.Failure) {
            UncaughtExceptionHandler.logOrRethrow(outcome.exception)
        }
    }
}

/**
 * Creates a [Task] from a suspended block of code.
 */
public actual suspend fun <T> Task.Companion.fromSuspended(
    coroutineContext: CoroutineContext,
    block: suspend () -> T
): Task<T> =
    Task.fromAsync { executor, callback ->
        val job = GlobalScope.launch(
            buildCoroutineDispatcher(executor) + coroutineContext
        ) {
            try {
                val r = block()
                callback(Outcome.Success(r))
            } catch (e: Throwable) {
                UncaughtExceptionHandler.rethrowIfFatal(e)
                when (e) {
                    is CancellationException, is TaskCancellationException ->
                        callback(Outcome.Cancellation)
                    else ->
                        callback(Outcome.Failure(e))
                }
            }
        }
        Cancellable {
            job.cancel()
        }
    }

public actual suspend fun <T> Task<T>.runSuspended(executor: Executor?): T =
    asPlatform.runSuspended(executor)
