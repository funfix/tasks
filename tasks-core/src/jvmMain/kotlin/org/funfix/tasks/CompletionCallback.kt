package org.funfix.tasks

import java.io.Serializable
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Represents a callback that will be invoked when a task completes.
 *
 * A task can complete either successfully with a value, or with an exception,
 * or it can be cancelled.
 *
 * MUST BE idempotent AND thread-safe.
 *
 * @param T is the type of the value that the task will complete with
 */
interface CompletionCallback<in T> : Serializable {
    /**
     * Must be called when the task completes successfully.
     *
     * @param value is the successful result of the task, to be signaled
     */
    fun onSuccess(value: T)

    /**
     * Must be called when the task completes with an exception.
     *
     * @param e is the exception that the task failed with
     */
    fun onFailure(e: Throwable)

    /**
     * Must be called when the task is cancelled.
     */
    fun onCancel()

    /**
     * Signals a final [Outcome] to this listener.
     */
    fun onCompletion(outcome: Outcome<T>) {
        when (outcome) {
            is Outcome.Succeeded -> onSuccess(outcome.value)
            is Outcome.Failed -> onFailure(outcome.exception)
            is Outcome.Cancelled -> onCancel()
        }
    }

    companion object {
        /**
         * @return a [CompletionCallback] that does nothing.
         */
        @JvmStatic
        fun <T> empty(): CompletionCallback<T> = object : CompletionCallback<T> {
            override fun onSuccess(value: T) {}
            override fun onCancel() {}
            override fun onFailure(e: Throwable) {
                UncaughtExceptionHandler.logException(e)
            }
        }

        @JvmStatic
        fun <T> protect(listener: CompletionCallback<T>): CompletionCallback<T> =
            ProtectedCompletionListener(listener)
    }
}

/**
 * Protects a given [CompletionCallback], by:
 * 1. Ensuring that the underlying listener is called at most once.
 * 2. Trampolining the call, such that stack overflows are avoided.
 */
object ProtectedCompletionListener {
    @JvmStatic
    operator fun <T> invoke(listener: CompletionCallback<T>): CompletionCallback<T> =
        Implementation(listener)

    private class Implementation<T>(
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
                UncaughtExceptionHandler.logException(e)
            }
        }

        override fun onCancel() {
            signal { this.cancelled = true }
        }
    }
}
