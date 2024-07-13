package org.funfix.tasks

import org.funfix.tasks.internals.ExecutedTaskFiber
import org.funfix.tasks.internals.MutableCancellable
import org.funfix.tasks.internals.ProtectedCompletionCallback
import org.funfix.tasks.internals.Trampoline
import java.time.Duration
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.AbstractQueuedSynchronizer
import java.io.Serializable

/**
 * Represents a suspended computation that can be executed asynchronously.
 *
 * To create a task, use one of its builders:
 * - [create]
 * - [createAsync]
 * - [fromBlockingIO]
 * - [fromBlockingFuture]
 * - [fromCancellableFuture]
 */
class Task<out T> private constructor(
    private val asyncFun: AsyncFun<T>
): Serializable {
    /**
     * Executes the task asynchronously.
     *
     * @param callback will be invoked with the result when the task completes
     * @param executor is the [FiberExecutor] that may be used to run the task
     *
     * @return a [Cancellable] that can be used to cancel a running task
     */
    fun executeAsync(
        callback: CompletionCallback<T>,
        executor: FiberExecutor?
    ): Cancellable {
        val protected = ProtectedCompletionCallback(callback)
        return try {
            asyncFun(protected, executor ?: FiberExecutor.shared())
        } catch (e: Throwable) {
            UncaughtExceptionHandler.rethrowIfFatal(e)
            protected.onFailure(e)
            Cancellable.EMPTY
        }
    }

    /**
     * Overload of [executeAsync] that uses [FiberExecutor.shared] as the executor.
     *
     * @param callback will be invoked with the result when the task completes
     *
     * @return a [Cancellable] that can be used to cancel a running task
     */
    fun executeAsync(callback: CompletionCallback<T>): Cancellable =
        executeAsync(callback, null)

    /**
     * Executes the task concurrently and returns a [TaskFiber] that can be
     * used to wait for the result or cancel the task.
     *
     * @param executor is the [FiberExecutor] that may be used to run the task
     */
    fun executeConcurrently(executor: FiberExecutor?): TaskFiber<T> {
        val fiber = ExecutedTaskFiber<T>()
        try {
            val token = asyncFun(fiber.onComplete, executor ?: FiberExecutor.shared())
            fiber.registerCancel(token)
        } catch (e: Throwable) {
            UncaughtExceptionHandler.rethrowIfFatal(e)
            fiber.onComplete.onFailure(e)
        }
        return fiber
    }

    /**
     * Overload of [executeConcurrently] that uses [FiberExecutor.shared]
     * as the executor.
     */
    fun executeConcurrently(): TaskFiber<T> =
        executeConcurrently(null)

    /**
     * Executes the task and blocks until it completes, or the current
     * thread gets interrupted (in which case the task is also cancelled).
     *
     * @throws ExecutionException if the task fails with an exception.
     *
     * @throws InterruptedException if the current thread is interrupted,
     * which also cancels the running task. Note that on interruption,
     * the running concurrent task must also be interrupted, as this method
     * always blocks for its interruption or completion.
     */
    @Throws(ExecutionException::class, InterruptedException::class)
    fun executeBlocking(executor: FiberExecutor?): T {
        val h = BlockingCompletionCallback<T>()
        val cancelToken =
            try {
                asyncFun(h, executor ?: FiberExecutor.shared())
            } catch (e: Exception) {
                h.onFailure(e)
                Cancellable.EMPTY
            }
        return h.await(cancelToken)
    }

    /**
     * Overload of [executeBlocking] that uses [FiberExecutor.shared]
     * as the executor.
     */
    @Throws(ExecutionException::class, InterruptedException::class)
    fun executeBlocking(): T = executeBlocking(null)

    /**
     * Executes the task and blocks until it completes, or the timeout is reached,
     * or the current thread is interrupted.
     *
     * @throws ExecutionException if the task fails with an exception.
     *
     * @throws InterruptedException if the current thread is interrupted.
     * The running task is also cancelled, and this method does not
     * return until `onCancel` is signaled.
     *
     * @throws TimeoutException if the task doesn't complete within the
     * specified timeout. The running task is also cancelled on timeout,
     * and this method does not returning until `onCancel` is signaled.
     */
    @Throws(ExecutionException::class, InterruptedException::class, TimeoutException::class)
    fun executeBlockingTimed(timeout: Duration, executor: FiberExecutor?): T {
        val h = BlockingCompletionCallback<T>()
        val cancelToken =
            try {
                asyncFun(h, executor ?: FiberExecutor.shared())
            } catch (e: Exception) {
                h.onFailure(e)
                Cancellable.EMPTY
            }
        return h.await(cancelToken, timeout)
    }

    /**
     * Overload of [executeBlockingTimed] that uses [FiberExecutor.shared]
     * as the executor.
     */
    @Throws(ExecutionException::class, InterruptedException::class, TimeoutException::class)
    fun executeBlockingTimed(timeout: Duration): T =
        executeBlockingTimed(timeout, null)

    companion object {
        /**
         * Creates a task from a builder function that, on evaluation, will have
         * same-thread execution.
         *
         * This method ensures:
         * 1. Idempotent cancellation
         * 2. Trampolined execution to avoid stack-overflows
         *
         * The created task will execute the given function on the current
         * thread, by using a "trampoline" to avoid stack overflows. This may be
         * useful if the computation for initiating the async process is
         * expected to be fast. However, if the computation can block the
         * current thread, it is recommended to use [createAsync] instead, which
         * will initiate the computation on a separate thread.
         *
         * @param run is the function that will trigger the async
         * computation, injecting a callback that will be used to signal the
         * result, and an executor that can be used for creating additional
         * threads.
         *
         * @return a new task that will execute the given builder function upon execution
         * @see createAsync
         */
        @JvmStatic
        fun <T> create(run: AsyncFun<T>): Task<T> =
            Task { callback, executor ->
                val cancel = MutableCancellable()
                Trampoline.execute {
                    try {
                        cancel.set(run.invoke(callback, executor))
                    } catch (e: Throwable) {
                        UncaughtExceptionHandler.rethrowIfFatal(e)
                        callback.onFailure(e)
                    }
                }
                cancel
            }

        /**
         * Creates a task that, when executed, will execute the given asynchronous
         * computation starting from a separate thread.
         *
         * This is a variant of [create], which executes the given
         * builder function on the same thread.
         *
         * NOTE: The thread is created via the injected [FiberExecutor] in the
         * "execute" methods (e.g., [executeAsync]). Even when using one of the overloads,
         * then [FiberExecutor.shared] is assumed.
         *
         * @param run is the function that will trigger the async computation.
         * @return a new task that will execute the given builder function.
         *
         * @see create
         * @see FiberExecutor
         */
        @JvmStatic
        fun <T> createAsync(run: AsyncFun<T>): Task<T> =
            Task { callback, executor ->
                val cancel = MutableCancellable()
                executor.execute {
                    try {
                        val token = run.invoke(callback, executor)
                        cancel.set(token)
                    } catch (e: Throwable) {
                        UncaughtExceptionHandler.rethrowIfFatal(e)
                        callback.onFailure(e)
                    }
                }
                cancel
            }

        /**
         * Creates a task from a [DelayedFun] executing blocking IO.
         */
        @JvmStatic
        fun <T> fromBlockingIO(run: DelayedFun<T>): Task<T> =
            Task { callback, executor ->
                executor.executeCancellable(
                    {
                        try {
                            val result = run.invoke()
                            callback.onSuccess(result)
                        } catch (e: InterruptedException) {
                            callback.onCancel()
                        } catch (e: CancellationException) {
                            callback.onCancel()
                        } catch (e: Throwable) {
                            UncaughtExceptionHandler.rethrowIfFatal(e)
                            callback.onFailure(e)
                        }
                    },
                    {
                        // This is a concurrent call that wouldn't be very safe
                        // with a naive implementation of the CompletionCallback
                        // (thankfully we protect the injected callback)
                        // noinspection Convert2MethodRef
                        callback.onCancel()
                    }
                )
            }

        /**
         * Creates a task from a [Future] builder.
         *
         * This is compatible with Java's interruption protocol and [Future.cancel], with the resulting task being cancellable.
         *
         * **NOTE:** Use [fromCompletionStage] for directly converting [CompletableFuture] builders, because it is not possible to cancel
         * such values, and the logic needs to reflect it. Better yet, use [fromCancellableFuture] for working with [CompletionStage]
         * values that can be cancelled.
         *
         * @param builder is the [Callable] that will create the [Future] upon this task's execution.
         * @return a new task that will complete with the result of the created `Future` upon execution
         *
         * @see fromCompletionStage
         * @see fromCancellableFuture
         */
        @JvmStatic
        fun <T> fromBlockingFuture(builder: DelayedFun<Future<out T>>): Task<T> =
            fromBlockingIO {
                builder.invoke().let { future ->
                    try {
                        future.get()
                    } catch (e: ExecutionException) {
                        throw e.cause ?: e
                    } catch (e: InterruptedException) {
                        future.cancel(true)
                        // We need to wait for this future to complete, as we need to
                        // try to back-pressure on its interruption (doesn't really work).
                        while (!future.isDone) {
                            // Ignore further interruption signals
                            Thread.interrupted()
                            try {
                                future.get()
                            } catch (ignored: Exception) {
                            }
                        }
                        throw e
                    }
                }
            }

        /**
         * Creates tasks from a builder of [CompletionStage].
         *
         * **NOTE:** `CompletionStage` isn't cancellable, and the resulting task should reflect this
         * (i.e., on cancellation, the listener should not receive an `onCancel` signal until the
         * `CompletionStage` actually completes).
         *
         * Prefer using [fromCancellableFuture] for working with [CompletionStage] values that can be cancelled.
         *
         * @param builder is the [Callable] that will create the [CompletionStage]
         * value. It's a builder because [Task] values are cold values (lazy, not executed yet).
         *
         * @return a new task that upon execution will complete with the result of
         * the created `CancellableCompletionStage`
         * @see fromCancellableFuture
         */
        @JvmStatic
        fun <T> fromCompletionStage(builder: DelayedFun<CompletionStage<out T>>): Task<T> =
            fromCancellableFuture {
                CancellableFuture(
                    builder.invoke().toCompletableFuture(),
                    Cancellable.EMPTY
                )
            }

        /**
         * Creates tasks from a builder of [CancellableFuture].
         *
         * This is the recommended way to work with [CompletionStage] builders,
         * because cancelling such values (e.g., [CompletableFuture]) doesn't
         * work for cancelling the connecting computation. As such, the user
         * should provide an explicit [Cancellable] token that can be used.
         *
         * @param run is the [Callable] that will create the [CancellableFuture]
         * value. It's a builder because [Task] values are cold values (lazy,
         * not executed yet).
         *
         * @return a new task that upon execution will complete with the result of
         * the created `CancellableCompletionStage`
         */
        @JvmStatic
        fun <T> fromCancellableFuture(run: DelayedFun<CancellableFuture<T>>): Task<T> =
            Task(TaskFromCancellableFuture(run))

        /**
         * Creates a task that will complete, successfully, with the given value.
         *
         * @param value is the value to complete the task with
         * @return a new task that will complete with the given value
         */
        @JvmStatic
        fun <T> successful(value: T): Task<T> =
            Task { listener, _ ->
                listener.onSuccess(value)
                Cancellable.EMPTY
            }

        /**
         * Creates a task that will complete with the given exception.
         *
         * @param e is the exception to complete the task with
         * @return a new task that will complete with the given exception
         */
        @JvmStatic
        fun <T> failed(e: Throwable): Task<T> =
            Task { listener, _ ->
                listener.onFailure(e)
                Cancellable.EMPTY
            }

        /**
         * Creates a task that will complete with a cancellation signal.
         *
         * @return a new task that will complete with a cancellation signal
         */
        @JvmStatic
        fun <T> cancelled(): Task<T> =
            Task { listener, _ ->
                listener.onCancel()
                Cancellable.EMPTY
            }
    }
}

