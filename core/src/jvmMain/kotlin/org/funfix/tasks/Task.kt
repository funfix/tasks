package org.funfix.tasks

import java.util.concurrent.Callable
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.AbstractQueuedSynchronizer

@FunctionalInterface
interface Task<out T> {
    fun executeAsync(onComplete: CompletionHandler<T>): Cancellable

    /**
     * Executes the task and blocks until it completes.
     *
     * @throws ExecutionException if the task fails with an exception
     *
     * @throws InterruptedException if the current thread is interrupted,
     *         which also cancels the running task. Note that on interruption,
     *         the running concurrent task must also be interrupted, as this method
     *         always blocks for its interruption or completion.
     */
    @Throws(ExecutionException::class, InterruptedException::class)
    fun executeBlocking(): T {
        val h = BlockingCompletionHandler<T>()
        val cancelToken = executeAsync(h)
        return h.await(cancelToken)
    }

    companion object {
        @JvmStatic fun <T> fromFuture(builder: Callable<CompletableFuture<T>>): Task<T> =
            TaskFromFuture(builder)
    }
}

private class BlockingCompletionHandler<T>: AbstractQueuedSynchronizer(), CompletionHandler<T> {
    private val isDone = AtomicBoolean(false)
    private var result: T? = null
    private var error: Throwable? = null
    private var interrupted: InterruptedException? = null

    override fun trySuccess(value: T): Boolean {
        if (!isDone.getAndSet(true)) {
            result = value
            releaseShared(1)
            return true
        }
        return false
    }

    override fun tryFailure(e: Throwable): Boolean {
        if (!isDone.getAndSet(true)) {
            error = e
            releaseShared(1)
            return true
        }
        return false
    }

    override fun tryCancel(): Boolean {
        if (!isDone.getAndSet(true)) {
            interrupted = InterruptedException("Task was cancelled")
            releaseShared(1)
            return true
        }
        return false
    }

    override fun tryAcquireShared(arg: Int): Int =
        if (state != 0) 1 else -1

    override fun tryReleaseShared(arg: Int): Boolean {
        state = 1
        return true
    }

    @Throws(InterruptedException::class, ExecutionException::class)
    fun await(cancelToken: Cancellable): T {
        while (true) {
            try {
                acquireSharedInterruptibly(1)
                break
            } catch (e: InterruptedException) {
                if (interrupted == null) {
                    // Tries to cancel the task
                    cancelToken.cancel()
                    interrupted = e
                }
                // Clearing the interrupted flag may not be necessary,
                // but doesn't hurt, and we should have a cleared flag before
                // re-throwing the exception
                Thread.interrupted()
            }
        }
        if (interrupted != null) throw interrupted!!
        if (error != null) throw ExecutionException(error)
        return result!!
    }
}

private class TaskFromFuture<T>(
    private val builder: Callable<CompletableFuture<T>>
): Task<T> {
    override fun executeAsync(onComplete: CompletionHandler<T>): Cancellable {
        var future: CompletableFuture<T>? = null
        var userError = true
        try {
            future = builder.call()
            userError = false

            future.whenComplete { value, error ->
                when {
                    error is InterruptedException -> onComplete.cancel()
                    error is CancellationException -> onComplete.cancel()
                    error != null -> onComplete.failure(error)
                    else -> onComplete.success(value)
                }
            }
        } catch (e: Exception) {
            if (userError) onComplete.failure(e)
            else throw e
        }

        if (future == null) {
            return Cancellable.EMPTY
        }

        return object : Cancellable {
            override fun cancel() {
                future.cancel(true)
            }
        }
    }
}
