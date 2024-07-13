package org.funfix.tasks.internals

import org.funfix.tasks.CompletionCallback
import org.funfix.tasks.UncaughtExceptionHandler
import java.util.concurrent.atomic.AtomicBoolean

internal class ProtectedCompletionCallback<T> private constructor(
    private var listener: CompletionCallback<T>?
) : CompletionCallback<T>, Runnable {
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

    private inline fun signal(modifyInternalState: () -> Unit): Boolean {
        val ref = this.isWaiting
        if (ref != null && ref.getAndSet(false)) {
            modifyInternalState()
            // Trampolined execution is needed because, with chained tasks,
            // we might end up with a stack overflow
            Trampoline.execute(this)
            // For GC purposes; but it doesn't really matter if we nullify this or not
            this.isWaiting = null
            return true
        }
        return false
    }

    override fun onSuccess(value: T) {
        signal { this.value = value }
    }

    override fun onFailure(e: Throwable) {
        val wasSignaled = signal { this.exception = e }
        if (!wasSignaled) {
            UncaughtExceptionHandler.logOrRethrow(e)
        }
    }

    override fun onCancel() {
        signal { this.cancelled = true }
    }

    companion object {
        @JvmStatic
        operator fun <T> invoke(listener: CompletionCallback<T>): CompletionCallback<T> =
            when {
                listener is ProtectedCompletionCallback<*> -> listener
                else -> ProtectedCompletionCallback(listener)
            }
    }
}
