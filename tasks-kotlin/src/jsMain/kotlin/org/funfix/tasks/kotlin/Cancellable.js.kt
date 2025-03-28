@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.funfix.tasks.kotlin

public actual fun interface Cancellable {
    public actual fun cancel()

    public companion object {
        public val empty: Cancellable =
            Cancellable {}
    }
}

internal class MutableCancellable : Cancellable {
    private var ref: State = State.Active(Cancellable.empty, 0)

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
