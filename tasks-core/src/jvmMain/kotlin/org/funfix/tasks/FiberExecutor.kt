package org.funfix.tasks

import org.funfix.tasks.internals.*
import java.util.concurrent.Executor
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicReference

interface FiberExecutor: Executor, ExecuteCancellableFun {
    fun executeFiber(command: Runnable): Fiber

    companion object {
        @JvmStatic
        fun fromThreadFactory(factory: ThreadFactory): FiberExecutor =
            FiberExecutorDefault.fromThreadFactory(factory)

        @JvmStatic
        fun fromExecutor(executor: Executor): FiberExecutor =
            FiberExecutorDefault.fromExecutor(executor)

        @JvmStatic
        fun shared(): FiberExecutor =
            FiberExecutorDefault.shared()
    }
}

fun interface ExecuteCancellableFun {
    fun executeCancellable(command: Runnable, onComplete: Runnable?): Cancellable
}

private class FiberExecutorDefault private constructor(
    private val _executeFun: ExecuteCancellableFun,
    private val _executor: Executor
) : FiberExecutor {
    override fun executeFiber(command: Runnable): Fiber {
        val fiber = ExecutedFiber()
        val token = executeCancellable(command) { fiber.signalComplete() }
        fiber.registerCancel(token)
        return fiber
    }

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
            } catch (e: Exception) {
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
