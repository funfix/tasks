@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.funfix.tasks

import org.funfix.tasks.support.NonBlocking
import org.funfix.tasks.support.Runnable
import org.funfix.tasks.support.Serializable

public actual abstract class Task<out T> : Serializable {
    protected actual abstract val unsafeExecuteAsync: AsyncFun<T>

    /**
     * Starts the asynchronous execution of this task.
     *
     * @param callback will be invoked with the result when the task completes
     * @param executor is the [FiberExecutor] that may be used to run the task
     *
     * @return a [Cancellable] that can be used to cancel a running task
     */
    @NonBlocking
    public actual fun executeAsync(
        executor: FiberExecutor,
        callback: CompletionCallback<T>
    ): Cancellable {
        val protected = ProtectedCompletionCallback(callback)
        return try {
            unsafeExecuteAsync(executor, protected)
        } catch (e: Throwable) {
            UncaughtExceptionHandler.rethrowIfFatal(e)
            protected.complete(Outcome.failed(e))
            Cancellable.EMPTY
        }
    }
}

private class ProtectedCompletionCallback<T> private constructor(
    private var listener: CompletionCallback<T>?
) : CompletionCallback<T>, Runnable {
    private var isWaiting = true
    private var outcome: Outcome<T>? = null

    override fun run() {
        val listener = this.listener
        if (listener != null) {
            listener.complete(outcome!!)
            // GC purposes
            this.outcome = null
            this.listener = null
        }
    }

    override fun complete(outcome: Outcome<T>) {
        if (isWaiting) {
            this.isWaiting = false
            this.outcome = outcome
            // Trampolined execution is needed because, with chained tasks,
            // we might end up with a stack overflow
            ThreadPools.TRAMPOLINE.execute(this)
        } else if (outcome is Outcome.Failed) {
            UncaughtExceptionHandler.logOrRethrow(outcome.exception)
        }
    }

    companion object {
        operator fun <T> invoke(listener: CompletionCallback<T>): CompletionCallback<T> =
            when {
                listener is ProtectedCompletionCallback<*> -> listener
                else -> ProtectedCompletionCallback(listener)
            }
    }
}
