package org.funfix.tasks.kotlin

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resumeWithException

internal fun buildExecutor(dispatcher: CoroutineDispatcher): Executor =
    DispatcherExecutor(dispatcher)

internal fun buildCoroutineDispatcher(executor: Executor): CoroutineDispatcher =
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

internal suspend fun currentDispatcher(): CoroutineDispatcher {
    // Access the coroutineContext to get the ContinuationInterceptor
    val continuationInterceptor = coroutineContext[ContinuationInterceptor]
    return continuationInterceptor as? CoroutineDispatcher ?: Dispatchers.Default
}
