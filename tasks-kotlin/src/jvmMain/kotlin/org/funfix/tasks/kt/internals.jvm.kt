@file:JvmName("InternalsKt")
@file:JvmMultifileClass
@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.funfix.tasks.kt

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.asExecutor
import org.funfix.tasks.CompletionCallback
import org.funfix.tasks.Outcome
import org.funfix.tasks.UncaughtExceptionHandler
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resumeWithException

internal actual fun buildExecutor(dispatcher: CoroutineDispatcher): Executor =
    dispatcher.asExecutor()

internal actual fun buildCoroutineDispatcher(executor: Executor): CoroutineDispatcher =
    executor.asCoroutineDispatcher()

internal actual class CoroutineAsCompletionCallback<T> actual constructor(
    private val cont: CancellableContinuation<T>
) : CompletionCallback<T> {
    private val isActive = AtomicBoolean(true)

    actual override fun complete(outcome: Outcome<T>) {
        if (isActive.getAndSet(false)) {
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
