package org.funfix.tasks

import java.time.Duration
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.AbstractQueuedSynchronizer

fun interface Task<out T> {
    fun executeAsync(onComplete: CompletionListener<T>): Cancellable

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
     * @throws ExecutionException if the task fails with an exception
     *
     * @throws InterruptedException if the current thread is interrupted,
     *         which also cancels the running task. Note that on interruption,
     *         the running concurrent task must also be interrupted, as this method
     *         always blocks for its interruption or completion.
     */
    @Throws(ExecutionException::class, InterruptedException::class)
    fun executeBlocking(): T {
        val h = BlockingCompletionListener<T>()
        val cancelToken = executeAsync(h)
        return h.await(cancelToken)
    }

    /**
     * Executes the task and blocks until it completes, or the timeout is reached,
     * or the current thread is interrupted.
     *
     * @throws ExecutionException if the task fails with an exception
     *
     * @throws InterruptedException if the current thread is interrupted.
     *         The running task is also cancelled, and this method does not
     *         return until `onCancel` is signaled.
     *
     * @throws TimeoutException if the task doesn't complete within the
     *         specified timeout. The running task is also cancelled on timeout,
     *         and this method does not returning until `onCancel` is signaled.
     */
    @Throws(ExecutionException::class, InterruptedException::class, TimeoutException::class)
    fun executeBlockingTimed(timeout: Duration): T {
        val h = BlockingCompletionListener<T>()
        val cancelToken = executeAsync(h)
        return h.await(cancelToken, timeout)
    }

    companion object {
        @JvmStatic fun <T> fromCompletableFuture(builder: Callable<CompletableFuture<T>>): Task<T> =
            TaskFromCompletableFuture(builder)

        @JvmStatic fun <T> blockingIO(es: Executor, callable: Callable<T>): Task<T> =
            TaskFromExecutor(es, callable)

        /**
         * Creates a `Task` from a (suspended) [java.util.concurrent.Future] computation.
         *
         * Java's classic `Future` is blocking, so its use will block the current thread.
         */
        @JvmStatic fun <T> fromBlockingFuture(es: Executor, builder: Callable<Future<T>>): Task<T> =
            blockingIO(es) {
                val f = builder.call()
                try {
                    f.get()
                } catch (e: InterruptedException) {
                    f.cancel(true)
                    while (!f.isDone) {
                        Thread.interrupted()
                        try {
                            f.get()
                        } catch (_: Exception) {}
                    }
                    throw e
                }
            }
    }
}

