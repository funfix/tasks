@file:JvmName("Extensions")

package org.funfix.tasks.kotlin

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.suspendCancellableCoroutine
import org.funfix.tasks.jvm.CompletionCallback
import org.funfix.tasks.jvm.UncaughtExceptionHandler
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resumeWithException

public typealias JvmTask<T> =
    org.funfix.tasks.jvm.Task<T>

public fun <T> JvmTask<T>.asKotlin(): Task<T> =
    Task(this)

/**
 * Similar with [Task.executeBlocking], however this is a "suspended" function,
 * to be executed in the context of [kotlinx.coroutines].
 *
 * NOTES:
 * - The [CoroutineDispatcher], made available via the "coroutine context", is
 *   used to execute the task, being passed to the task's implementation as an
 *   `Executor`.
 * - The coroutine's cancellation protocol cooperates with that of [Task],
 *   so cancelling the coroutine will also cancel the task (including the
 *   possibility for back-pressuring on the fiber's completion after
 *   cancellation).
 *
 * @param executor is an override of the `Executor` to be used for executing
 *       the task. If `null`, the `Executor` will be derived from the
 *       `CoroutineDispatcher`
 */
public suspend fun <T> JvmTask<out T>.executeCoroutine(executor: Executor? = null): T = run {
    val executorNonNull = executor ?: currentDispatcher().asExecutor()
    suspendCancellableCoroutine { cont ->
        val contCallback = CoroutineAsCompletionCallback(cont)
        try {
            val token = executeAsync(executorNonNull, contCallback)
            cont.invokeOnCancellation {
                token.cancel()
            }
        } catch (e: Throwable) {
            UncaughtExceptionHandler.rethrowIfFatal(e)
            contCallback.onFailure(e)
        }
    }
}

internal suspend fun currentDispatcher(): CoroutineDispatcher {
    // Access the coroutineContext to get the ContinuationInterceptor
    val continuationInterceptor = coroutineContext[ContinuationInterceptor]
    return continuationInterceptor as? CoroutineDispatcher ?: Dispatchers.Default
}

/**
 * Internal API: wraps a [CancellableContinuation] into a [CompletionCallback].
 */
internal class CoroutineAsCompletionCallback<T>(
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

    override fun onSuccess(value: T) {
        completeWith {
            cont.resume(value) { _, _, _ ->
                // on cancellation?
            }
        }
    }

    override fun onFailure(e: Throwable) {
        if (!completeWith {
                cont.resumeWithException(e)
            }) {
            UncaughtExceptionHandler.logOrRethrow(e)
        }
    }

    override fun onCancellation() {
        completeWith {
            cont.resumeWithException(kotlinx.coroutines.CancellationException())
        }
    }
}
