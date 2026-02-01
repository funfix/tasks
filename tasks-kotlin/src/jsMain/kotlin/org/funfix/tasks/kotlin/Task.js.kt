@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.funfix.tasks.kotlin

import kotlin.js.Promise

public actual class PlatformTask<T>(
    private val f: (Executor, (Outcome<T>) -> Unit) -> Cancellable
) {
    public operator fun invoke(
        executor: Executor,
        callback: (Outcome<T>) -> Unit
    ): Cancellable =
        f(executor, callback)
}

public actual value class Task<out T> public actual constructor(
    public actual val asPlatform: PlatformTask<out T>
) {
    public actual companion object
}

public actual fun <T> Task<T>.runAsync(
    executor: Executor?,
    callback: (Outcome<T>) -> Unit
): Cancellable {
    val protected = callback.protect()
    try {
        return asPlatform.invoke(
            executor ?: SharedIOExecutor,
            protected
        )
    } catch (e: Throwable) {
        UncaughtExceptionHandler.rethrowIfFatal(e)
        protected(Outcome.failure(e))
        return Cancellable.empty
    }
}

public actual fun <T> Task.Companion.fromAsync(
    start: (Executor, Callback<T>) -> Cancellable
): Task<T> =
    Task(PlatformTask { executor, cb ->
        val cRef = MutableCancellable()
        TrampolineExecutor.execute {
            cRef.set(start(executor, cb))
        }
        cRef
    })

internal fun <T> Callback<T>.protect(): Callback<T> {
    var isWaiting = true
    return { o ->
        if (o is Outcome.Failure) {
            UncaughtExceptionHandler.logOrRethrow(o.exception)
        }
        if (isWaiting) {
            isWaiting = false
            TrampolineExecutor.execute {
                this@protect.invoke(o)
            }
        }
    }
}

public actual fun <T> Task<T>.ensureRunningOnExecutor(executor: Executor?): Task<T> =
    Task(PlatformTask { injectedExecutor, callback ->
        val ec = executor ?: injectedExecutor
        val cRef = MutableCancellable()
        ec.execute {
            val c = this@ensureRunningOnExecutor.asPlatform.invoke(ec, callback)
            cRef.set(c)
        }
        cRef
    })

private fun promiseFailure(cause: Any?): Throwable =
    when (cause) {
        is Throwable -> cause
        null -> RuntimeException("Promise rejected")
        else -> RuntimeException(cause.toString())
    }

/**
 * Creates a task from a JavaScript [Promise] builder.
 *
 * Contract:
 * - The resulting task is not cancellable; cancellation does not
 *   affect the underlying promise.
 * - Completion mirrors the promise outcome: resolve maps to
 *   [Outcome.Success], reject maps to [Outcome.Failure].
 *
 * Example:
 * ```kotlin
 * val task = Task.fromPromise {
 *   Promise { resolve, _ -> resolve(1) }
 * }
 * ```
 */
public fun <T> Task.Companion.fromPromise(
    builder: () -> Promise<T>
): Task<T> =
    Task.fromAsync { _, callback ->
        var isDone = false
        try {
            val promise = builder()
            promise.then(
                { value ->
                    if (!isDone) {
                        isDone = true
                        callback(Outcome.Success(value))
                    }
                },
                { error ->
                    if (!isDone) {
                        isDone = true
                        callback(Outcome.Failure(promiseFailure(error)))
                    }
                }
            )
        } catch (e: Throwable) {
            callback(Outcome.Failure(e))
        }
        Cancellable.empty
    }

/**
 * Creates a task from a [CancellablePromise] builder.
 *
 * Contract:
 * - Cancellation triggers the provided [Cancellable], but the resulting
 *   task only completes when [CancellablePromise.join] completes.
 * - If [CancellablePromise.join] rejects, the task fails with that error.
 * - Otherwise, if cancellation was requested, the task completes with
 *   [Outcome.Cancellation].
 * - Otherwise, the task mirrors [CancellablePromise.promise].
 *
 * Example:
 * ```kotlin
 * val task = Task.fromCancellablePromise {
 *   val promise = Promise { resolve, _ -> resolve(1) }
 *   val join = Promise<Unit> { resolve, _ ->
 *     promise.then({ resolve(Unit) }, { resolve(Unit) })
 *   }
 *   CancellablePromise(promise, join, Cancellable.empty)
 * }
 * ```
 */
public fun <T> Task.Companion.fromCancellablePromise(
    builder: () -> CancellablePromise<T>
): Task<T> =
    Task.fromAsync { _, callback ->
        var isDone = false
        var isCancelled = false
        var token: Cancellable = Cancellable.empty
        try {
            val value = builder()
            token = value.cancellable
            value.join.then(
                {
                    if (!isDone) {
                        if (isCancelled) {
                            isDone = true
                            callback(Outcome.Cancellation)
                        } else {
                            value.promise.then(
                                { result ->
                                    if (!isDone) {
                                        isDone = true
                                        callback(Outcome.Success(result))
                                    }
                                },
                                { error ->
                                    if (!isDone) {
                                        isDone = true
                                        callback(Outcome.Failure(promiseFailure(error)))
                                    }
                                }
                            )
                        }
                    }
                },
                { error ->
                    if (!isDone) {
                        isDone = true
                        callback(Outcome.Failure(promiseFailure(error)))
                    }
                }
            )
        } catch (e: Throwable) {
            callback(Outcome.Failure(e))
        }
        Cancellable {
            isCancelled = true
            token.cancel()
        }
    }

/**
 * Executes the task and returns its result as a JavaScript [Promise].
 *
 * Contract:
 * - [Outcome.Success] resolves the promise.
 * - [Outcome.Failure] rejects the promise with the original exception.
 * - [Outcome.Cancellation] rejects with [TaskCancellationException].
 *
 * Example:
 * ```kotlin
 * Task.fromAsync<Int> { _, cb ->
 *   cb(Outcome.Success(1))
 *   Cancellable.empty
 * }.runToPromise()
 * ```
 */
public fun <T> Task<T>.runToPromise(): Promise<T> =
    Promise { resolve, reject ->
        runAsync { outcome ->
            when (outcome) {
                is Outcome.Success -> resolve(outcome.value)
                is Outcome.Failure -> reject(outcome.exception)
                is Outcome.Cancellation -> reject(TaskCancellationException())
            }
        }
    }

/**
 * Executes the task and returns a [CancellablePromise].
 *
 * The resulting [CancellablePromise.join] completes regardless of whether
 * the task succeeds, fails, or is cancelled.
 *
 * Example:
 * ```kotlin
 * val cp = Task.fromAsync<Int> { _, cb ->
 *   cb(Outcome.Success(1))
 *   Cancellable.empty
 * }.runToCancellablePromise()
 * cp.cancelAndJoin()
 * ```
 */
public fun <T> Task<T>.runToCancellablePromise(): CancellablePromise<T> {
    var token: Cancellable = Cancellable.empty
    val promise = Promise<T> { resolve, reject ->
        token = runAsync { outcome ->
            when (outcome) {
                is Outcome.Success -> resolve(outcome.value)
                is Outcome.Failure -> reject(outcome.exception)
                is Outcome.Cancellation -> reject(TaskCancellationException())
            }
        }
    }
    val join = Promise<Unit> { resolve, _ ->
        promise.then(
            { resolve(Unit) },
            { resolve(Unit) }
        )
    }
    return CancellablePromise(promise, join, Cancellable { token.cancel() })
}
