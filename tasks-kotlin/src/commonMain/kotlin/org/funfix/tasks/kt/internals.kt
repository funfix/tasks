@file:kotlin.jvm.JvmName("InternalsKt")
@file:kotlin.jvm.JvmMultifileClass
@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.funfix.tasks.kt

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.funfix.tasks.CompletionCallback
import org.funfix.tasks.Outcome
import org.funfix.tasks.support.Executor
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.coroutineContext

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
    override fun complete(outcome: Outcome<T>)
}
