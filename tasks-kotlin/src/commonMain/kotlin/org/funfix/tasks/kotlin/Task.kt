@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.funfix.tasks.kotlin

/**
 * Definition that provides the platform-specific implementation of a task.
 * I.e., on the JVM, this is `org.funfix.tasks.jvm.Task`.
 */
public expect class PlatformTask<T>

/**
 * Kotlin-specific callback type used for signaling the completion of running
 * tasks.
 */
public typealias Callback<T> = (Outcome<T>) -> Unit

/**
 * A task is a computation that can be executed asynchronously.
 *
 * In the vocabulary of "reactive streams", this is a "cold data source",
 * meaning that the computation hasn't executed yet, and when it will execute,
 * the result won't get cached (memoized). In the vocabulary of
 * "functional programming", this is a pure value, being somewhat equivalent
 * to `IO`.
 */
public expect value class Task<out T> public constructor(
    public val asPlatform: PlatformTask<out T>
) {
    // Companion object currently doesn't do anything, but we
    // need to define one to make the class open for extensions.
    public companion object
}

/**
 * Converts a platform task to a Kotlin task.
 *
 * E.g., can convert a `jvm.Task` to a `kotlin.Task`.
 */
public fun <T> PlatformTask<T>.asKotlin(): Task<T> =
    Task(this)

/**
 * Executes the task asynchronously.
 *
 * @param executor is the [Executor] to use for running the task
 * @param callback is the callback given for signaling completion
 * @return a [Cancellable] that can be used to cancel the running task
 */
public expect fun <T> Task<T>.runAsync(
    executor: Executor? = null,
    callback: Callback<T>
): Cancellable

/**
 * Creates a task from an asynchronous computation, initiated on the current thread.
 *
 * This method ensures:
 * 1. Idempotent cancellation
 * 2. Trampolined execution to avoid stack-overflows
 *
 * The created task will execute the given function on the current
 * thread, by using a "trampoline" to avoid stack overflows. This may
 * be useful if the computation for initiating the async process is
 * expected to be fast. However, if the computation can block the
 * current thread, it is recommended to use [fromForkedAsync] instead,
 * which will initiate the computation by yielding first (i.e., on the
 * JVM this means the execution will start on a different thread).
 *
 * @param start is the function that will trigger the async computation,
 * injecting a callback that will be used to signal the result, and an
 * executor that can be used for creating additional threads.
 *
 * @return a new task that will execute the given builder function upon execution
 * @see fromForkedAsync
 */
public expect fun <T> Task.Companion.fromAsync(start: (Executor, Callback<T>) -> Cancellable): Task<T>
