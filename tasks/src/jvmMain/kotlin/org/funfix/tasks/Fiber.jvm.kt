@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.funfix.tasks

import org.jetbrains.annotations.Blocking
import org.jetbrains.annotations.NonBlocking
import java.time.Duration
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.AbstractQueuedSynchronizer

public actual interface Fiber<out T> : Cancellable {
    public actual val outcome: Outcome<T>?

    @NonBlocking
    public actual fun joinAsync(onComplete: Runnable): Cancellable

    /**
     * Blocks the current thread until the fiber completes, or until
     * the timeout is reached.
     *
     * This method does not return the outcome of the fiber. To check
     * the outcome, use [outcome].
     *
     * @throws InterruptedException if the current thread is interrupted, which
     * will just stop waiting for the fiber, but will not cancel the running
     * task.
     *
     * @throws TimeoutException if the timeout is reached before the fiber
     * completes.
     */
    @Blocking
    @Throws(InterruptedException::class, TimeoutException::class)
    public fun joinBlockingTimed(timeoutMillis: Long) {
        val latch = AwaitSignal()
        val token = joinAsync { latch.signal() }
        try {
            latch.await(timeoutMillis, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            token.cancel()
            throw e
        } catch (e: TimeoutException) {
            token.cancel()
            throw e
        }
    }

    /**
     * Overload of [joinBlockingTimed] that takes a [Duration] instead of
     * a timeout in milliseconds.
     */
    @Blocking
    @Throws(InterruptedException::class, TimeoutException::class)
    public fun joinBlockingTimed(timeout: Duration): Unit =
        joinBlockingTimed(timeout.toMillis())

    /**
     * Blocks the current thread until the fiber completes.
     *
     * This method does not return the outcome of the fiber. To check
     * the outcome, use [outcome].
     *
     * @throws InterruptedException if the current thread is interrupted, which
     * will just stop waiting for the fiber, but will not cancel the running
     * task.
     */
    @Blocking
    @Throws(InterruptedException::class)
    public fun joinBlocking() {
        val latch = AwaitSignal()
        val token = joinAsync { latch.signal() }
        try {
            latch.await()
        } finally {
            token.cancel()
        }
    }

    /**
     * Returns a [CancellableFuture] that can be used to join the fiber
     * asynchronously.
     */
    @NonBlocking
    public fun joinAsync(): CancellableFuture<Unit> {
        val p = CompletableFuture<Unit>()
        val token = joinAsync { p.complete(Unit) }
        val cRef = Cancellable {
            try {
                token.cancel()
            } finally {
                p.cancel(false)
            }
        }
        return CancellableFuture(p, cRef)
    }

    public companion object {
        /**
         * INTERNAL API, do not use!
         */
        /**
         * INTERNAL API, do not use!
         */
        @NonBlocking
        internal fun <T> start(executor: Executor, asyncFun: AsyncFun<T>): Fiber<T> {
            val fiber = ExecutedFiber<T>()
            try {
                val token = asyncFun(executor, fiber.onComplete)
                fiber.registerCancel(token)
            } catch (e: Throwable) {
                UncaughtExceptionHandler.rethrowIfFatal(e)
                fiber.onComplete.onFailure(e)
            }
            return fiber
        }
    }
}

/**
 * INTERNAL API, do not use!
 */
private class ExecutedFiber<T> : Fiber<T> {
    private val stateRef = AtomicReference<State<T>>(State.start())

    override val outcome: Outcome<T>?
        get() = when (val current = stateRef.get()) {
            is State.Completed -> current.outcome
            else -> null
        }

    override fun joinAsync(onComplete: Runnable): Cancellable {
        while (true) {
            when (val current = stateRef.get()) {
                is State.Active, is State.Cancelled -> {
                    val update = current.addListener(onComplete)
                    if (stateRef.compareAndSet(current, update)) {
                        return removeListenerCancellable(onComplete)
                    }
                }
                is State.Completed -> {
                    TaskExecutors.trampoline.execute(onComplete)
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
                is State.Cancelled, is State.Completed ->
                    return
            }
        }
    }

    val onComplete: CompletionCallback<T> =
        CompletionCallback.OutcomeBased { outcome ->
            while (true) {
                when (val current = stateRef.get())  {
                    is State.Active ->
                        if (stateRef.compareAndSet(current, State.Completed(outcome))) {
                            current.triggerListeners()
                            return@OutcomeBased
                        }
                    is State.Cancelled -> {
                        val update = State.Completed<T>(Outcome.cancellation())
                        if (stateRef.compareAndSet(current, update)) {
                            current.triggerListeners()
                            return@OutcomeBased
                        }
                    }
                    is State.Completed -> {
                        if (outcome is Outcome.Failure) {
                            UncaughtExceptionHandler.logOrRethrow(outcome.exception)
                        }
                        return@OutcomeBased
                    }
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
                    val update = State.Active<T>(current.listeners, token)
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

    sealed interface State<T> {
        data class Active<T>(val listeners: List<Runnable>, val token: Cancellable?) : State<T>
        data class Cancelled<T>(val listeners: List<Runnable>) : State<T>
        data class Completed<T>(val outcome: Outcome<T>) : State<T>

        fun triggerListeners() =
            when (this) {
                is Active -> listeners.forEach(TaskExecutors.trampoline::execute)
                is Cancelled -> listeners.forEach(TaskExecutors.trampoline::execute)
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

/**
 * INTERNAL API, do not use!
 */
private class AwaitSignal : AbstractQueuedSynchronizer() {
    override fun tryAcquireShared(arg: Int): Int = if (state != 0) 1 else -1

    override fun tryReleaseShared(arg: Int): Boolean {
        state = 1
        return true
    }

    fun signal() {
        releaseShared(1)
    }

    @Throws(InterruptedException::class)
    fun await() {
        acquireSharedInterruptibly(1)
    }

    @Throws(InterruptedException::class, TimeoutException::class)
    fun await(timeout: Long, unit: TimeUnit) {
        if (!tryAcquireSharedNanos(1, unit.toNanos(timeout))) {
            throw TimeoutException("Timed out after $timeout ${unit.name.lowercase()}")
        }
    }
}
