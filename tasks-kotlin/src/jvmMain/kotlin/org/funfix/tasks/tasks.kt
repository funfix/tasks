package org.funfix.tasks

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resumeWithException

suspend fun <T> Task<T>.awaitSuspended(): T = run {
    val executor = currentFiberExecutor()
    suspendCancellableCoroutine { cont ->
        val callback = CompletionCallback<T> { outcome ->
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
        }
        try {
            val token = this.startAsync(executor, callback)
            cont.invokeOnCancellation {
                token.cancel()
            }
        } catch (e: Exception) {
            cont.resumeWithException(e)
        }
    }
}

@OptIn(DelicateCoroutinesApi::class)
fun <T> Task.Companion.fromSuspended(block: suspend () -> T): Task<T> =
    create { executor, callback ->
        val context = executor.asCoroutineDispatcher()
        val job = GlobalScope.launch(context) {
            try {
                val r = block()
                callback.complete(Outcome.succeeded(r))
            } catch (e: Throwable) {
                UncaughtExceptionHandler.rethrowIfFatal(e)
                when (e) {
                    is kotlinx.coroutines.CancellationException,
                        is TaskCancellationException,
                        is InterruptedException ->
                        callback.complete(Outcome.cancelled())
                    else ->
                        callback.complete(Outcome.failed(e))
                }
            }
        }
        Cancellable {
            job.cancel()
        }
    }
