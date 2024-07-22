@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.funfix.tasks.kotlin

public expect class PlatformTask<T> {}

public expect value class Task<out T> internal constructor(private val self: PlatformTask<out T>) {
    public suspend fun await(executor: Executor? = null): T
    public fun runAsync(executor: Executor? = null, callback: (Outcome<T>) -> Unit): Cancellable

    public companion object {
        public fun <T> create(f: (Executor, (Outcome<T>) -> Unit) -> Cancellable): Task<T>
        public fun <T> fromSuspended(block: suspend () -> T): Task<T>
    }
}
