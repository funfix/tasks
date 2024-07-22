@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.funfix.tasks.kotlin

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import java.util.concurrent.CompletionStage
import java.util.concurrent.Executor
import java.util.concurrent.Future
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.toJavaDuration

@JvmInline
public actual value class Task<out T> internal constructor(
    private val self: PlatformTask<out T>
) {
    public val asJava: PlatformTask<out T>
        get() = self

    public actual suspend fun await(): T =
        self.executeSuspended()

    public actual suspend fun await(executor: Executor): T =
        self.executeSuspended(executor)

    public fun runBlocking(executor: Executor): T =
        self.executeBlocking(executor)

    public fun runBlocking(): T =
        self.executeBlocking()

    public fun runBlockingTimed(executor: Executor, timeout: Duration): T =
        self.executeBlockingTimed(executor, timeout.toJavaDuration())

    public fun runBlockingTimed(timeout: Duration): T =
        self.executeBlockingTimed(timeout.toJavaDuration())

    public actual fun runAsync(executor: Executor, callback: (Outcome<T>) -> Unit): Cancellable =
        self.executeAsync(executor, callback.asJava())

    public actual fun runAsync(callback: (Outcome<T>) -> Unit): Cancellable =
        self.executeAsync(callback.asJava())

    public actual companion object {
        public actual fun <T> create(f: (Executor, (Outcome<T>) -> Unit) -> Cancellable): Task<T> =
            Task(org.funfix.tasks.jvm.Task.create { executor, cb -> f(executor, cb.asKotlin()) })

        public fun <T> createAsync(f: (Executor, (Outcome<T>) -> Unit) -> Cancellable): Task<T> =
            Task(org.funfix.tasks.jvm.Task.createAsync { executor, cb ->
                f(executor, cb.asKotlin())
            })

        public fun <T> fromBlockingIO(block: () -> T): Task<T> =
            Task(org.funfix.tasks.jvm.Task.fromBlockingIO(block))

        public fun <T> fromBlockingFuture(block: () -> Future<out T>): Task<T> =
            Task(org.funfix.tasks.jvm.Task.fromBlockingFuture(block))

        public fun <T> fromCompletionStage(block: () -> CompletionStage<out T>): Task<T> =
            Task(org.funfix.tasks.jvm.Task.fromCompletionStage(block))

        public fun <T> fromCancellableFuture(block: () -> CancellableFuture<out T>): Task<T> =
            Task(org.funfix.tasks.jvm.Task.fromCancellableFuture(block))

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
        public actual fun <T> fromSuspended(block: suspend () -> T): Task<T> =
            Task(PlatformTask.create { executor, callback ->
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
            })
    }
}

internal fun <T> ((Outcome<T>) -> Unit).asJava(): CompletionCallback<T> =
    object : CompletionCallback<T> {
        override fun onSuccess(value: T): Unit = this@asJava(Outcome.Success(value))
        override fun onFailure(e: Throwable): Unit = this@asJava(Outcome.Failure(e))
        override fun onCancellation(): Unit = this@asJava(Outcome.Cancellation)
    }

internal fun <T> CompletionCallback<in T>.asKotlin(): (Outcome<T>) -> Unit = { outcome ->
    when (outcome) {
        is Outcome.Success -> onSuccess(outcome.value)
        is Outcome.Failure -> onFailure(outcome.exception)
        is Outcome.Cancellation -> onCancellation()
    }
}
