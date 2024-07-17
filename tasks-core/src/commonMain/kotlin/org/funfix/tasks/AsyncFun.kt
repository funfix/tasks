
package org.funfix.tasks

import org.funfix.tasks.support.Executor
import org.funfix.tasks.support.NonBlocking
import org.funfix.tasks.support.Serializable

/**
 * A function that is a delayed, asynchronous computation.
 */
public fun interface AsyncFun<out T> : Serializable {
    @NonBlocking
    public operator fun invoke(
        executor: Executor,
        callback: CompletionCallback<T>
    ): Cancellable
}
