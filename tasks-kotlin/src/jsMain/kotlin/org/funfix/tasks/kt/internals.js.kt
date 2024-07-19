@file:OptIn(DelicateCoroutinesApi::class)
@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.funfix.tasks.kt

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import org.funfix.tasks.CompletionCallback
import org.funfix.tasks.Outcome
import org.funfix.tasks.UncaughtExceptionHandler
import org.funfix.tasks.support.Executor
import org.funfix.tasks.support.Runnable
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resumeWithException

internal actual fun buildExecutor(dispatcher: CoroutineDispatcher): Executor =
    DispatcherExecutor(dispatcher)

internal actual fun buildCoroutineDispatcher(executor: Executor): CoroutineDispatcher =
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

internal actual class CoroutineAsCompletionCallback<T> actual constructor(
    private val cont: CancellableContinuation<T>
) : CompletionCallback<T> {
    private var isActive = true

    actual override fun complete(outcome: Outcome<T>) {
        if (isActive) {
            isActive = false
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
        } else if (outcome is Outcome.Failed) {
            UncaughtExceptionHandler.logOrRethrow(outcome.exception)
        }
    }
}
