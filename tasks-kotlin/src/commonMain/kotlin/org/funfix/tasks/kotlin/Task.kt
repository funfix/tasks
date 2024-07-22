@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.funfix.tasks.kotlin

public expect class PlatformTask<T> {}

public expect value class Task<out T> internal constructor(private val self: PlatformTask<out T>) {
    public suspend fun await(executor: Executor): T
    public suspend fun await(): T

    public fun runAsync(executor: Executor, callback: (Outcome<T>) -> Unit): Cancellable
    public fun runAsync(callback: (Outcome<T>) -> Unit): Cancellable

    public companion object {
        public fun <T> create(f: (Executor, (Outcome<T>) -> Unit) -> Cancellable): Task<T>
        public fun <T> fromSuspended(block: suspend () -> T): Task<T>
    }
}
