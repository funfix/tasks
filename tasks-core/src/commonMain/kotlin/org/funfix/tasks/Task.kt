@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.funfix.tasks

import org.funfix.tasks.support.NonBlocking
import org.funfix.tasks.support.Serializable

public expect abstract class Task<out T>: Serializable {
    protected abstract val unsafeExecuteAsync: AsyncFun<T>

    /**
     * Starts the asynchronous execution of this task.
     *
     * @param callback will be invoked with the result when the task completes
     * @param executor is the [FiberExecutor] that may be used to run the task
     *
     * @return a [Cancellable] that can be used to cancel a running task
     */
    @NonBlocking
    public fun executeAsync(executor: FiberExecutor, callback: CompletionCallback<T>): Cancellable

    @NonBlocking
    public fun executeAsync(callback: CompletionCallback<T>): Cancellable

    @NonBlocking
    public fun executeFiber(executor: FiberExecutor): TaskFiber<T>

    /**
     * Overload of [executeFiber] that uses [FiberExecutor.global] as the executor.
     */
    @NonBlocking
    public fun executeFiber(): TaskFiber<T>
}
