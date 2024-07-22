@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
@file:OptIn(DelicateCoroutinesApi::class)

package org.funfix.tasks.kotlin

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.js.Promise

public actual class Task<T>(
    private val f: (Executor, (Outcome<T>) -> Unit) -> Cancellable
) {
    public operator fun invoke(
        executor: Executor,
        callback: (Outcome<T>) -> Unit
    ): Cancellable {
        return f(executor, callback)
    }
}

/**
 * Similar with `executeBlocking`, however this is a "suspended" function,
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
public actual suspend fun <T> Task<out T>.executeSuspended(
    executor: Executor?
): T = run {
    val executorOrDefault = executor ?: buildExecutor(currentDispatcher())
    suspendCancellableCoroutine { cont ->
        val contCallback = cont.asCompletionCallback()
        try {
            val token = this.invoke(executorOrDefault, contCallback)
            cont.invokeOnCancellation {
                token.cancel()
            }
        } catch (e: Throwable) {
            UncaughtExceptionHandler.rethrowIfFatal(e)
            contCallback(Outcome.Failure(e))
        }
    }
}

public actual fun <T> Task<out T>.executeAsync(
    executor: Executor?,
    callback: (Outcome<T>) -> Unit
): Cancellable {
    val protected = callback.protect()
    return try {
        this.invoke(executor ?: GlobalExecutor, protected)
    } catch (e: Throwable) {
        UncaughtExceptionHandler.rethrowIfFatal(e)
        protected(Outcome.Failure(e))
        Cancellable.empty
    }
}

public fun <T> Task<out T>.executeToPromise(
    executor: Executor? = null
): CancellablePromise<T> {
    val cancel = MutableCancellable()
    val promise = Promise { resolve, reject ->
        val token = executeAsync(executor) { outcome ->
            when (outcome) {
                is Outcome.Success -> resolve(outcome.value)
                is Outcome.Failure -> reject(outcome.exception)
                is Outcome.Cancellation -> reject(TaskCancellationException())
            }
        }
        cancel.set {
            try {
                token.cancel()
            } finally {
                reject(TaskCancellationException())
            }
        }
    }
    return CancellablePromise(promise, cancel)
}

public actual fun <T> taskFromAsync(start: (Executor, (Outcome<T>) -> Unit) -> Cancellable): Task<T> =
    Task { executor, callback ->
        val cancel = MutableCancellable()
        TrampolineExecutor.execute {
            try {
                cancel.set(start(executor, callback))
            } catch (e: Throwable) {
                UncaughtExceptionHandler.rethrowIfFatal(e)
                callback(Outcome.Failure(e))
            }
        }
        cancel
    }

public actual fun <T> taskFromSuspended(
    coroutineContext: CoroutineContext,
    block: suspend () -> T
): Task<T> =
    taskFromAsync { executor, callback ->
        val job = GlobalScope.launch(
            buildCoroutineDispatcher(executor) + coroutineContext
        ) {
            try {
                val r = block()
                callback(Outcome.Success(r))
            } catch (e: Throwable) {
                UncaughtExceptionHandler.rethrowIfFatal(e)
                when (e) {
                    is CancellationException, is TaskCancellationException ->
                        callback(Outcome.Cancellation)
                    else ->
                        callback(Outcome.Failure(e))
                }
            }
        }
        Cancellable {
            job.cancel()
        }
    }

/**
 * Creates tasks from a builder of [CancellablePromise].
 *
 * This is the recommended way to work with [kotlin.js.Promise],
 * because such values are not cancellable. As such, the user
 * should provide an explicit [Cancellable] token that can be used.
 *
 * @param block is the thunk that will create the [CancellablePromise]
 * value. It's a builder because [Task] values are cold values (lazy,
 * not executed yet).
 *
 * @return a new task that upon execution will complete with the result of
 * the created `Promise`
 */
public fun <T> fromCancellablePromise(block: () -> CancellablePromise<T>): Task<T> =
    taskFromAsync { executor, callback ->
        val cancel = MutableCancellable()
        executor.execute {
            try {
                val promise = block()
                cancel.set(promise.cancellable)
                promise.promise.then(
                    onFulfilled = { callback(Outcome.Success(it)) },
                    onRejected = { callback(Outcome.Failure(it)) }
                )
                Cancellable { promise.cancellable.cancel() }
            } catch (e: Throwable) {
                UncaughtExceptionHandler.rethrowIfFatal(e)
                callback(Outcome.Failure(e))
                Cancellable.empty
            }
        }
        cancel
    }

/**
 * Converts a [kotlin.js.Promise] builder into a [Task].
 */
public fun <T> fromUncancellablePromise(block: () -> Promise<T>): Task<T> =
    fromCancellablePromise {
        val promise = block()
        CancellablePromise(promise, Cancellable.empty)
    }
