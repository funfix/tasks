@file:OptIn(DelicateCoroutinesApi::class)
@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.funfix.tasks.kt

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import org.funfix.tasks.CompletionCallback
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

    private inline fun completeWith(crossinline block: () -> Unit): Boolean {
        if (isActive) {
            isActive = false
            block()
            return true
        } else {
            return false
        }
    }

    actual override fun onSuccess(value: T) {
        completeWith {
            cont.resume(value) { _, _, _ ->
                // on cancellation?
            }
        }
    }

    actual override fun onFailure(e: Throwable) {
        if (!completeWith {
                cont.resumeWithException(e)
            }) {
            UncaughtExceptionHandler.logOrRethrow(e)
        }
    }

    actual override fun onCancellation() {
        completeWith { cont.cancel() }
    }
}
