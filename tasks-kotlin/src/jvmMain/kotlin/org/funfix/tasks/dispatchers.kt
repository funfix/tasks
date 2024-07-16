package org.funfix.tasks

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import org.funfix.tasks.internals.CoroutineDispatcherAsFiberExecutor
import org.funfix.tasks.internals.FiberExecutorAsCoroutineDispatcher
import org.jetbrains.annotations.BlockingExecutor
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.coroutineContext

private val virtualThreadsDispatcherRef by lazy {
    try {
        VirtualThreads
            .executorService("VirtualThreadsDispatcher-worker-")
            .asCoroutineDispatcher()
    } catch (_: VirtualThreads.NotSupportedException) {
        null
    }
}

@Suppress("UnusedReceiverParameter")
val Dispatchers.VirtualThreads: @BlockingExecutor CoroutineDispatcher?
    get() = virtualThreadsDispatcherRef

fun Dispatchers.virtualThreadsOrBackup(backup: () -> CoroutineDispatcher = { IO }): @BlockingExecutor CoroutineDispatcher =
    VirtualThreads ?: backup()

private suspend fun currentDispatcher(): CoroutineDispatcher {
    // Access the coroutineContext to get the ContinuationInterceptor
    val continuationInterceptor = coroutineContext[ContinuationInterceptor]
    return continuationInterceptor as? CoroutineDispatcher ?: Dispatchers.Default
}

fun FiberExecutor.asCoroutineDispatcher(): CoroutineDispatcher =
    when (this) {
        is CoroutineDispatcherAsFiberExecutor -> dispatcher
        else -> FiberExecutorAsCoroutineDispatcher(this)
    }

fun CoroutineDispatcher.asFiberExecutor(): FiberExecutor =
    when (this) {
        is FiberExecutorAsCoroutineDispatcher -> executor
        else -> CoroutineDispatcherAsFiberExecutor(this)
    }

suspend fun currentFiberExecutor(): FiberExecutor =
    currentDispatcher().asFiberExecutor()
