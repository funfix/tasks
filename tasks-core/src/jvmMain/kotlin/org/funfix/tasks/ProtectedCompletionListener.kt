package org.funfix.tasks

import java.util.concurrent.atomic.AtomicBoolean

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
internal actual object ProtectedCompletionListener {
    @JvmStatic
    actual operator fun <T> invoke(listener: CompletionListener<T>): CompletionListener<T> =
        Implementation(listener)

    private class Implementation<T>(
        private var listener: CompletionListener<T>?
    ) : CompletionListener<T>, Runnable {
        private var isWaiting: AtomicBoolean? = AtomicBoolean(true)

        private var value: T? = null
        private var exception: Throwable? = null
        private var cancelled: Boolean = false

        override fun run() {
            val listener = this.listener
            if (listener != null) {
                if (exception != null) {
                    listener.onFailure(exception!!)
                    exception = null
                } else if (cancelled) {
                    listener.onCancel()
                } else {
                    @Suppress("UNCHECKED_CAST")
                    listener.onSuccess(value as T)
                    value = null
                }
                this.listener = null
            }
        }

        private inline fun signal(modifyInternalState: () -> Unit) {
            val ref = this.isWaiting
            if (ref != null && ref.getAndSet(false)) {
                modifyInternalState()
                // Trampolined execution is needed because, with chained tasks,
                // we might end up with a stack overflow
                Trampoline.execute(this)
                // For GC purposes; but it doesn't really matter if we nullify this or not
                this.isWaiting = null
            }
        }

        override fun onSuccess(value: T) {
            signal { this.value = value }
        }

        override fun onFailure(e: Throwable) {
            signal { this.exception = e }
        }

        override fun onCancel() {
            signal { this.cancelled = true }
        }
    }
}
