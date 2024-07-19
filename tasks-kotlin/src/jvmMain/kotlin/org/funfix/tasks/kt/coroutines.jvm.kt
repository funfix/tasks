@file:JvmName("CoroutinesJvmKt")
@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.funfix.tasks.kt

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.asExecutor
import org.funfix.tasks.CompletionCallback
import org.funfix.tasks.UncaughtExceptionHandler
import org.funfix.tasks.VirtualThreads
import org.jetbrains.annotations.BlockingExecutor
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resumeWithException

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

internal actual fun buildExecutor(dispatcher: CoroutineDispatcher): Executor =
    dispatcher.asExecutor()

internal actual fun buildCoroutineDispatcher(executor: Executor): CoroutineDispatcher =
    executor.asCoroutineDispatcher()

internal actual class CoroutineAsCompletionCallback<T> actual constructor(
    private val cont: CancellableContinuation<T>
) : CompletionCallback<T> {
    private val isActive = AtomicBoolean(true)

    private inline fun completeWith(crossinline block: () -> Unit): Boolean =
        if (isActive.getAndSet(false)) {
            block()
            true
        } else {
            false
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
        completeWith {
            cont.resumeWithException(kotlinx.coroutines.CancellationException())
        }
    }
}
