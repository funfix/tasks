package org.funfix.tasks.kotlin

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.ContinuationInterceptor
import kotlinx.coroutines.currentCoroutineContext

/**
 * Internal API: gets the current [CoroutineDispatcher] from the coroutine context.
 */
internal suspend fun currentDispatcher(): CoroutineDispatcher {
    // Access the coroutineContext to get the ContinuationInterceptor
    val continuationInterceptor = currentCoroutineContext()[ContinuationInterceptor]
    return continuationInterceptor as? CoroutineDispatcher ?: Dispatchers.Default
}
