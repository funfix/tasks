package org.funfix.tasks

import org.jetbrains.annotations.BlockingExecutor
import org.jetbrains.annotations.NonBlocking
import java.io.Serializable

/**
 * A function that is a delayed, asynchronous computation.
 */
fun interface AsyncFun<out T> : Serializable {
    @NonBlocking
    operator fun invoke(
        executor: FiberExecutor,
        callback: CompletionCallback<T>
    ): Cancellable
}
