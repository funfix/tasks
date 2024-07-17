package org.funfix.tasks

import org.jetbrains.annotations.NonBlocking
import java.io.Serializable

/**
 * A function that is a delayed, asynchronous computation.
 */
public fun interface AsyncFun<out T> : Serializable {
    @NonBlocking
    public operator fun invoke(
        executor: FiberExecutor,
        callback: CompletionCallback<T>
    ): Cancellable
}
