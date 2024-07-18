@file:kotlin.jvm.JvmName("InternalsKt")
@file:kotlin.jvm.JvmMultifileClass

package org.funfix.tasks.kt

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.funfix.tasks.support.Executor
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.coroutineContext

internal expect fun coroutineDispatcherAsExecutor(dispatcher: CoroutineDispatcher): Executor

internal expect fun executorAsCoroutineDispatcher(executor: Executor): CoroutineDispatcher

internal suspend fun currentDispatcher(): CoroutineDispatcher {
    // Access the coroutineContext to get the ContinuationInterceptor
    val continuationInterceptor = coroutineContext[ContinuationInterceptor]
    return continuationInterceptor as? CoroutineDispatcher ?: Dispatchers.Default
}
