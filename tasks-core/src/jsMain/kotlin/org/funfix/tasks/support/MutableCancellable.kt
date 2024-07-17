package org.funfix.tasks.support

import org.funfix.tasks.Cancellable

@NonBlocking
internal class MutableCancellable : Cancellable {
    private var ref: State = State.Active(Cancellable.EMPTY, 0)

    override fun cancel() {
        when (val current = ref) {
            is State.Active -> {
                ref = State.Cancelled
                current.token.cancel()
            }
            State.Cancelled -> return
        }
    }

    fun set(token: Cancellable) {
        while (true) {
            when (val current = ref) {
                is State.Active -> {
                    ref = State.Active(token, current.order + 1)
                    return
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
