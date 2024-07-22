@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.funfix.tasks.kotlin

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.cancellation.CancellationException
import kotlin.js.Promise

public actual class PlatformTask<T>(
    private val f: (Executor, (Outcome<T>) -> Unit) -> Cancellable
) {
    public operator fun invoke(
        executor: Executor?,
        callback: (Outcome<T>) -> Unit
    ): Cancellable {
        val ec = executor ?: TaskExecutors.global
        return f(ec, callback)
    }
}

public actual value class Task<out T> internal actual constructor(
    internal actual val self: PlatformTask<out T>
) {
    public actual suspend fun await(): T =
        await(buildExecutor(currentDispatcher()))

    public actual suspend fun await(executor: Executor): T = run {
        suspendCancellableCoroutine { cont ->
            val contCallback = cont.asCompletionCallback()
            try {
                val token = self.invoke(executor, contCallback)
                cont.invokeOnCancellation {
                    token.cancel()
                }
            } catch (e: Throwable) {
                UncaughtExceptionHandler.rethrowIfFatal(e)
                contCallback(Outcome.Failure(e))
            }
        }
    }

    public actual fun runAsync(
        executor: Executor,
        callback: (Outcome<T>) -> Unit
    ): Cancellable {
        val protected = callback.protect()
        return try {
            self.invoke(executor, protected)
        } catch (e: Throwable) {
            UncaughtExceptionHandler.rethrowIfFatal(e)
            protected(Outcome.Failure(e))
            Cancellable.empty
        }
    }

    public actual fun runAsync(callback: (Outcome<T>) -> Unit): Cancellable =
        runAsync(TaskExecutors.global, callback)

    public fun runToPromise(executor: Executor): CancellablePromise<T> {
        val cancel = MutableCancellable()
        val promise = Promise { resolve, reject ->
            val token = runAsync(executor) { outcome ->
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

    public fun runToPromise(): CancellablePromise<T> =
        runToPromise(TaskExecutors.global)

    public actual companion object {
        internal operator fun <T> invoke(f: (Executor, (Outcome<T>) -> Unit) -> Cancellable): Task<T> =
            Task(PlatformTask(f))

        public actual fun <T> create(f: (Executor, (Outcome<T>) -> Unit) -> Cancellable): Task<T> =
            Task { executor, callback ->
                val cancel = MutableCancellable()
                TaskExecutors.trampoline.execute {
                    try {
                        cancel.set(f(executor, callback))
                    } catch (e: Throwable) {
                        UncaughtExceptionHandler.rethrowIfFatal(e)
                        callback(Outcome.Failure(e))
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
         * @param block is the thunk that will create the [CancellablePromise]
         * value. It's a builder because [Task] values are cold values (lazy,
         * not executed yet).
         *
         * @return a new task that upon execution will complete with the result of
         * the created `Promise`
         */
        public fun <T> fromCancellablePromise(block: () -> CancellablePromise<T>): Task<T> =
            Task { executor, callback ->
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

        @OptIn(DelicateCoroutinesApi::class)
        public actual fun <T> fromSuspended(block: suspend () -> T): Task<T> = run {
            create { executor, callback ->
                val context = buildCoroutineDispatcher(executor)
                val job = GlobalScope.launch(context) {
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
        }
    }
}
