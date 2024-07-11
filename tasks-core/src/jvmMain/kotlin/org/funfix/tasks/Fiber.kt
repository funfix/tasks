package org.funfix.tasks

import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.AbstractQueuedSynchronizer

fun interface FiberExecutor {
    fun execute(command: Runnable): Fiber

    companion object {
        fun fromThreadFactory(factory: ThreadFactory): FiberExecutor =
            FiberExecutorDefault.fromThreadFactory(factory)

        fun fromExecutor(executor: Executor): FiberExecutor =
            FiberExecutorDefault.fromExecutor(executor)

        fun shared(): FiberExecutor =
            FiberExecutorDefault.shared()
    }
}

interface Fiber : Cancellable {
    fun joinAsync(onComplete: Runnable): Cancellable

    @Throws(InterruptedException::class, TimeoutException::class)
    fun joinBlockingTimed(timeout: Duration) {
        val latch = AwaitSignalForFiber()
        val token = joinAsync { latch.signal() }
        try {
            latch.await(timeout)
        } catch (e: InterruptedException) {
            token.cancel()
            throw e
        } catch (e: TimeoutException) {
            token.cancel()
            throw e
        }
    }

    @Throws(InterruptedException::class)
    fun joinBlocking() {
        val latch = AwaitSignalForFiber()
        val token = joinAsync { latch.signal() }
        try {
            latch.await()
        } finally {
            token.cancel()
        }
    }

    fun joinAsync(): CancellableFuture<Void?> {
        val p = CompletableFuture<Void?>()
        val token = joinAsync { p.complete(null) }
        val cRef = Cancellable {
            try {
                token.cancel()
            } finally {
                p.cancel(false)
            }
        }
        return CancellableFuture(p, cRef)
    }
}

private fun interface RunnableExecuteFun {
    operator fun invoke(command: Runnable, onComplete: Runnable?): Cancellable
}

private class SimpleFiber : Fiber {
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
                    Trampoline.execute(onComplete)
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
                is Active -> listeners.forEach(Trampoline::execute)
                is Cancelled -> listeners.forEach(Trampoline::execute)
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

    companion object {
        fun create(
            execute: RunnableExecuteFun,
            command: Runnable
        ): Fiber {
            val fiber = SimpleFiber()
            val token = execute.invoke(command) { fiber.signalComplete() }
            fiber.registerCancel(token)
            return fiber
        }
    }
}

private class AwaitSignalForFiber : AbstractQueuedSynchronizer() {
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
    fun await(timeout: Duration) {
        if (!tryAcquireSharedNanos(1, timeout.toNanos())) {
            throw TimeoutException("Timed out after $timeout")
        }
    }
}

private class FiberExecutorDefault private constructor(
    private val _executeFun: RunnableExecuteFun
) : FiberExecutor {
    override fun execute(command: Runnable): Fiber =
        SimpleFiber.create(_executeFun, command)

    companion object {
        fun fromThreadFactory(factory: ThreadFactory): FiberExecutor =
            FiberExecutorDefault(RunnableExecuteFunViaThreadFactory(factory))

        fun fromExecutor(executor: Executor): FiberExecutor =
            FiberExecutorDefault(RunnableExecuteFunViaExecutor(executor))

        @Volatile
        private var defaultFiberExecutorOlderJavaRef: FiberExecutor? = null
        @Volatile
        private var defaultFiberExecutorLoomRef: FiberExecutor? = null

        fun shared(): FiberExecutor {
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
                defaultFiberExecutorOlderJavaRef = fromExecutor(ThreadPools.sharedIO())
                return defaultFiberExecutorOlderJavaRef!!
            }
        }
    }
}

private class RunnableExecuteFunViaThreadFactory(
    private val _factory: ThreadFactory
) : RunnableExecuteFun {
    override fun invoke(command: Runnable, onComplete: Runnable?): Cancellable {
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

private class RunnableExecuteFunViaExecutor(
    private val _executor: Executor
) : RunnableExecuteFun {
    override fun invoke(command: Runnable, onComplete: Runnable?): Cancellable {
        val state = AtomicReference<State>(State.NotStarted)
        _executor.execute {
            val running = State.Running(Thread.currentThread())
            if (!state.compareAndSet(State.NotStarted, running)) {
                onComplete?.run()
                return@execute
            }
            try {
                command.run()
            } catch (e: Exception) {
                UncaughtExceptionHandler.logException(e)
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
                    if (state.compareAndSet(current, State.Completed)) return
                is State.Running -> {
                    val wasInterrupted = AwaitSignalForFiber()
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
        data class Interrupting(val wasInterrupted: AwaitSignalForFiber) : State
        data object Completed : State
    }
}