private class BlockingCompletionCallback<T>: AbstractQueuedSynchronizer(), CompletionCallback<T> {
    private val isDone = AtomicBoolean(false)
    private var result: T? = null
    private var error: Throwable? = null
    private var interrupted: InterruptedException? = null

    override fun onSuccess(value: T) {
        if (!isDone.getAndSet(true)) {
            result = value
            releaseShared(1)
        }
    }

    override fun onFailure(e: Throwable) {
        if (!isDone.getAndSet(true)) {
            error = e
            releaseShared(1)
        } else {
            UncaughtExceptionHandler.logOrRethrow(e)
        }
    }

    override fun onCancel() {
        if (!isDone.getAndSet(true)) {
            interrupted = InterruptedException("Task was cancelled")
            releaseShared(1)
        }
    }

    override fun tryAcquireShared(arg: Int): Int =
        if (state != 0) 1 else -1

    override fun tryReleaseShared(arg: Int): Boolean {
        state = 1
        return true
    }

    private inline fun awaitInline(cancelToken: Cancellable, await: (Boolean) -> Unit): T {
        var isNotCancelled = true
        var timedOut: TimeoutException? = null
        while (true) {
            try {
                await(isNotCancelled)
                break
            } catch (e: TimeoutException) {
                if (isNotCancelled) {
                    isNotCancelled = false
                    timedOut = e
                    cancelToken.cancel()
                }
            } catch (e: InterruptedException) {
                if (isNotCancelled) {
                    isNotCancelled = false
                    cancelToken.cancel()
                }
            }
            // Clearing the interrupted flag may not be necessary,
            // but doesn't hurt, and we should have a cleared flag before
            // re-throwing the exception
            Thread.interrupted()
        }
        if (timedOut != null) throw timedOut
        if (interrupted != null) throw interrupted!!
        if (error != null) throw ExecutionException(error)
        return result!!
    }