private class BlockingCompletionListener<T>: AbstractQueuedSynchronizer(), CompletionListener<T> {
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
        var firstAwait = true
        var timedOut: TimeoutException? = null
        while (true) {
            try {
                await(firstAwait)
                break
            } catch (e: TimeoutException) {
                if (firstAwait) {
                    firstAwait = false
                    timedOut = e
                    cancelToken.cancel()
                }
            } catch (e: InterruptedException) {
                if (firstAwait) {
                    cancelToken.cancel()
                    firstAwait = false
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
        awaitInline(cancelToken) { acquireSharedInterruptibly(1) }

    @Throws(InterruptedException::class, ExecutionException::class, TimeoutException::class)
    fun await(cancelToken: Cancellable, timeout: Duration): T =
        awaitInline(cancelToken) {
            if (!tryAcquireSharedNanos(1, timeout.toNanos()))
                throw TimeoutException("Task timed-out after $timeout")
        }
}

private class TaskFromCompletableFuture<T>(
    private val builder: Callable<CompletableFuture<T>>
): Task<T> {
    override fun executeAsync(onComplete: CompletionListener<T>): Cancellable {
        var userError = true
        try {
            val future = builder.call()
            userError = false

            future.whenCompleteAsync { value, error ->
                when {
                    error is InterruptedException -> onComplete.onCancel()
                    error is CancellationException -> onComplete.onCancel()
                    error is CompletionException -> onComplete.onFailure(error.cause ?: error)
                    error != null -> onComplete.onFailure(error)
                    else -> onComplete.onSuccess(value)
                }
            }
            return Cancellable {
                future.cancel(true)
            }
        } catch (e: Exception) {
            if (userError) {
                onComplete.onFailure(e)
                return Cancellable.EMPTY
            }
            throw e
        }
    }
}

private class AwaitSignal: AbstractQueuedSynchronizer() {
    override fun tryAcquireShared(arg: Int): Int =
        if (state != 0) 1 else -1

    override fun tryReleaseShared(arg: Int): Boolean {
        state = 1
        return true
    }

    fun signal() {
        releaseShared(1)
    }

    fun await() {
        acquireSharedInterruptibly(1)
    }
}

private class TaskFromExecutor<T>(
    private val es: Executor,
    private val callable: Callable<T>
): Task<T> {
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

    override fun executeAsync(onComplete: CompletionListener<T>): Cancellable {
        val state = AtomicReference<State>(NotStarted)
        es.execute {
            if (!state.compareAndSet(NotStarted, Running(Thread.currentThread()))) {
                onComplete.onCancel()
                return@execute
            }
            var result: T? = null
            var error: Throwable? = null
            var interrupted = false
            try {
                result = callable.call()
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
                                onComplete.onCancel()
                            else if (error != null)
                                onComplete.onFailure(error)
                            else
                                @Suppress("UNCHECKED_CAST")
                                onComplete.onSuccess(result as T)
                            return@execute
                        }
                    }
                    is Interrupting -> {
                        try {
                            current.wasInterrupted.await()
                        } catch (_: InterruptedException) {}
                        onComplete.onCancel()
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

private class TaskFiber<T>: Fiber<T> {
    private val ref = AtomicReference<State<T>>(State.START)

    private sealed class State<out T> {
        data class Active(
            val listeners: List<Runnable>,
            val token: Cancellable?
        ): State<Nothing>()

        data class Cancelled(
            val listeners: List<Runnable>
        ): State<Nothing>()

        data class Completed<out T>(
            val isSuccessful: Boolean,
            val result: T?,
            val isFailure: Boolean,
            val failure: Throwable?,
            val isCancelled: Boolean,
        ): State<T>()

        companion object {
            val START = Active(emptyList(), null)
        }
    }

    val onComplete: CompletionListener<T> = object: CompletionListener<T> {
        override fun onSuccess(value: T) =
            signalComplete(State.Completed(
                isSuccessful = true,
                result = value,
                isFailure = false,
                failure = null,
                isCancelled = false
            ))

        override fun onFailure(e: Throwable) =
            signalComplete(State.Completed(
                isSuccessful = false,
                result = null,
                isFailure = true,
                failure = e,
                isCancelled = false
            ))

        override fun onCancel() =
            signalComplete(State.Completed(
                isSuccessful = false,
                result = null,
                isFailure = false,
                failure = null,
                isCancelled = true
            ))
    }

    fun registerCancel(token: Cancellable) {
        while (true) {
            when (val current = ref.get()) {
                is State.Active -> {
                    if (current.token != null) {
                        throw IllegalStateException("Cancellable already registered")
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
                is State.Completed -> {
                    return
                }
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
                is State.Cancelled -> {
                    return
                }
                is State.Completed -> {
                    return
                }
            }
        }
    }

    override fun resultOrThrow(): T {
        when (val state = ref.get()) {
            is State.Completed -> {
                if (state.isSuccessful) {
                    @Suppress("UNCHECKED_CAST")
                    return state.result as T
                } else if (state.isFailure) {
                    throw ExecutionException(state.failure)
                } else {
                    throw CancellationException()
                }
            }
            else -> {
                throw NotCompletedException()
            }
        }
    }

    override fun joinBlocking() {
        val latch = AwaitSignal()
        val runnable = Runnable { latch.signal() }
        val token = joinAsync(runnable)
        try {
            latch.await()
        } catch (e: InterruptedException) {
            token.cancel()
            throw e
        }
    }

    override fun joinAsync(onComplete: Runnable): Cancellable {
        while (true) {
            when (val current = ref.get()) {
                is State.Active -> {
                    val update = current.copy(listeners = current.listeners + onComplete)
                    if (ref.compareAndSet(current, update)) {
                        return joinCancellable(onComplete)
                    }
                }
                is State.Cancelled -> {
                    val update = current.copy(listeners = current.listeners + onComplete)
                    if (ref.compareAndSet(current, update)) {
                        return joinCancellable(onComplete)
                    }
                }
                is State.Completed -> {
                    Trampoline.execute(onComplete)
                    return Cancellable.EMPTY
                }
            }
        }
    }

    private fun signalComplete(state: State.Completed<T>) {
        while (true) {
            when (val current = ref.get()) {
                is State.Active -> {
                    if (ref.compareAndSet(current, state)) {
                        current.listeners.forEach { Trampoline.execute(it) }
                    }
                }
                is State.Cancelled -> {
                    if (ref.compareAndSet(current, state)) {
                        current.listeners.forEach { Trampoline.execute(it) }
                    }
                }
                is State.Completed -> {}
            }
        }
    }

    private fun joinCancellable(onComplete: Runnable) =
        Cancellable {
            while (true) {
                when (val current = ref.get()) {
                    is State.Active -> {
                        val update = current.copy(listeners = current.listeners - onComplete)
                        if (ref.compareAndSet(current, update)) {
                            return@Cancellable
                        }
                    }
                    is State.Cancelled -> {
                        val update = current.copy(listeners = current.listeners - onComplete)
                        if (ref.compareAndSet(current, update)) {
                            return@Cancellable
                        }
                    }
                    is State.Completed -> {
                        return@Cancellable
                    }
                }
            }
        }
}

private object Trampoline: Executor {
    private val queue = ThreadLocal<MutableList<Runnable>?>()

    private fun eventLoop() {
        while (true) {
            val current = queue.get() ?: return
            val next = current.removeFirstOrNull() ?: return
            try {
                next.run()
            } catch (e: Exception) {
                UncaughtExceptionHandler.logException(e)
            }
        }
    }

    override fun execute(command: Runnable) {
        val current = queue.get()
        if (current == null) {
            queue.set(mutableListOf(command))
            try {
                eventLoop()
            } finally {
                queue.set(null)
            }
        } else {
            current.add(command)
        }
    }
}

