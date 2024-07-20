package org.funfix.tasks.kotlin

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import org.funfix.tasks.jvm.Cancellable
import org.funfix.tasks.jvm.CompletionCallback
import org.funfix.tasks.jvm.TaskCancellationException
import org.funfix.tasks.jvm.UncaughtExceptionHandler
import java.util.concurrent.Executor
import kotlin.coroutines.cancellation.CancellationException

@JvmInline
public value class Task<out T>(
    private val self: org.funfix.tasks.jvm.Task<out T>
) {
    public fun asJava(): JvmTask<out T> = self

    public suspend fun execute(executor: Executor? = null): T =
        self.executeCoroutine(executor)

    public companion object {
        public fun <T> create(f: (Executor, CompletionCallback<in T>) -> Cancellable): Task<T> =
            Task(org.funfix.tasks.jvm.Task.create(f))

        /**
         * Converts a suspended function into a [Task].
         *
         * NOTES:
         * - The [kotlinx.coroutines.CoroutineDispatcher], made available via the
         *   "coroutine context", will be created from the `Executor` injected
         *   by the [Task] implementation.
         * - The [Task] cancellation protocol cooperates with that of the coroutine,
         *   so cancelling the task will also cleanly cancel the coroutine
         *   (including the possibility for back-pressuring on the coroutine's completion
         *   after cancellation).
         */
        @OptIn(DelicateCoroutinesApi::class)
        public fun <T> fromSuspended(block: suspend () -> T): Task<T> =
            create { executor, callback ->
                val context = executor.asCoroutineDispatcher()
                val job = GlobalScope.launch(context) {
                    try {
                        val r = block()
                        callback.onSuccess(r)
                    } catch (e: Throwable) {
                        UncaughtExceptionHandler.rethrowIfFatal(e)
                        when (e) {
                            is CancellationException,
                            is TaskCancellationException,
                            is InterruptedException ->
                                callback.onCancellation()
                            else ->
                                callback.onFailure(e)
                        }
                    }
                }
                Cancellable {
                    job.cancel()
                }
            }
    }
}
