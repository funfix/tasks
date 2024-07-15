package org.funfix.tasks

import java.io.Serializable

/**
 * A function that is a delayed, asynchronous computation.
 */
fun interface AsyncFun<out T> : Serializable {
    operator fun invoke(
        executor: FiberExecutor,
        callback: CompletionCallback<T>
    ): Cancellable
}
