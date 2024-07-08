package org.funfix.tasks

import java.time.Duration
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.AbstractQueuedSynchronizer
import java.io.Serializable
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType

/**
 * Represents a suspended computation that can be executed asynchronously.
 *
 * To create a task, use one of its builders:
 * - [create]
 * - [createAsync]
 * - [fromBlockingIO]
 * - [fromBlockingFuture]
 * - [fromCancellableCompletionStage]
 */
class Task<out T> private constructor(
    private val asyncFun: AsyncFun<T>
): Serializable {
    /**
     * Executes the task asynchronously.
     *
     * @param callback will be invoked with the result when the task completes
     * @return a [Cancellable] that can be used to cancel a running task
     */
    fun executeAsync(callback: CompletionCallback<T>): Cancellable {
        val protected = CompletionCallback.protect(callback)
        return try {
            asyncFun(callback)
        } catch (e: Exception) {
            protected.onFailure(e)
            Cancellable.EMPTY
        }
    }

    /**
     * Executes the task concurrently and returns a [Fiber] that can be
     * used to wait for the result or cancel the task.
     */
    fun executeConcurrently(): Fiber<T> {
        val fiber = TaskFiber<T>()
        val token = executeAsync(fiber.onComplete)
        fiber.registerCancel(token)
        return fiber
    }

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
    fun executeBlocking(): T {
        val h = BlockingCompletionCallback<T>()
        val cancelToken = executeAsync(h)
        return h.await(cancelToken)
    }

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
    fun executeBlockingTimed(timeout: Duration): T {
        val h = BlockingCompletionCallback<T>()
        val cancelToken = executeAsync(h)
        return h.await(cancelToken, timeout)
    }

    companion object {
        /**
         * Creates a task from a builder function that, on evaluation, will have
         * same-thread execution.
         *
         * Compared with creating a new task instance directly, this method ensures:
         * 1. Safe/idempotent use of the listener
         * 2. Idempotent cancellation
         * 3. Trampolined execution to avoid stack-overflows
         *
         * The created task will execute the given function on the current thread, by
         * using a "trampoline" to avoid stack overflows. This may be useful if the
         * computation for initiating the async process is expected to be fast. However,
         * if the computation can block the current thread, it is recommended to use
         * [createAsync] instead, which will initiate the computation
         * on a separate thread.
         *
         * @param builder is the function that will trigger the async computation,
         * receiving a callback that will be used to signal the result.
         *
         * @return a new task that will execute the given builder function upon execution
         */
        @JvmStatic
        fun <T> create(builder: AsyncFun<T>): Task<T> =
            Task(TaskCreate(builder, null, null))

        /**
         * Creates a task that, when executed, will execute the given asynchronous
         * computation starting from a separate thread.
         *
         * This is a variant of [create], which executes the given
         * builder function on the same thread.
         *
         * @param es is the [Executor] to use for starting the async computation
         * @param builder is the function that will trigger the async computation
         * @return a new task that will execute the given builder function
         */
        @JvmStatic
        fun <T> createAsync(es: Executor, builder: AsyncFun<T>): Task<T> =
            Task(TaskCreate(builder, es, null))

        /**
         * Creates a task that, when executed, will execute the given asynchronous
         * computation starting from a separate thread.
         *
         * This is a simplified version of [createAsync] that uses [ThreadPools.sharedIO] as the common thread-pool. This
         * will use virtual threads on Java 21+.
         *
         * @param builder is the function that will trigger the async computation
         * @return a new task that will execute the given builder function
         */
        @JvmStatic
        fun <T> createAsync(builder: AsyncFun<T>): Task<T> =
            if (VirtualThreads.areVirtualThreadsSupported())
                try {
                    createAsync(VirtualThreads.factory(), builder)
                } catch (ignored: VirtualThreads.NotSupportedException) {
                    createAsync(ThreadPools.sharedIO(), builder)
                }
            else
                createAsync(ThreadPools.sharedIO(), builder)

        /**
         * Creates a task that, when executed, will execute the given asynchronous
         * computation starting from a separate thread.
         *
         * This is a variant of [createAsync] that will
         * use the given [ThreadFactory] for creating the thread, instead of
         * using an [Executor]. The advantage of this method may be performance,
         * in case virtual threads are supported, since there may be less synchronization
         * overhead.
         *
         * @param factory is the [ThreadFactory] to use for creating the thread
         * @param builder is the function that will trigger the async computation
         * @return a new task that will execute the given builder function on a separate thread
         */
        @JvmStatic
        fun <T> createAsync(factory: ThreadFactory, builder: AsyncFun<T>): Task<T> =
            Task(TaskCreate(builder, null, factory))

        /**
         * Creates a task from a [DelayedFun] executing blocking IO.
         *
         * Similar to [fromBlockingIO], but uses
         * the common thread-pool defined by [ThreadPools.sharedIO],
         * which is using virtual threads on Java 21+.
         *
         * @param delayed is the blocking IO operation to execute
         * @return a new task that will perform the blocking IO operation upon execution
         */
        @JvmStatic
        fun <T> fromBlockingIO(delayed: DelayedFun<T>): Task<T> =
            if (VirtualThreads.areVirtualThreadsSupported())
                try {
                    fromBlockingIO(VirtualThreads.factory(), delayed)
                } catch (ignored: VirtualThreads.NotSupportedException) {
                    fromBlockingIO(ThreadPools.sharedIO(), delayed)
                }
            else
                fromBlockingIO(ThreadPools.sharedIO(), delayed)

        /**
         * Creates a task from a [DelayedFun] executing blocking IO
         * asynchronously (on another thread).
         *
         * @param es is the [Executor] to use for executing the blocking IO operation
         * @param delayed is the blocking IO operation to execute
         * @return a new task that will perform the blocking IO operation upon execution
         */
        @JvmStatic
        fun <T> fromBlockingIO(es: Executor, delayed: DelayedFun<T>): Task<T> =
            Task(TaskFromExecutor(es, delayed))

        /**
         * Creates a task from a [DelayedFun] executing blocking IO, using a
         * different thread created by the given [ThreadFactory].
         *
         * @param factory is the [ThreadFactory] to use for creating the thread
         * @param delayed is the blocking IO operation to execute
         * @return a new task that will perform the blocking IO operation upon execution
         */
        @JvmStatic
        fun <T> fromBlockingIO(factory: ThreadFactory, delayed: DelayedFun<T>): Task<T> =
            Task(TaskFromThreadFactory(factory, delayed))

        /**
         * Creates a task from a [Future] builder.
         *
         * This is similar to [fromBlockingFuture], but uses
         * the common thread-pool defined by [ThreadPools.sharedIO], which is
         * using virtual threads on Java 21+.
         *
         * @param builder is the [Callable] that will create the [Future] upon
         * this task's execution.
         * @return a new task that will complete with the result of the created `Future`
         * upon execution
         */
        @JvmStatic
        fun <T> fromBlockingFuture(builder: Callable<Future<out T>>): Task<T> =
            fromBlockingFuture(ThreadPools.sharedIO(), builder)

        /**
         * Creates a task from a [Future] builder.
         *
         * This is compatible with Java's interruption protocol and
         * [Future.cancel], with the resulting task being cancellable.
         *
         * @param builder is the [Callable] that will create the [Future] upon
         * this task's execution.
         * @return a new task that will complete with the result of the created `Future`
         * upon execution
         */
        @JvmStatic
        fun <T> fromBlockingFuture(es: Executor, builder: Callable<Future<out T>>): Task<T> =
            fromBlockingIO(es) {
                val f = builder.call()
                try {
                    f.get()
                } catch (e: ExecutionException) {
                    throw e.cause ?: e
                } catch (e: InterruptedException) {
                    f.cancel(true)
                    while (!f.isDone) {
                        Thread.interrupted()
                        try {
                            f.get()
                        } catch (ignored: Exception) {
                        }
                    }
                    throw e
                }
            }

        /**
         * Creates tasks from a builder of [CompletionStage].
         *
         * Prefer using [fromCancellableCompletionStage] for working with
         * [CompletionStage] values that can be cancelled.
         *
         * @param builder is the [Callable] that will create the [CompletionStage]
         * value. It's a builder because [Task] values are cold values
         * (lazy, not executed yet).
         * @return a new task that upon execution will complete with the result of
         * the created `CancellableCompletionStage`
         */
        @JvmStatic
        fun <T> fromCompletionStage(builder: Callable<CompletionStage<out T>>): Task<T> =
            fromCancellableCompletionStage {
                CancellableCompletionStage(builder.call(), Cancellable.EMPTY)
            }

        /**
         * Creates tasks from a builder of [CancellableCompletionStage].
         *
         * This is the recommended way to work with [CompletionStage] builders,
         * because cancelling such values (e.g., [CompletableFuture]) doesn't work
         * for cancelling the connecting computation. As such, the user should provide
         * an explicit [Cancellable] token that can be used.
         *
         * @param builder is the [Callable] that will create the [CancellableCompletionStage]
         * value. It's a builder because [Task] values are cold values
         * (lazy, not executed yet).
         *
         * @return a new task that upon execution will complete with the result of
         * the created `CancellableCompletionStage`
         */
        @JvmStatic
        fun <T> fromCancellableCompletionStage(builder: Callable<CancellableCompletionStage<T>>): Task<T> =
            Task(TaskFromCompletionStage(builder))

        /**
         * Creates a task that will complete, successfully, with the given value.
         *
         * @param value is the value to complete the task with
         * @return a new task that will complete with the given value
         */
        @JvmStatic
        fun <T> successful(value: T): Task<T> =
            Task { listener ->
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
            Task { listener ->
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
            Task { listener ->
                listener.onCancel()
                Cancellable.EMPTY
            }
    }
}

/**
 * Provides utilities for working with [Executor] instances, optimized
 * for common use-cases.
 */
object ThreadPools {
    @Volatile
    private var sharedVirtualIORef: Executor? = null

    @Volatile
    private var sharedPlatformIORef: Executor? = null

    /**
     * Returns a shared [Executor] meant for blocking I/O tasks.
     * The reference gets lazily initialized on the first call.
     *
     * Uses [unlimitedThreadPoolForIO] to create the executor,
     * which will use virtual threads on Java 21+, or a plain
     * [Executors.newCachedThreadPool] on older JVM versions.
     */
    @JvmStatic
    fun sharedIO(): Executor =
        if (VirtualThreads.areVirtualThreadsSupported())
            sharedVirtualIO()
        else
            sharedPlatformIO()

    private fun sharedPlatformIO(): Executor {
        // Using double-checked locking to avoid synchronization
        if (sharedPlatformIORef == null) {
            synchronized(ThreadPools::class.java) {
                if (sharedPlatformIORef == null) {
                    sharedPlatformIORef = unlimitedThreadPoolForIO("common-io")
                }
            }
        }
        return sharedPlatformIORef!!
    }

    private fun sharedVirtualIO(): Executor {
        // Using double-checked locking to avoid synchronization
        if (sharedVirtualIORef == null) {
            synchronized(ThreadPools::class.java) {
                if (sharedVirtualIORef == null) {
                    sharedVirtualIORef = unlimitedThreadPoolForIO("common-io")
                }
            }
        }
        return sharedVirtualIORef!!
    }

    /**
     * Creates an [Executor] meant for blocking I/O tasks, with an
     * unlimited number of threads.
     *
     * On Java 21 and above, the created [Executor] will run tasks on virtual threads.
     * On older JVM versions, it returns a plain [Executors.newCachedThreadPool].
     */
    @JvmStatic
    fun unlimitedThreadPoolForIO(prefix: String): ExecutorService =
        if (VirtualThreads.areVirtualThreadsSupported())
            try {
                VirtualThreads.executorService("$prefix-virtual-")
            } catch (ignored: VirtualThreads.NotSupportedException) {
                Executors.newCachedThreadPool { r ->
                    Thread(r).apply {
                        name = "$prefix-platform-${threadId()}"
                    }
                }
            }
        else
            Executors.newCachedThreadPool { r ->
                Thread(r).apply {
                    name = "$prefix-platform-${threadId()}"
                }
            }
}

private class TaskCreate<out T>(
    private val builder: AsyncFun<T>,
    es: Executor?,
    factory: ThreadFactory?
) : AsyncFun<T> {
    private val executor: Executor = when {
        es != null -> es
        factory != null -> Executor { command -> factory.newThread(command).start() }
        else -> Trampoline.EXECUTOR
    }

    private class CreateCancellable : Cancellable {
        private val ref = AtomicReference(Cancellable.EMPTY)

        fun set(token: Cancellable) {
            if (!ref.compareAndSet(Cancellable.EMPTY, token)) {
                Trampoline.execute { token.cancel() }
            }
        }

        override fun cancel() {
            ref.getAndSet(null)?.cancel()
        }
    }

    override fun invoke(callback: CompletionCallback<T>): Cancellable {
        val cancel = CreateCancellable()
        val run = Runnable {
            val token = builder(callback)
            cancel.set(token)
        }
        executor.execute(run)
        return cancel
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

private class AwaitSignal : AbstractQueuedSynchronizer() {
    override fun tryAcquireShared(arg: Int): Int {
        return if (state != 0) 1 else -1
    }

    override fun tryReleaseShared(arg: Int): Boolean {
        state = 1
        return true
    }

    fun signal() {
        releaseShared(1)
    }

    @Throws(InterruptedException::class)
    fun await() {
        acquireSharedInterruptibly(1)
    }

    @Throws(InterruptedException::class, TimeoutException::class)
    fun await(timeout: Duration) {
        if (!tryAcquireSharedNanos(1, timeout.toNanos())) {
            throw TimeoutException("Timed out after $timeout")
        }
    }
}

private class TaskFromExecutor<T>(
    private val es: Executor,
    private val delayed: DelayedFun<T>
): AsyncFun<T> {
    private sealed interface State
    private data object NotStarted: State
    private data object Completed: State
    private data class Running(val thread: Thread): State
    private data class Interrupting(val wasInterrupted: AwaitSignal): State

    private fun triggerCancel(
        state: AtomicReference<State>,
    ) {
        while (true) {
            when (val current = state.get()) {
                is NotStarted -> {
                    if (state.compareAndSet(current, Completed)) {
                        return
                    }
                }
                is Running -> {
                    // Starts the interruption process
                    val wasInterrupted = AwaitSignal()
                    if (state.compareAndSet(current, Interrupting(wasInterrupted))) {
                        current.thread.interrupt()
                        wasInterrupted.signal()
                        return
                    }
                }
                is Completed -> {
                    return
                }
                is Interrupting -> {
                    return
                }
            }
        }
    }

    override fun invoke(callback: CompletionCallback<T>): Cancellable {
        val state = AtomicReference<State>(NotStarted)
        es.execute {
            if (!state.compareAndSet(NotStarted, Running(Thread.currentThread()))) {
                callback.onCancel()
                return@execute
            }
            var result: T? = null
            var error: Throwable? = null
            var interrupted = false
            try {
                result = delayed()
            } catch (_: InterruptedException) {
                interrupted = true
            } catch (e: Exception) {
                error = e
            }

            while (true) {
                when (val current = state.get()) {
                    is Running -> {
                        if (state.compareAndSet(current, Completed)) {
                            if (interrupted)
                                callback.onCancel()
                            else if (error != null)
                                callback.onFailure(error)
                            else
                                @Suppress("UNCHECKED_CAST")
                                callback.onSuccess(result as T)
                            return@execute
                        }
                    }
                    is Interrupting -> {
                        try {
                            current.wasInterrupted.await()
                        } catch (_: InterruptedException) {}
                        callback.onCancel()
                        return@execute
                    }
                    is Completed -> {
                        throw IllegalStateException("Task:Completed")
                    }
                    is NotStarted -> {
                        throw IllegalStateException("Task:NotStarted")
                    }
                }
            }
        }
        return Cancellable {
            triggerCancel(state)
        }
    }
}

private class TaskFromThreadFactory<out T>(
    private val factory: ThreadFactory,
    private val delayed: DelayedFun<T>
) : AsyncFun<T> {

    override fun invoke(callback: CompletionCallback<T>): Cancellable {
        val thread = factory.newThread(TaskRunnable(callback))
        thread.start()
        return Cancellable { thread.interrupt() }
    }

    private inner class TaskRunnable(private val listener: CompletionCallback<T>) : Runnable {
        override fun run() {
            var isUserError = true
            try {
                val result = delayed()
                isUserError = false
                listener.onSuccess(result)
            } catch (e: InterruptedException) {
                listener.onCancel()
            } catch (e: CancellationException) {
                listener.onCancel()
            } catch (e: Exception) {
                if (isUserError) listener.onFailure(e)
                else UncaughtExceptionHandler.logException(e)
            }
        }
    }
}

private class TaskFromCompletionStage<out T>(
    private val builder: Callable<CancellableCompletionStage<T>>
) : AsyncFun<T> {

    override fun invoke(callback: CompletionCallback<T>): Cancellable {
        var userError = true
        try {
            val future = builder.call()
            userError = false

            future.completionStage.whenComplete { value, error ->
                when (error) {
                    null ->
                        callback.onSuccess(value)
                    is InterruptedException, is CancellationException ->
                        callback.onCancel()
                    is ExecutionException ->
                        callback.onFailure( error.cause ?: error)
                    is CompletionException ->
                        callback.onFailure( error.cause ?: error)
                    else ->
                        callback.onFailure(error)
                }
            }
            return future.cancellable
        } catch (e: Exception) {
            if (userError) {
                callback.onFailure(e)
                return Cancellable.EMPTY
            }
            throw RuntimeException(e)
        }
    }
}

private class TaskFiber<T> : Fiber<T> {
    private sealed interface State<out T> {
        data class Active<out T>(val listeners: List<Runnable>, val token: Cancellable?) : State<T>
        data class Cancelled(val listeners: List<Runnable>) : State<Nothing>
        data class Completed<out T>(val outcome: Outcome<T>) : State<T>

        fun triggerListeners() {
            when (this) {
                is Active -> listeners.forEach(Trampoline::execute)
                is Cancelled -> listeners.forEach(Trampoline::execute)
                else -> {}
            }
        }

        fun addListener(listener: Runnable): State<T> = when (this) {
            is Active -> copy(listeners = listeners + listener)
            is Cancelled -> copy(listeners = listeners + listener)
            else -> this
        }

        fun removeListener(listener: Runnable): State<T> = when (this) {
            is Active -> copy(listeners = listeners.filter { it != listener })
            is Cancelled -> copy(listeners = listeners.filter { it != listener })
            else -> this
        }

        companion object {
            fun <T> start(): State<T> = Active(emptyList(), null)
        }
    }

    private val ref = AtomicReference<State<T>>(State.start())

    val onComplete: CompletionCallback<T> = object : CompletionCallback<T> {
        override fun onSuccess(value: T) =
            signalComplete(Outcome.succeeded(value))
        override fun onFailure(e: Throwable) =
            signalComplete(Outcome.failed(e))
        override fun onCancel() =
            signalComplete(Outcome.cancelled())
    }

    fun registerCancel(token: Cancellable) {
        while (true) {
            when (val current = ref.get()) {
                is State.Active -> {
                    if (current.token != null) {
                        throw IllegalStateException("Token already registered")
                    }
                    val update = current.copy(token = token)
                    if (ref.compareAndSet(current, update)) {
                        return
                    }
                }
                is State.Cancelled -> {
                    token.cancel()
                    return
                }
                is State.Completed -> return
            }
        }
    }

    override fun cancel() {
        while (true) {
            when (val current = ref.get()) {
                is State.Active -> {
                    val update = State.Cancelled(current.listeners)
                    if (ref.compareAndSet(current, update)) {
                        current.token?.cancel()
                        return
                    }
                }
                is State.Cancelled -> return
                is State.Completed -> return
            }
        }
    }

    override fun outcome(): Outcome<T>? =
        when (val current = ref.get()) {
            is State.Completed -> current.outcome
            is State.Active, is State.Cancelled -> null
        }

    override fun joinBlocking() {
        tryJoinBlockingTimed(null)
    }

    override fun tryJoinBlockingTimed(timeout: Duration?): Boolean {
        val latch = AwaitSignal()
        val runnable = Runnable { latch.signal() }
        val token = joinAsync(runnable)
        return try {
            if (timeout == null) latch.await() else latch.await(timeout)
            true
        } catch (e: InterruptedException) {
            token.cancel()
            throw e
        } catch (e: TimeoutException) {
            false
        }
    }

    override fun joinAsync(onComplete: Runnable): Cancellable {
        while (true) {
            when (val current = ref.get()) {
                is State.Active, is State.Cancelled -> {
                    val update = current.addListener(onComplete)
                    if (ref.compareAndSet(current, update)) {
                        return cancelJoinAsync(onComplete)
                    }
                }
                is State.Completed -> {
                    Trampoline.execute(onComplete)
                    return Cancellable.EMPTY
                }
            }
        }
    }

    private fun cancelJoinAsync(onComplete: Runnable): Cancellable =
        Cancellable {
            while (true) {
                val current = ref.get()
                val update = current.removeListener(onComplete)
                if (ref.compareAndSet(current, update)) {
                    return@Cancellable
                }
            }
        }

    private fun signalComplete(outcome: Outcome<T>) {
        while (true) {
            when (val current = ref.get()) {
                is State.Active, is State.Cancelled -> {
                    val update = State.Completed(outcome)
                    if (ref.compareAndSet(current, update)) {
                        current.triggerListeners()
                        return
                    }
                }
                is State.Completed -> {
                    if (outcome is Outcome.Failed) {
                        UncaughtExceptionHandler.logException(outcome.exception)
                    }
                    return
                }
            }
        }
    }
}

/**
 * Internal utilities — not exposed yet, because lacking Loom support is only
 * temporary.
 */
private object VirtualThreads {
    private val newThreadPerTaskExecutorMethodHandle: MethodHandle?

    class NotSupportedException(feature: String) : Exception("$feature is not supported on this JVM")

    init {
        var tempHandle: MethodHandle?
        try {
            val executorsClass = Class.forName("java.util.concurrent.Executors")
            val lookup = MethodHandles.lookup()
            tempHandle = lookup.findStatic(
                executorsClass,
                "newThreadPerTaskExecutor",
                MethodType.methodType(ExecutorService::class.java, ThreadFactory::class.java)
            )
        } catch (e: Throwable) {
            tempHandle = null
        }
        newThreadPerTaskExecutorMethodHandle = tempHandle
    }

    /**
     * Create a virtual thread executor, returns `null` if failed.
     *
     * This function can only return a non-`null` value if running on Java 21 or later,
     * as it uses reflection to access the `Executors.newVirtualThreadPerTaskExecutor`.
     *
     * @throws NotSupportedException if the current JVM does not support virtual threads.
     */
    @JvmStatic
    @Throws(NotSupportedException::class)
    fun executorService(prefix: String): ExecutorService {
        if (!areVirtualThreadsSupported()) {
            throw NotSupportedException("Executors.newThreadPerTaskExecutor")
        }
        var thrown: Throwable? = null
        try {
            val factory = factory(prefix)
            if (newThreadPerTaskExecutorMethodHandle != null) {
                return newThreadPerTaskExecutorMethodHandle.invoke(factory) as ExecutorService
            }
        } catch (e: NotSupportedException) {
            throw e
        } catch (e: Throwable) {
            thrown = e
        }
        val e2 = NotSupportedException("Executors.newThreadPerTaskExecutor")
        if (thrown != null) e2.addSuppressed(thrown)
        throw e2
    }

    @JvmStatic
    @Throws(NotSupportedException::class)
    fun factory(prefix: String = VIRTUAL_THREAD_NAME_PREFIX): ThreadFactory {
        if (!areVirtualThreadsSupported()) {
            throw NotSupportedException("Thread.ofVirtual")
        }
        try {
            val builderClass = Class.forName("java.lang.Thread\$Builder")
            val ofVirtualClass = Class.forName("java.lang.Thread\$Builder\$OfVirtual")
            val lookup = MethodHandles.lookup()
            val ofVirtualMethod = lookup.findStatic(Thread::class.java, "ofVirtual", MethodType.methodType(ofVirtualClass))
            var builder = ofVirtualMethod.invoke()
            val nameMethod = lookup.findVirtual(ofVirtualClass, "name", MethodType.methodType(ofVirtualClass, String::class.java, Long::class.javaPrimitiveType))
            val factoryMethod = lookup.findVirtual(builderClass, "factory", MethodType.methodType(ThreadFactory::class.java))
            builder = nameMethod.invoke(builder, prefix, 0L)
            return factoryMethod.invoke(builder) as ThreadFactory
        } catch (e: Throwable) {
            val e2 = NotSupportedException("Thread.ofVirtual")
            e2.addSuppressed(e)
            throw e2
        }
    }

    private val isVirtualMethodHandle: MethodHandle?

    init {
        var tempHandle: MethodHandle?
        try {
            val threadClass = Class.forName("java.lang.Thread")
            val lookup = MethodHandles.lookup()
            tempHandle = lookup.findVirtual(
                threadClass,
                "isVirtual",
                MethodType.methodType(Boolean::class.javaPrimitiveType)
            )
        } catch (e: Throwable) {
            tempHandle = null
        }
        isVirtualMethodHandle = tempHandle
    }

    @JvmStatic
    fun isVirtualThread(th: Thread): Boolean {
        try {
            if (isVirtualMethodHandle != null) {
                return isVirtualMethodHandle.invoke(th) as Boolean
            }
        } catch (e: Throwable) {
            // Ignored
        }
        return false
    }

    @JvmStatic
    fun areVirtualThreadsSupported(): Boolean {
        val sp = System.getProperty("funfix.tasks.virtual-threads")
        val disableFeature = sp.equals("off", ignoreCase = true)
                || sp.equals("false", ignoreCase = true)
                || sp.equals("no", ignoreCase = true)
                || sp == "0"
                || sp.equals("disabled", ignoreCase = true)
        return !disableFeature && isVirtualMethodHandle != null && newThreadPerTaskExecutorMethodHandle != null
    }

    const val VIRTUAL_THREAD_NAME_PREFIX = "common-io-virtual-"
}
