package org.funfix.tasks.internals

import org.funfix.tasks.Cancellable
import java.util.concurrent.atomic.AtomicReference

internal class MutableCancellable : Cancellable {
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
