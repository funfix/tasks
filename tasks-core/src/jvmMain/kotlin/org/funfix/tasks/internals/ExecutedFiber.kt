package org.funfix.tasks.internals

import org.funfix.tasks.Cancellable
import org.funfix.tasks.ExecuteCancellableFun
import org.funfix.tasks.Fiber
import java.util.concurrent.atomic.AtomicReference

/**
 * INTERNAL API, do not use!
 */
internal class ExecutedFiber : Fiber {
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
        @JvmStatic
        operator fun invoke(
            command: Runnable,
            executor: ExecuteCancellableFun,
        ): Fiber {
            val fiber = ExecutedFiber()
            val token = executor.executeCancellable(command) { fiber.signalComplete() }
            fiber.registerCancel(token)
            return fiber
        }
    }
}
