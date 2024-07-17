@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.funfix.tasks

import org.funfix.tasks.support.Executor
import org.funfix.tasks.support.MutableCancellable
import org.funfix.tasks.support.NonBlocking
import org.funfix.tasks.support.Runnable
import kotlin.js.Promise

public actual interface Fiber : Cancellable {
    @NonBlocking
    public actual fun joinAsync(onComplete: Runnable): Cancellable

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
        internal fun start(
            executor: ExecuteCancellableFun,
            command: Runnable
        ): Fiber {
            val fiber = ExecutedFiber()
            val token = executor.executeCancellable(command) { fiber.signalComplete() }
            fiber.registerCancel(token)
            return fiber
        }
    }
}

public actual interface FiberExecutor: Executor {
    public actual companion object {
        public actual val global: FiberExecutor
            get() = object : FiberExecutor {
                override fun execute(command: Runnable) {
                    TaskExecutors.global.execute(command)
                }
            }
    }
}

/**
 * Represents a [Task] that has started execution and is running concurrently.
 *
 * @param T is the type of the value that the task will complete with
 */
public actual interface TaskFiber<out T> : Fiber {
    /**
     * @return the [Outcome] of the task, if it has completed, or `null` if the
     * task is still running.
     */
    @NonBlocking
    public actual fun outcome(): Outcome<T>?

    public companion object {
        /**
         * INTERNAL API, do not use!
         */
        @NonBlocking
        internal fun <T> start(executor: FiberExecutor, asyncFun: AsyncFun<T>): TaskFiber<T> {
            val fiber = ExecutedTaskFiber<T>()
            try {
                val token = asyncFun(executor, fiber.onComplete)
                fiber.registerCancel(token)
            } catch (e: Throwable) {
                UncaughtExceptionHandler.rethrowIfFatal(e)
                fiber.onComplete.complete(Outcome.failed(e))
            }
            return fiber
        }
    }
}

public fun interface ExecuteCancellableFun {
    @NonBlocking
    public fun executeCancellable(command: Runnable, onComplete: Runnable?): Cancellable
}

/**
 * INTERNAL API, do not use!
 */
private class ExecutedTaskFiber<T>(
    private val fiber: ExecutedFiber = ExecutedFiber()
) : TaskFiber<T> {
    private var outcomeRef: Outcome<T>? = null

    override fun outcome(): Outcome<T>? =
        outcomeRef

    override fun joinAsync(onComplete: Runnable): Cancellable =
        fiber.joinAsync(onComplete)

    override fun cancel() {
        fiber.cancel()
    }

    fun registerCancel(token: Cancellable) {
        fiber.registerCancel(token)
    }

    private fun signalComplete(outcome: Outcome<T>) {
        if (outcomeRef == null) {
            outcomeRef = outcome
            fiber.signalComplete()
        } else if (outcome is Outcome.Failed) {
            UncaughtExceptionHandler.logOrRethrow(outcome.exception)
        }
    }

    val onComplete: CompletionCallback<T> =
        CompletionCallback { outcome ->
            if (outcome is Outcome.Failed) {
                UncaughtExceptionHandler.rethrowIfFatal(outcome.exception)
            }
            signalComplete(outcome)
        }
}

private class ExecutedFiber : Fiber {
    private var stateRef: State = State.start()

    override fun joinAsync(onComplete: Runnable): Cancellable {
        when (val current = stateRef) {
            is State.Active, is State.Cancelled -> {
                val update = current.addListener(onComplete)
                stateRef = update
                return removeListenerCancellable(onComplete)
            }
            State.Completed -> {
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
            is State.Cancelled, State.Completed ->
                return
        }
    }

    fun signalComplete() {
        when (val current = stateRef)  {
            is State.Active, is State.Cancelled -> {
                stateRef = State.Completed
                current.triggerListeners()
                return
            }
            State.Completed ->
                return
        }
    }

    fun registerCancel(token: Cancellable) {
        when (val current = stateRef) {
            is State.Active -> {
                if (current.token != null) {
                    throw IllegalStateException("Already registered a cancel token")
                }
                stateRef = State.Active(current.listeners, token)
                return
            }
            is State.Cancelled -> {
                token.cancel()
                return
            }
            is State.Completed ->
                return
        }
    }

    private fun removeListenerCancellable(listener: Runnable): Cancellable =
        Cancellable {
            when (val current = stateRef) {
                is State.Active, is State.Cancelled -> {
                    stateRef = current.removeListener(listener)
                    return@Cancellable
                }
                is State.Completed ->
                    return@Cancellable
            }
        }

    sealed interface State {
        data class Active(val listeners: List<Runnable>, val token: Cancellable?) : State
        data class Cancelled(val listeners: List<Runnable>) : State
        data object Completed : State

        fun triggerListeners() =
            when (this) {
                is Active -> listeners.forEach(TaskExecutors.global::execute)
                is Cancelled -> listeners.forEach(TaskExecutors.global::execute)
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
}
