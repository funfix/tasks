package org.funfix.tasks

import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.AbstractQueuedSynchronizer

interface Fiber : Cancellable {
    fun joinAsync(onComplete: Runnable): Cancellable

    fun joinTimed(timeout: Duration) {
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

    fun join() {
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
    operator fun invoke(command: Runnable, onComplete: Runnable): Cancellable
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
        object Completed : State

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