    @Throws(InterruptedException::class, ExecutionException::class)
    fun await(cancelToken: Cancellable): T =
        awaitInline(cancelToken) {
            acquireSharedInterruptibly(1)
        }

    @Throws(InterruptedException::class, ExecutionException::class, TimeoutException::class)
    fun await(cancelToken: Cancellable, timeout: Duration): T =
        awaitInline(cancelToken) {
            if (!tryAcquireSharedNanos(1, timeout.toNanos()))
                throw TimeoutException("Task timed-out after $timeout")
        }
}

private class TaskFromCancellableFuture<T>(
    private val builder: DelayedFun<CancellableFuture<T>>
) : AsyncFun<T> {
    override fun invoke(callback: CompletionCallback<T>, executor: FiberExecutor): Cancellable {
        val cancel = MutableCancellable()
        executor.execute {
            var isUserError = true
            try {
                val cancellableFuture = builder.invoke()
                isUserError = false
                val future = getCompletableFuture(cancellableFuture, callback)
                val cancellable = cancellableFuture.cancellable
                cancel.set {
                    try {
                        cancellable.cancel()
                    } finally {
                        future.cancel(true)
                    }
                }
            } catch (e: Throwable) {
                UncaughtExceptionHandler.rethrowIfFatal(e)
                if (isUserError) callback.onFailure(e)
                else UncaughtExceptionHandler.logOrRethrow(e)
            }
        }
        return cancel
    }

    companion object {
        private fun <T> getCompletableFuture(
            cancellableFuture: CancellableFuture<T>,
            callback: CompletionCallback<T>
        ): CompletableFuture<out T> {
            val future = cancellableFuture.future
            future.whenComplete { value, error ->
                when (error) {
                    is InterruptedException, is CancellationException ->
                        callback.onCancel()
                    is ExecutionException ->
                        callback.onFailure(error.cause ?: error)
                    is CompletionException ->
                        callback.onFailure(error.cause ?: error)
                    else -> {
                        if (error != null) callback.onFailure(error)
                        else callback.onSuccess(value)
                    }
                }
            }
            return future
        }
    }
}
