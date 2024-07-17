@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.funfix.tasks

import org.funfix.tasks.support.*
import org.funfix.tasks.support.MutableCancellable
import kotlin.js.Promise

/**
 * Task implementation for JavaScript.
 */
public actual abstract class Task<out T> : Serializable {
    protected actual abstract val unsafeExecuteAsync: AsyncFun<T>

    /**
     * Starts the asynchronous execution of this task.
     *
     * @param callback will be invoked with the result when the task completes
     * @param executor is the [Executor] that may be used to run the task
     *
     * @return a [Cancellable] that can be used to cancel a running task
     */
    @NonBlocking
    public actual fun executeAsync(
        executor: Executor,
        callback: CompletionCallback<T>
    ): Cancellable {
        val protected = ProtectedCompletionCallback(callback)
        return try {
            unsafeExecuteAsync(executor, protected)
        } catch (e: Throwable) {
            UncaughtExceptionHandler.rethrowIfFatal(e)
            protected.complete(Outcome.failed(e))
            Cancellable.EMPTY
        }
    }

    @NonBlocking
    public actual fun executeAsync(callback: CompletionCallback<T>): Cancellable {
        return executeAsync(TaskExecutors.global, callback)
    }

    @NonBlocking
    public actual fun executeFiber(executor: Executor): Fiber<T> =
        Fiber.start(executor, unsafeExecuteAsync)

    @NonBlocking
    public actual fun executeFiber(): Fiber<T> =
        executeFiber(TaskExecutors.global)

    /**
     * Starts the asynchronous execution of this task, returning a [CancellablePromise].
     */
    @NonBlocking
    public fun executePromise(executor: Executor): CancellablePromise<T> {
        val cancel = MutableCancellable()
        val promise = Promise { resolve, reject ->
            val token = executeAsync(executor) { outcome ->
                when (outcome) {
                    is Outcome.Succeeded -> resolve(outcome.value)
                    is Outcome.Failed -> reject(outcome.exception)
                    is Outcome.Cancelled -> reject(TaskCancellationException())
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

    public fun executePromise(): CancellablePromise<T> {
        return executePromise(TaskExecutors.global)
    }

    public actual companion object {
        private operator fun <T> invoke(run: AsyncFun<T>): Task<T> =
            object : Task<T>() {
                override val unsafeExecuteAsync: AsyncFun<T> = run
            }

        @NonBlocking
        public actual fun <T> create(run: AsyncFun<T>): Task<T> =
            Task { executor, callback ->
                val cancel = MutableCancellable()
                TaskExecutors.trampoline.execute {
                    try {
                        cancel.set(run.invoke(executor, callback))
                    } catch (e: Throwable) {
                        UncaughtExceptionHandler.rethrowIfFatal(e)
                        callback.complete(Outcome.failed(e))
                    }
                }
                cancel
            }

        /**
         * Creates tasks from a builder of [CancellablePromise].
         *
         * This is the recommended way to work with [kotlin.js.Promise],
         * because such values are not cancellable. As such, the user
         * should provide an explicit [Cancellable] token that can be used.
         *
         * @param run is the [DelayedFun] that will create the [CancellablePromise]
         * value. It's a builder because [Task] values are cold values (lazy,
         * not executed yet).
         *
         * @return a new task that upon execution will complete with the result of
         * the created `Promise`
         */
        @NonBlocking
        public fun <T> fromCancellablePromise(run: DelayedFun<CancellablePromise<T>>): Task<T> =
            Task { executor, callback ->
                val cancel = MutableCancellable()
                executor.execute {
                    try {
                        val promise = run()
                        cancel.set(promise.cancellable)
                        promise.promise.then(
                            onFulfilled = { value ->
                                callback.complete(Outcome.succeeded(value))
                            },
                            onRejected = { e ->
                                callback.complete(Outcome.failed(e))
                            }
                        )
                        Cancellable { promise.cancellable.cancel() }
                    } catch (e: Throwable) {
                        UncaughtExceptionHandler.rethrowIfFatal(e)
                        callback.complete(Outcome.failed(e))
                        Cancellable.EMPTY
                    }
                }
                cancel
            }

        @NonBlocking
        public fun <T> fromUncancellablePromise(run: DelayedFun<Promise<T>>): Task<T> =
            fromCancellablePromise { CancellablePromise(run(), Cancellable.EMPTY) }
    }
}

private class ProtectedCompletionCallback<T> private constructor(
    private var listener: CompletionCallback<T>?
) : CompletionCallback<T>, Runnable {
    private var isWaiting = true
    private var outcome: Outcome<T>? = null

    override fun run() {
        val listener = this.listener
        if (listener != null) {
            listener.complete(outcome!!)
            // GC purposes
            this.outcome = null
            this.listener = null
        }
    }

    override fun complete(outcome: Outcome<T>) {
        if (isWaiting) {
            this.isWaiting = false
            this.outcome = outcome
            // Trampolined execution is needed because, with chained tasks,
            // we might end up with a stack overflow
            TaskExecutors.trampoline.execute(this)
        } else if (outcome is Outcome.Failed) {
            UncaughtExceptionHandler.logOrRethrow(outcome.exception)
        }
    }

    companion object {
        operator fun <T> invoke(listener: CompletionCallback<T>): CompletionCallback<T> =
            when {
                listener is ProtectedCompletionCallback<*> -> listener
                else -> ProtectedCompletionCallback(listener)
            }
    }
}
