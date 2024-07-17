@file:JvmName("VirtualThreadsKt")
@file:JvmMultifileClass

package org.funfix.tasks.kt

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import org.funfix.tasks.VirtualThreads
import org.jetbrains.annotations.BlockingExecutor

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
public val Dispatchers.VirtualThreads: @BlockingExecutor CoroutineDispatcher?
    get() = virtualThreadsDispatcherRef

public fun Dispatchers.virtualThreadsOrBackup(backup: () -> CoroutineDispatcher = { IO }): @BlockingExecutor CoroutineDispatcher =
    VirtualThreads ?: backup()
