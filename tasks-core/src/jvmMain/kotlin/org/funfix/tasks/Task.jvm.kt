@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

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
public actual abstract class Task<out T>: Serializable {
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
            protected.onFailure(e)
            Cancellable.EMPTY
        }
    }

    /**
     * Overload of [executeAsync] that uses [TaskExecutors.global] as the executor.
     */
    @NonBlocking
    public actual fun executeAsync(callback: CompletionCallback<T>): Cancellable =
        executeAsync(TaskExecutors.global, callback)

    /**
     * Executes the task concurrently and returns a [Fiber] that can be
     * used to wait for the result or cancel the task.
     *
     * @param executor is the [Executor] that may be used to run the task
     */
    @NonBlocking
    public actual fun executeFiber(executor: Executor): Fiber<T> =
        Fiber.start(executor, unsafeExecuteAsync)

    /**
     * Overload of [executeFiber] that uses [TaskExecutors.global] as the executor.
     */
    @NonBlocking
    public actual fun executeFiber(): Fiber<T> =
        executeFiber(TaskExecutors.global)

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
    public fun executeBlocking(executor: Executor): T {
        val h = BlockingCompletionCallback<T>()
        val cancelToken =
            try {
                unsafeExecuteAsync(executor, h)
            } catch (e: Exception) {
                h.onFailure(e)
                Cancellable.EMPTY
            }
        return h.await(cancelToken)
    }

    /**
     * Overload of [executeBlocking] that uses [TaskExecutors.global] as the executor.
     */
    @Blocking
    @Throws(ExecutionException::class, InterruptedException::class)
    public fun executeBlocking(): T = executeBlocking(TaskExecutors.global)

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
        executor: Executor,
        timeoutMillis: Long
    ): T {
        val h = BlockingCompletionCallback<T>()
        val cancelToken =
            try {
                unsafeExecuteAsync(executor, h)
            } catch (e: Exception) {
                h.onFailure(e)
                Cancellable.EMPTY
            }
        return h.await(cancelToken, timeoutMillis)
    }

    /**
     * Overload of [executeBlockingTimed] that uses [TaskExecutors.global] as the executor.
     */
    @Blocking
    @Throws(ExecutionException::class, InterruptedException::class, TimeoutException::class)
    public fun executeBlockingTimed(timeoutMillis: Long): T =
        executeBlockingTimed(TaskExecutors.global, timeoutMillis)

    public actual companion object {
        private operator fun <T> invoke(asyncFun: AsyncFun<T>): Task<T> =
            object : Task<T>() {
                override val unsafeExecuteAsync: AsyncFun<T> = asyncFun
            }

        @NonBlocking
        @JvmStatic
        public actual fun <T> create(run: AsyncFun<T>): Task<T> =
            Task { executor, callback ->
                val cancel = MutableCancellable()
                TaskExecutors.trampoline.execute {
                    try {
                        cancel.set(run.invoke(executor, callback))
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
         * NOTE: The thread is created via the injected [Executor] in the
         * "execute" methods (e.g., [executeAsync]). Even when using one of the overloads,
         * then [TaskExecutors.global] is assumed.
         *
         * @param run is the function that will trigger the async computation.
         * @return a new task that will execute the given builder function.
         *
         * @see create
         * @see Executor
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
                        callback.onFailure(e)
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
                val cancelToken = MutableCancellable()
                executor.execute {
                    val th = Thread.currentThread()
                    cancelToken.set {
                        th.interrupt()
                    }
                    try {
                        val result = try {
                            run.invoke()
                        } finally {
                            cancelToken.set(Cancellable.EMPTY)
                        }
                        callback.onSuccess(result)
                    } catch (_: InterruptedException) {
                        callback.onCancellation()
                    } catch (_: TaskCancellationException) {
                        callback.onCancellation()
                    } catch (e: Throwable) {
                        UncaughtExceptionHandler.rethrowIfFatal(e)
                        callback.onFailure(e)
                    }
                }
                cancelToken
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
         * @param run is the [DelayedFun] that will create the [CancellableFuture]
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
            return Task { _, cb ->
                cb.onSuccess(value)
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
            return Task { _, cb ->
                cb.onFailure(e)
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
                cb.onCancellation()
                Cancellable.EMPTY
            }
    }
}

private class BlockingCompletionCallback<T>: AbstractQueuedSynchronizer(),
    CompletionCallback<T> {
    private val isDone = AtomicBoolean(false)
    
    private var outcomeRef: Outcome<T>? = null
    private var successful: T? = null
    private var failure: Throwable? = null
    private var isCancelled = false
    
    private inline fun completeWith(crossinline block: () -> Unit): Boolean {
        if (!isDone.getAndSet(true)) {
            block()
            releaseShared(1)
            return true
        } else {
            return false
        }
    }

    override fun onSuccess(value: T) {
        completeWith { successful = value }
    }

    override fun onFailure(e: Throwable) {
        UncaughtExceptionHandler.rethrowIfFatal(e)
        if (!completeWith { failure = e }) {
            UncaughtExceptionHandler.logOrRethrow(e)
        }
    }

    override fun onCancellation() {
        completeWith { isCancelled = true }
    }

    override fun onOutcome(outcome: Outcome<T>) {
        if (!completeWith { this.outcomeRef = outcome } && outcome is Outcome.Failure) {
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
        when  (val outcome = outcomeRef) {
            null -> {
                if (isCancelled) throw InterruptedException()
                if (failure != null) throw ExecutionException(failure)
                @Suppress("UNCHECKED_CAST")
                return successful as T
            }
            is Outcome.Cancellation ->
                throw InterruptedException()
            else -> 
                return outcome.getOrThrow()
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
        executor: Executor,
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
                    is InterruptedException, is TaskCancellationException ->
                        callback.onCancellation()
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

private class ProtectedCompletionCallback<T> private constructor(
    private var listener: CompletionCallback<T>?
) : CompletionCallback<T>, Runnable {
    private val isWaiting: AtomicBoolean = AtomicBoolean(true)

    private var outcome: Outcome<T>? = null
    private var successful: T? = null
    private var failure: Throwable? = null
    private var isCancelled = false

    override fun run() {
        val listener = this.listener
        if (listener != null) {
            @Suppress("UNCHECKED_CAST")
            when {
                outcome != null -> listener.onOutcome(outcome!!)
                isCancelled -> listener.onCancellation()
                failure != null -> listener.onFailure(failure!!)
                else -> listener.onSuccess(successful as T)
            }
            // GC purposes
            this.outcome = null
            this.successful = null
            this.failure = null
            this.listener = null
        }
    }

    private inline fun completeWith(crossinline block: () -> Unit): Boolean {
        if (isWaiting.getAndSet(false)) {
            block()
            TaskExecutors.trampoline.execute(this)
            return true
        }
        return false
    }

    override fun onSuccess(value: T) {
        completeWith { this.successful = value }
    }

    override fun onFailure(e: Throwable) {
        if (!completeWith { this.failure = e }) {
            UncaughtExceptionHandler.logOrRethrow(e)
        }
    }

    override fun onCancellation() {
        completeWith { this.isCancelled = true }
    }

    override fun onOutcome(outcome: Outcome<T>) {
        if (!completeWith { this.outcome = outcome }) {
            if (outcome is Outcome.Failure)
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
    /*
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
    }*/

    private sealed interface State {
        data class Active(val token: Cancellable, val order: Int) : State
        data object Cancelled : State
    }
}
