package org.funfix.tasks

import java.util.concurrent.Callable
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.function.Supplier

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
        executeAsync(h)
        return h.await()
    }

    companion object {
        @JvmStatic fun <T> fromFuture(builder: Callable<CompletableFuture<T>>): Task<T> =
            object : Task<T> {
                override fun executeAsync(onComplete: CompletionHandler<T>): Cancellable {
                    var future: CompletableFuture<T>? = null
                    var userError = true
                    try {
                        future = builder.call()
                        userError = false

                        future.whenComplete { value, error ->
                            when {
                                error is InterruptedException -> onComplete.onCancellation()
                                error is CancellationException -> onComplete.onCancellation()
                                error != null -> onComplete.onException(error)
                                else -> onComplete.onSuccess(value)
                            }
                        }
                    } catch (e: Exception) {
                        if (userError) onComplete.onException(e)
                        else throw e
                    }

                    if (future == null) {
                        return Cancellable.EMPTY
                    }

                    return object : Cancellable {
                        override fun cancel() {
                            future.cancel(true)
                            onComplete.onCancellation()
                        }
                    }
                }
            }
    }
}

private class BlockingCompletionHandler<T> : CompletionHandler<T> {
    private val latch = java.util.concurrent.CountDownLatch(1)
    private var result: T? = null
    private var error: Throwable? = null
    private var interrupted: InterruptedException? = null
    private var isDone = false

    override fun onSuccess(value: T) {
        result = value
        isDone = true
        latch.countDown()
    }

    override fun onException(e: Throwable) {
        error = e
        isDone = true
        latch.countDown()
    }

    override fun onCancellation() {
        interrupted = interrupted ?: InterruptedException("Task was cancelled")
        isDone = true
        latch.countDown()
    }

    @Throws(InterruptedException::class, ExecutionException::class)
    fun await(): T {
        while (!isDone) {
            try {
                latch.await()
            } catch (e: InterruptedException) {
                interrupted = e
                Thread.interrupted()
            }
        }

        if (interrupted != null) throw interrupted!!
        if (error != null) throw ExecutionException(error)
        return result!!
    }
}
