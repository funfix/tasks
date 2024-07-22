@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.funfix.tasks.kotlin

public actual class PlatformTask<T>(
    private val f: (Executor, (Outcome<T>) -> Unit) -> Cancellable
) {
    public operator fun invoke(
        executor: Executor,
        callback: (Outcome<T>) -> Unit
    ): Cancellable =
        f(executor, callback)
}

public actual value class Task<out T> internal actual constructor(
    public actual val asPlatform: PlatformTask<out T>
) {
    public actual companion object
}

public actual fun <T> Task<T>.runAsync(
    executor: Executor?,
    callback: (Outcome<T>) -> Unit
): Cancellable {
    val protected = callback.protect()
    try {
        return asPlatform.invoke(
            executor ?: GlobalExecutor,
            protected
        )
    } catch (e: Throwable) {
        UncaughtExceptionHandler.rethrowIfFatal(e)
        protected(Outcome.failure(e))
        return Cancellable.empty
    }
}

public actual fun <T> fromAsync(start: (Executor, Callback<T>) -> Cancellable): Task<T> =
    Task(PlatformTask { executor, cb ->
        val cRef = MutableCancellable()
        TrampolineExecutor.execute {
            cRef.set(start(executor, cb))
        }
        cRef
    })

public actual fun <T> fromForkedAsync(start: (Executor, Callback<T>) -> Cancellable): Task<T> =
    Task(PlatformTask { executor, cb ->
        val cRef = MutableCancellable()
        executor.execute {
            cRef.set(start(executor, cb))
        }
        cRef
    })

internal fun <T> Callback<T>.protect(): Callback<T> {
    var isWaiting = true
    return { o ->
        if (o is Outcome.Failure) {
            UncaughtExceptionHandler.logOrRethrow(o.exception)
        }
        if (isWaiting) {
            isWaiting = false
            TrampolineExecutor.execute {
                this@protect.invoke(o)
            }
        }
    }
}
