@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.funfix.tasks

import org.funfix.tasks.support.Executor
import org.funfix.tasks.support.NonBlocking
import org.funfix.tasks.support.Serializable

public expect abstract class Task<out T>: Serializable {
    protected abstract val unsafeExecuteAsync: AsyncFun<T>

    /**
     * Starts the asynchronous execution of this task.
     *
     * @param callback will be invoked with the result when the task completes
     * @param executor is the [Executor] that may be used to run the task
     *
     * @return a [Cancellable] that can be used to cancel a running task
     */
    @NonBlocking
    public fun executeAsync(executor: Executor, callback: CompletionCallback<T>): Cancellable

    @NonBlocking
    public fun executeAsync(callback: CompletionCallback<T>): Cancellable

    @NonBlocking
    public fun executeFiber(executor: Executor): Fiber<T>

    /**
     * Overload of [executeFiber] that uses [TaskExecutors.global] as the executor.
     */
    @NonBlocking
    public fun executeFiber(): Fiber<T>

    public companion object {
        /**
         * Creates a task from a builder function that, on evaluation, will have
         * same-thread execution.
         *
         * This method ensures:
         * 1. Idempotent cancellation
         * 2. Trampolined execution to avoid stack-overflows
         *
         * The created task will execute the given function on the current
         * thread, by using a "trampoline" to avoid stack overflows. This may be
         * useful if the computation for initiating the async process is
         * expected to be fast. However, if the computation can block the
         * current thread, it is recommended to use [createAsync] instead, which
         * will initiate the computation on a separate thread.
         *
         * @param run is the function that will trigger the async
         * computation, injecting a callback that will be used to signal the
         * result, and an executor that can be used for creating additional
         * threads.
         *
         * @return a new task that will execute the given builder function upon execution
         * @see createAsync
         */
        @NonBlocking
        public fun <T> create(run: AsyncFun<T>): Task<T>
    }
}
