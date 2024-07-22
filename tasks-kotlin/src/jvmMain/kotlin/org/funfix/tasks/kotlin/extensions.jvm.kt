@file:JvmName("ExtensionsKt")
@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
@file:OptIn(DelicateCoroutinesApi::class)

package org.funfix.tasks.kotlin

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.funfix.tasks.jvm.CompletionCallback
import org.funfix.tasks.jvm.UncaughtExceptionHandler
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resumeWithException

public actual typealias Task<T> = org.funfix.tasks.jvm.Task<T>

public actual fun <T> Task<out T>.executeAsync(
    executor: Executor?,
    callback: (Outcome<T>) -> Unit
): Cancellable =
    this.executeAsync(executor ?: GlobalExecutor, callback.asJava())

public actual suspend fun <T> Task<out T>.executeSuspended(executor: Executor?): T =
    run {
        val executorOrDefault = executor ?: currentDispatcher().asExecutor()
        suspendCancellableCoroutine { cont ->
            val contCallback = CoroutineAsCompletionCallback(cont)
            try {
                val token = executeAsync(executorOrDefault, contCallback)
                cont.invokeOnCancellation {
                    token.cancel()
                }
            } catch (e: Throwable) {
                UncaughtExceptionHandler.rethrowIfFatal(e)
                contCallback.onFailure(e)
            }
        }
    }


public actual fun <T> taskFromAsync(start: (Executor, (Outcome<T>) -> Unit) -> Cancellable): Task<T> =
    Task.create { executor, cb ->
        start(executor, cb.asKotlin())
    } as Task<T>

/**
 * Internal API: gets the current [CoroutineDispatcher] from the coroutine context.
 */
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

internal fun <T> ((Outcome<T>) -> Unit).asJava(): CompletionCallback<T> =
    object : CompletionCallback<T> {
        override fun onSuccess(value: T): Unit = this@asJava(Outcome.Success(value))
        override fun onFailure(e: Throwable): Unit = this@asJava(Outcome.Failure(e))
        override fun onCancellation(): Unit = this@asJava(Outcome.Cancellation)
    }

internal fun <T> CompletionCallback<in T>.asKotlin(): (Outcome<T>) -> Unit = { outcome ->
    when (outcome) {
        is Outcome.Success -> onSuccess(outcome.value)
        is Outcome.Failure -> onFailure(outcome.exception)
        is Outcome.Cancellation -> onCancellation()
    }
}

public actual fun <T> taskFromSuspended(
    coroutineContext: CoroutineContext,
    block: suspend () -> T
): Task<T> =
    Task.create { executor, callback ->
        val job = GlobalScope.launch(
            executor.asCoroutineDispatcher() + coroutineContext
        ) {
            try {
                val r = block()
                callback.onSuccess(r)
            } catch (e: Throwable) {
                UncaughtExceptionHandler.rethrowIfFatal(e)
                when (e) {
                    is CancellationException,
                    is TaskCancellationException,
                    is InterruptedException ->
                        callback.onCancellation()
                    else ->
                        callback.onFailure(e)
                }
            }
        }
        Cancellable {
            job.cancel()
        }
    } as Task<T>
