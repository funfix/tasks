package org.funfix.tasks

import org.jetbrains.annotations.Blocking
import org.jetbrains.annotations.NonBlocking
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.AbstractQueuedSynchronizer
import java.io.Serializable
import java.util.concurrent.atomic.AtomicReference

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
public class Task<out T> private constructor(
    private val asyncFun: AsyncFun<T>
): Serializable {
    /**
     * Starts the asynchronous execution of this task.
     *
     * @param callback will be invoked with the result when the task completes
     * @param executor is the [FiberExecutor] that may be used to run the task
     *
     * @return a [Cancellable] that can be used to cancel a running task
     */
    @NonBlocking
    public fun executeAsync(
        executor: FiberExecutor,
        callback: CompletionCallback<T>
    ): Cancellable {
        val protected = ProtectedCompletionCallback(callback)
        return try {
            asyncFun(executor, protected)
        } catch (e: Throwable) {
            UncaughtExceptionHandler.rethrowIfFatal(e)
            protected.complete(Outcome.failed(e))
            Cancellable.EMPTY
        }
    }

    /**
     * Overload of [executeAsync] that uses [FiberExecutor.shared] as the executor.
     */
    @NonBlocking
    public fun executeAsync(callback: CompletionCallback<T>): Cancellable =
        executeAsync(FiberExecutor.shared(), callback)

    /**
     * Executes the task concurrently and returns a [TaskFiber] that can be
     * used to wait for the result or cancel the task.
     *
     * @param executor is the [FiberExecutor] that may be used to run the task
     */
    @NonBlocking
    public fun executeConcurrently(executor: FiberExecutor): TaskFiber<T> =
        TaskFiber.start(executor, asyncFun)

    /**
     * Overload of [executeConcurrently] that uses [FiberExecutor.shared] as the executor.
     */
    @NonBlocking
    public fun executeConcurrently(): TaskFiber<T> =
        executeConcurrently(FiberExecutor.shared())

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
    @Blocking
    @Throws(ExecutionException::class, InterruptedException::class)
    public fun executeBlocking(executor: FiberExecutor): T {
        val h = BlockingCompletionCallback<T>()
        val cancelToken =
            try {
                asyncFun(executor, h)
            } catch (e: Exception) {
                h.complete(Outcome.failed(e))
                Cancellable.EMPTY
            }
        return h.await(cancelToken)
    }

    /**
     * Overload of [executeBlocking] that uses [FiberExecutor.shared] as the executor.
     */
    @Blocking
    @Throws(ExecutionException::class, InterruptedException::class)
    public fun executeBlocking(): T = executeBlocking(FiberExecutor.shared())

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
    @Blocking
    @Throws(ExecutionException::class, InterruptedException::class, TimeoutException::class)
    public fun executeBlockingTimed(
        executor: FiberExecutor,
        timeoutMillis: Long
    ): T {
        val h = BlockingCompletionCallback<T>()
        val cancelToken =
            try {
                asyncFun(executor, h)
            } catch (e: Exception) {
                h.complete(Outcome.failed(e))
                Cancellable.EMPTY
            }
        return h.await(cancelToken, timeoutMillis)
    }

    /**
     * Overload of [executeBlockingTimed] that uses [FiberExecutor.shared] as the executor.
     */
    @Blocking
    @Throws(ExecutionException::class, InterruptedException::class, TimeoutException::class)
    public fun executeBlockingTimed(timeoutMillis: Long): T =
        executeBlockingTimed(FiberExecutor.shared(), timeoutMillis)

    public companion object {
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
        @NonBlocking
        @JvmStatic
        public fun <T> create(run: AsyncFun<T>): Task<T> =
            Task { executor, callback ->
                val cancel = MutableCancellable()
                ThreadPools.TRAMPOLINE.execute {
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
        @NonBlocking
        @JvmStatic
        public fun <T> createAsync(run: AsyncFun<T>): Task<T> =
            Task { executor, callback ->
                val cancel = MutableCancellable()
                executor.execute {
                    try {
                        val token = run.invoke(executor, callback)
                        cancel.set(token)
                    } catch (e: Throwable) {
                        UncaughtExceptionHandler.rethrowIfFatal(e)
                        callback.complete(Outcome.failed(e))
                    }
                }
                cancel
            }

        /**
         * Creates a task from a [DelayedFun] executing blocking IO.
         */
        @NonBlocking
        @JvmStatic
        public fun <T> fromBlockingIO(run: DelayedFun<T>): Task<T> =
            Task { executor, callback ->
                executor.executeCancellable(
                    {
                        try {
                            // This warning shouldn't happen (?!)
                            @Suppress("BlockingMethodInNonBlockingContext")
                            val result = run.invoke()
                            callback.complete(Outcome.succeeded(result))
                        } catch (_: InterruptedException) {
                            callback.complete(Outcome.cancelled())
                        } catch (_: TaskCancellationException) {
                            callback.complete(Outcome.cancelled())
                        } catch (e: Throwable) {
                            UncaughtExceptionHandler.rethrowIfFatal(e)
                            callback.complete(Outcome.failed(e))
                        }
                    },
                    {
                        // This is a concurrent call that wouldn't be very safe
                        // with a naive implementation of the CompletionCallback
                        // (thankfully we protect the injected callback)
                        callback.complete(Outcome.cancelled())
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
        public fun <T> fromBlockingFuture(builder: DelayedFun<Future<out T>>): Task<T> =
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
        public fun <T> fromCompletionStage(builder: DelayedFun<CompletionStage<out T>>): Task<T> =
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
        public fun <T> fromCancellableFuture(run: DelayedFun<CancellableFuture<T>>): Task<T> =
            Task(TaskFromCancellableFuture(run))

        /**
         * Creates a task that will complete, successfully, with the given value.
         *
         * @param value is the value to complete the task with
         * @return a new task that will complete with the given value
         */
        @JvmStatic
        public fun <T> successful(value: T): Task<T> {
            val outcome = Outcome.succeeded(value)
            return Task { _, cb ->
                cb.complete(outcome)
                Cancellable.EMPTY
            }
        }

        /**
         * Creates a task that will complete with the given exception.
         *
         * @param e is the exception to complete the task with
         * @return a new task that will complete with the given exception
         */
        @JvmStatic
        public fun <T> failed(e: Throwable): Task<T> {
            val outcome = Outcome.failed<T>(e)
            return Task { _, cb ->
                cb.complete(outcome)
                Cancellable.EMPTY
            }
        }

        /**
         * Creates a task that will complete with a cancellation signal.
         *
         * @return a new task that will complete with a cancellation signal
         */
        @JvmStatic
        public fun <T> cancelled(): Task<T> =
            Task { _, cb ->
                cb.complete(Outcome.cancelled())
                Cancellable.EMPTY
            }
    }
}

private class BlockingCompletionCallback<T>: AbstractQueuedSynchronizer(), CompletionCallback<T> {
    private val isDone = AtomicBoolean(false)
    private var outcome: Outcome<T>? = null

    override fun complete(outcome: Outcome<T>) {
        if (!isDone.getAndSet(true)) {
            this.outcome = outcome
            releaseShared(1)
        } else if (outcome is Outcome.Failed) {
            UncaughtExceptionHandler.logOrRethrow(outcome.exception)
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
            } catch (e: Exception) {
                when (e) {
                    is InterruptedException, is TimeoutException ->
                        if (isNotCancelled) {
                            isNotCancelled = false
                            timedOut = if (e is TimeoutException) e else null
                            cancelToken.cancel()
                        }
                    else ->
                        throw e
                }
            }
            // Clearing the interrupted flag may not be necessary,
            // but doesn't hurt, and we should have a cleared flag before
            // re-throwing the exception
            Thread.interrupted()
        }
        if (timedOut != null) throw timedOut
        when  (outcome) {
            is Outcome.Cancelled -> throw InterruptedException()
            else -> return outcome!!.getOrThrow()
        }
    }

    @Throws(InterruptedException::class, ExecutionException::class)
    fun await(cancelToken: Cancellable): T =
        awaitInline(cancelToken) {
            acquireSharedInterruptibly(1)
        }

    @Throws(InterruptedException::class, ExecutionException::class, TimeoutException::class)
    fun await(cancelToken: Cancellable, timeoutMillis: Long): T =
        awaitInline(cancelToken) {
            if (!tryAcquireSharedNanos(1, TimeUnit.MILLISECONDS.toNanos(timeoutMillis)))
                throw TimeoutException("Task timed-out after $timeoutMillis millis")
        }
}

private class TaskFromCancellableFuture<T>(
    private val builder: DelayedFun<CancellableFuture<T>>
) : AsyncFun<T> {
    override fun invoke(
        executor: FiberExecutor,
        callback: CompletionCallback<T>
    ): Cancellable {
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
                if (isUserError) callback.complete(Outcome.failed(e))
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
                    is InterruptedException, is TaskCancellationException ->
                        callback.complete(Outcome.cancelled())
                    is ExecutionException ->
                        callback.complete(Outcome.failed(error.cause ?: error))
                    is CompletionException ->
                        callback.complete(Outcome.failed(error.cause ?: error))
                    else -> {
                        if (error != null) callback.complete(Outcome.failed(error))
                        else callback.complete(Outcome.succeeded(value))
                    }
                }
            }
            return future
        }
    }
}

private class ProtectedCompletionCallback<T> private constructor(
    private var listener: CompletionCallback<T>?
) : CompletionCallback<T>, Runnable {
    private val isWaiting: AtomicBoolean = AtomicBoolean(true)
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
        if (isWaiting.getAndSet(false)) {
            this.outcome = outcome
            // Trampolined execution is needed because, with chained tasks,
            // we might end up with a stack overflow
            ThreadPools.TRAMPOLINE.execute(this)
        } else if (outcome is Outcome.Failed) {
            UncaughtExceptionHandler.logOrRethrow(outcome.exception)
        }
    }

    companion object {
        @JvmStatic
        operator fun <T> invoke(listener: CompletionCallback<T>): CompletionCallback<T> =
            when {
                listener is ProtectedCompletionCallback<*> -> listener
                else -> ProtectedCompletionCallback(listener)
            }
    }
}

private class MutableCancellable : Cancellable {
    private val ref = AtomicReference<State>(State.Active(Cancellable.EMPTY, 0))

    override fun cancel() {
        (ref.getAndSet(State.Cancelled) as? State.Active)?.token?.cancel()
    }

    fun set(token: Cancellable) {
        while (true) {
            when (val current = ref.get()) {
                is State.Active -> {
                    val update = State.Active(token, current.order + 1)
                    if (ref.compareAndSet(current, update)) return
                }
                is State.Cancelled -> {
                    token.cancel()
                    return
                }
            }
        }
    }

    fun setOrdered(token: Cancellable, order: Int) {
        while (true) {
            when (val current = ref.get()) {
                is State.Active -> {
                    if (current.order < order) {
                        val update = State.Active(token, order)
                        if (ref.compareAndSet(current, update)) return
                    } else {
                        return
                    }
                }
                is State.Cancelled -> {
                    token.cancel()
                    return
                }
            }
        }
    }

    private sealed interface State {
        data class Active(val token: Cancellable, val order: Int) : State
        data object Cancelled : State
    }
}
