@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.funfix.tasks

import org.jetbrains.annotations.Blocking
import org.jetbrains.annotations.NonBlocking
import java.time.Duration
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.AbstractQueuedSynchronizer


public actual interface FiberExecutor: Executor, ExecuteCancellableFun {
    @NonBlocking
    public fun startFiber(command: Runnable): Fiber

    public actual companion object {
        @JvmStatic
        public fun fromThreadFactory(factory: ThreadFactory): FiberExecutor =
            FiberExecutorDefault.fromThreadFactory(factory)

        @JvmStatic
        public fun fromExecutor(executor: Executor): FiberExecutor =
            FiberExecutorDefault.fromExecutor(executor)

        public actual val global: FiberExecutor
            @JvmStatic @JvmName("global")
            get() = FiberExecutorDefault.global()
    }
}

public actual interface Fiber : Cancellable {
    @NonBlocking
    public actual fun joinAsync(onComplete: Runnable): Cancellable

    @Blocking
    @Throws(InterruptedException::class, TimeoutException::class)
    public fun joinBlockingTimed(timeoutMillis: Long) {
        val latch = AwaitSignal()
        val token = joinAsync { latch.signal() }
        try {
            latch.await(timeoutMillis, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            token.cancel()
            throw e
        } catch (e: TimeoutException) {
            token.cancel()
            throw e
        }
    }

    @Blocking
    @Throws(InterruptedException::class, TimeoutException::class)
    public fun joinBlockingTimed(timeout: Duration): Unit =
        joinBlockingTimed(timeout.toMillis())

    @Blocking
    @Throws(InterruptedException::class)
    public fun joinBlocking() {
        val latch = AwaitSignal()
        val token = joinAsync { latch.signal() }
        try {
            latch.await()
        } finally {
            token.cancel()
        }
    }

    @NonBlocking
    public fun joinAsync(): CancellableFuture<Unit> {
        val p = CompletableFuture<Unit>()
        val token = joinAsync { p.complete(Unit) }
        val cRef = Cancellable {
            try {
                token.cancel()
            } finally {
                p.cancel(false)
            }
        }
        return CancellableFuture(p, cRef)
    }

    public companion object {
        /**
         * INTERNAL API, do not use!
         */
        @NonBlocking
        internal fun start(executor: ExecuteCancellableFun, command: Runnable): Fiber {
            val fiber = ExecutedFiber()
            val token = executor.executeCancellable(command) { fiber.signalComplete() }
            fiber.registerCancel(token)
            return fiber
        }
    }
}

/**
 * Represents a [Task] that has started execution and is running concurrently.
 *
 * @param T is the type of the value that the task will complete with
 */
public actual interface TaskFiber<out T> : Fiber {
    /**
     * @return the [Outcome] of the task, if it has completed, or `null` if the
     * task is still running.
     */
    @NonBlocking
    public actual fun outcome(): Outcome<T>?

    public companion object {
        /**
         * INTERNAL API, do not use!
         */
        @NonBlocking
        internal fun <T> start(executor: FiberExecutor, asyncFun: AsyncFun<T>): TaskFiber<T> {
            val fiber = ExecutedTaskFiber<T>()
            try {
                val token = asyncFun(executor, fiber.onComplete)
                fiber.registerCancel(token)
            } catch (e: Throwable) {
                UncaughtExceptionHandler.rethrowIfFatal(e)
                fiber.onComplete.complete(Outcome.failed(e))
            }
            return fiber
        }
    }
}

public fun interface ExecuteCancellableFun {
    @NonBlocking
    public fun executeCancellable(command: Runnable, onComplete: Runnable?): Cancellable
}

/**
 * INTERNAL API, do not use!
 */
private class ExecutedFiber : Fiber {
    private val stateRef = AtomicReference(State.start())

    override fun joinAsync(onComplete: Runnable): Cancellable {
        while (true) {
            when (val current = stateRef.get()) {
                is State.Active, is State.Cancelled -> {
                    val update = current.addListener(onComplete)
                    if (stateRef.compareAndSet(current, update)) {
                        return removeListenerCancellable(onComplete)
                    }
                }
                State.Completed -> {
                    TaskExecutors.trampoline.execute(onComplete)
                    return Cancellable.EMPTY
                }
            }
        }
    }

    override fun cancel() {
        while (true) {
            when (val current = stateRef.get()) {
                is State.Active ->
                    if (stateRef.compareAndSet(current, State.Cancelled(current.listeners))) {
                        current.token?.cancel()
                        return
                    }
                is State.Cancelled, State.Completed ->
                    return
            }
        }
    }

    fun signalComplete() {
        while (true) {
            when (val current = stateRef.get())  {
                is State.Active, is State.Cancelled ->
                    if (stateRef.compareAndSet(current, State.Completed)) {
                        current.triggerListeners()
                        return
                    }
                State.Completed ->
                    return
            }
        }
    }

    fun registerCancel(token: Cancellable) {
        while (true) {
            when (val current = stateRef.get()) {
                is State.Active -> {
                    if (current.token != null) {
                        throw IllegalStateException("Already registered a cancel token")
                    }
                    val update = State.Active(current.listeners, token)
                    if (stateRef.compareAndSet(current, update)) {
                        return
                    }
                }
                is State.Cancelled -> {
                    token.cancel()
                    return
                }
                is State.Completed ->
                    return
            }
        }
    }

    private fun removeListenerCancellable(listener: Runnable): Cancellable =
        Cancellable {
            while (true) {
                when (val current = stateRef.get()) {
                    is State.Active, is State.Cancelled -> {
                        val update = current.removeListener(listener)
                        if (stateRef.compareAndSet(current, update)) {
                            return@Cancellable
                        }
                    }
                    is State.Completed ->
                        return@Cancellable
                }
            }
        }

    sealed interface State {
        data class Active(val listeners: List<Runnable>, val token: Cancellable?) : State
        data class Cancelled(val listeners: List<Runnable>) : State
        data object Completed : State

        fun triggerListeners() =
            when (this) {
                is Active -> listeners.forEach(TaskExecutors.trampoline::execute)
                is Cancelled -> listeners.forEach(TaskExecutors.trampoline::execute)
                is Completed -> {}
            }

        fun addListener(listener: Runnable): State =
            when (this) {
                is Active -> Active(listeners + listener, token)
                is Cancelled -> Cancelled(listeners + listener)
                is Completed -> this
            }

        fun removeListener(listener: Runnable): State =
            when (this) {
                is Active -> Active(listeners.filter { it != listener }, token)
                is Cancelled -> Cancelled(listeners.filter { it != listener })
                is Completed -> this
            }

        companion object {
            fun start(): State = Active(listOf(), null)
        }
    }
}

/**
 * INTERNAL API, do not use!
 */
private class ExecutedTaskFiber<T>(
    private val fiber: ExecutedFiber = ExecutedFiber()
) : TaskFiber<T> {
    private val outcomeRef: AtomicReference<Outcome<T>?> =
        AtomicReference(null)

    override fun outcome(): Outcome<T>? =
        outcomeRef.get()

    override fun joinAsync(onComplete: Runnable): Cancellable =
        fiber.joinAsync(onComplete)

    override fun cancel() {
        fiber.cancel()
    }

    fun registerCancel(token: Cancellable) {
        fiber.registerCancel(token)
    }

    private fun signalComplete(outcome: Outcome<T>) {
        if (outcomeRef.compareAndSet(null, outcome)) {
            fiber.signalComplete()
        } else if (outcome is Outcome.Failed) {
            UncaughtExceptionHandler.logOrRethrow(outcome.exception)
        }
    }

    val onComplete: CompletionCallback<T> =
        CompletionCallback { outcome ->
            if (outcome is Outcome.Failed) {
                UncaughtExceptionHandler.rethrowIfFatal(outcome.exception)
            }
            signalComplete(outcome)
        }
}

private class FiberExecutorDefault private constructor(
    private val _executeFun: ExecuteCancellableFun,
    private val _executor: Executor
) : FiberExecutor {
    override fun startFiber(command: Runnable): Fiber =
        Fiber.start(_executeFun, command)

    override fun executeCancellable(command: Runnable, onComplete: Runnable?): Cancellable =
        _executeFun.executeCancellable(command, onComplete)

    override fun execute(command: Runnable) =
        _executor.execute(command)

    companion object {
        fun fromThreadFactory(factory: ThreadFactory): FiberExecutor =
            FiberExecutorDefault(
                ExecuteCancellableFunViaThreadFactory(factory)
            ) { command ->
                val t = factory.newThread(command)
                t.start()
            }

        fun fromExecutor(executor: Executor): FiberExecutor =
            FiberExecutorDefault(
                ExecuteCancellableFunViaExecutor(executor),
                executor
            )

        @Volatile
        private var defaultFiberExecutorOlderJavaRef: FiberExecutor? = null
        @Volatile
        private var defaultFiberExecutorLoomRef: FiberExecutor? = null

        fun global(): FiberExecutor {
            if (VirtualThreads.areVirtualThreadsSupported()) {
                // Double-checked locking
                if (defaultFiberExecutorLoomRef != null) return defaultFiberExecutorLoomRef!!
                synchronized(this) {
                    if (defaultFiberExecutorLoomRef != null) return defaultFiberExecutorLoomRef!!
                    try {
                        defaultFiberExecutorLoomRef = fromThreadFactory(VirtualThreads.factory())
                        return defaultFiberExecutorLoomRef!!
                    } catch (ignored: VirtualThreads.NotSupportedException) {}
                }
            }
            // Double-checked locking
            if (defaultFiberExecutorOlderJavaRef != null) return defaultFiberExecutorOlderJavaRef!!
            synchronized(this) {
                if (defaultFiberExecutorOlderJavaRef != null) return defaultFiberExecutorOlderJavaRef!!
                defaultFiberExecutorOlderJavaRef = fromExecutor(TaskExecutors.global)
                return defaultFiberExecutorOlderJavaRef!!
            }
        }
    }
}

private class ExecuteCancellableFunViaThreadFactory(
    private val _factory: ThreadFactory
) : ExecuteCancellableFun {
    override fun executeCancellable(command: Runnable, onComplete: Runnable?): Cancellable {
        val t = _factory.newThread {
            try {
                command.run()
            } finally {
                onComplete?.run()
            }
        }
        t.start()
        return Cancellable { t.interrupt() }
    }
}

private class ExecuteCancellableFunViaExecutor(
    private val _executor: Executor
) : ExecuteCancellableFun {
    override fun executeCancellable(command: Runnable, onComplete: Runnable?): Cancellable {
        val state = AtomicReference<State>(State.NotStarted)
        _executor.execute {
            val running = State.Running(Thread.currentThread())
            if (!state.compareAndSet(State.NotStarted, running)) {
                onComplete?.run()
                return@execute
            }
            try {
                command.run()
            } catch (e: Throwable) {
                UncaughtExceptionHandler.logOrRethrow(e)
            }

            while (true) {
                when (val current = state.get()) {
                    is State.Running ->
                        if (state.compareAndSet(current, State.Completed)) {
                            onComplete?.run()
                            return@execute
                        }
                    is State.Interrupting -> {
                        if (state.compareAndSet(current, State.Completed)) {
                            try {
                                current.wasInterrupted.await()
                            } catch (ignored: InterruptedException) {
                            } finally {
                                onComplete?.run()
                            }
                            return@execute
                        }
                    }
                    State.Completed, State.NotStarted ->
                        throw IllegalStateException("Invalid state: $current")
                }
            }
        }
        return Cancellable { triggerCancel(state) }
    }

    private fun triggerCancel(state: AtomicReference<State>) {
        while (true) {
            when (val current = state.get()) {
                is State.NotStarted ->
                    if (state.compareAndSet(current, State.Completed)) {
                        return
                    }
                is State.Running -> {
                    val wasInterrupted = AwaitSignal()
                    if (state.compareAndSet(current, State.Interrupting(wasInterrupted))) {
                        current.thread.interrupt()
                        wasInterrupted.signal()
                        return
                    }
                }
                is State.Completed ->
                    return
                is State.Interrupting ->
                    throw IllegalStateException("Invalid state: $current")
            }
        }
    }

    sealed interface State {
        data object NotStarted : State
        data class Running(val thread: Thread) : State
        data class Interrupting(val wasInterrupted: AwaitSignal) : State
        data object Completed : State
    }
}

/**
 * INTERNAL API, do not use!
 */
private class AwaitSignal : AbstractQueuedSynchronizer() {
    override fun tryAcquireShared(arg: Int): Int = if (state != 0) 1 else -1

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
    fun await(timeout: Long, unit: TimeUnit) {
        if (!tryAcquireSharedNanos(1, unit.toNanos(timeout))) {
            throw TimeoutException("Timed out after $timeout ${unit.name.lowercase()}")
        }
    }
}
