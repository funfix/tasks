@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.funfix.tasks

import org.funfix.tasks.support.Executor
import org.funfix.tasks.support.MutableCancellable
import org.funfix.tasks.support.NonBlocking
import org.funfix.tasks.support.Runnable
import kotlin.js.Promise

public actual interface Fiber<out T> : Cancellable {
    public actual val outcome: Outcome<T>?

    @NonBlocking
    public actual fun joinAsync(onComplete: Runnable): Cancellable

    /**
     * Returns a [CancellablePromise] that can be used to wait on the completion
     * of the fiber.
     *
     * Cancelling the returned promise will not cancel the running task, it will
     * only unregister the callback.
     */
    @NonBlocking
    public fun joinAsync(): CancellablePromise<Unit> {
        val cancel = MutableCancellable()
        val promise = Promise { resolve, reject ->
            val token = joinAsync { resolve(Unit) }
            cancel.set {
                try { token.cancel() }
                finally {
                    reject(TaskCancellationException())
                }
            }
        }
        return CancellablePromise(promise, cancel)
    }

    public companion object {
        @NonBlocking
        internal fun <T> start(executor: Executor, asyncFun: AsyncFun<T>): Fiber<T> {
            val fiber = ExecutedFiber<T>()
            try {
                val token = asyncFun(executor, fiber.callback)
                fiber.registerCancel(token)
            } catch (e: Throwable) {
                UncaughtExceptionHandler.rethrowIfFatal(e)
                fiber.callback.complete(Outcome.failed(e))
            }
            return fiber
        }
    }
}

private class ExecutedFiber<T> : Fiber<T> {
    private var stateRef: State<T> = State.start()

    override val outcome: Outcome<T>?
        get() = when (val current = stateRef) {
            is State.Completed -> current.outcome
            else -> null
        }

    override fun joinAsync(onComplete: Runnable): Cancellable {
        when (val current = stateRef) {
            is State.Active, is State.Cancelled -> {
                val update = current.addListener(onComplete)
                stateRef = update
                return removeListenerCancellable(onComplete)
            }
            is State.Completed -> {
                TaskExecutors.trampoline.execute(onComplete)
                return Cancellable.EMPTY
            }
        }
    }

    override fun cancel() {
        when (val current = stateRef) {
            is State.Active -> {
                stateRef = State.Cancelled(current.listeners)
                current.token?.cancel()
                return
            }
            is State.Cancelled, is State.Completed ->
                return
        }
    }

    val callback = CompletionCallback { outcome ->
        when (val current = stateRef) {
            is State.Active -> {
                stateRef = State.Completed(outcome)
                current.triggerListeners()
            }
            is State.Cancelled -> {
                stateRef = State.Completed(Outcome.cancelled())
                current.triggerListeners()
                if (outcome is Outcome.Failed) {
                    UncaughtExceptionHandler.logOrRethrow(outcome.exception)
                }
            }
            is State.Completed ->
                if (outcome is Outcome.Failed) {
                    UncaughtExceptionHandler.logOrRethrow(outcome.exception)
                }
        }
    }

    fun registerCancel(token: Cancellable) {
        when (val current = stateRef) {
            is State.Active<T> -> {
                if (current.token != null) {
                    throw IllegalStateException("Already registered a cancel token")
                }
                stateRef = State.Active(current.listeners, token)
                return
            }
            is State.Cancelled<*> -> {
                token.cancel()
                return
            }
            is State.Completed<*> ->
                return
        }
    }

    private fun removeListenerCancellable(listener: Runnable): Cancellable =
        Cancellable {
            when (val current = stateRef) {
                is State.Active<*>, is State.Cancelled<*> -> {
                    stateRef = current.removeListener(listener)
                    return@Cancellable
                }
                is State.Completed<*> ->
                    return@Cancellable
            }
        }

    sealed interface State<T> {
        data class Active<T>(val listeners: List<Runnable>, val token: Cancellable?) : State<T>
        data class Cancelled<T>(val listeners: List<Runnable>) : State<T>
        data class Completed<T>(val outcome: Outcome<T>) : State<T>

        fun triggerListeners() =
            when (this) {
                is Active -> listeners.forEach(TaskExecutors.global::execute)
                is Cancelled -> listeners.forEach(TaskExecutors.global::execute)
                is Completed -> {}
            }

        fun addListener(listener: Runnable): State<T> =
            when (this) {
                is Active -> Active(listeners + listener, token)
                is Cancelled -> Cancelled(listeners + listener)
                is Completed -> this
            }

        fun removeListener(listener: Runnable): State<T> =
            when (this) {
                is Active -> Active(listeners.filter { it != listener }, token)
                is Cancelled -> Cancelled(listeners.filter { it != listener })
                is Completed -> this
            }

        companion object {
            fun <T> start(): State<T> = Active(listOf(), null)
        }
    }
}
