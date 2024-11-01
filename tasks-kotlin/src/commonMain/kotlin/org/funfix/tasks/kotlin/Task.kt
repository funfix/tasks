@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.funfix.tasks.kotlin

/**
 * An alias for a platform-specific implementation that powers [Task].
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
 *
 * This is designed to be a compile-time type that's going to be erased at
 * runtime. Therefore, for the JVM at least, when using it in your APIs, it
 * won't pollute it with Kotlin-specific wrappers.
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
public fun <T> PlatformTask<out T>.asKotlin(): Task<T> =
    Task(this)

/**
 * Ensures that the task starts asynchronously and runs on the given executor,
 * regardless of the `run` method that is used, or the injected executor in
 * any of those methods.
 *
 * One example where this is useful is for blocking I/O operations, for
 * ensuring that the task runs on the thread-pool meant for blocking I/O,
 * regardless of what executor is passed to [runAsync].
 *
 * Example:
 * ```kotlin
 * Task.fromBlockingIO {
 *     // Reads a file from disk
 *     Files.readString(Paths.get("file.txt"))
 * }.ensureRunningOnExecutor(
 *     BlockingIOExecutor
 * )
 * ```
 *
 * Another use-case is for ensuring that the task runs asynchronously, on
 * another thread. Otherwise, tasks may be able to execute on the current thread:
 *
 * ```kotlin
 * val task = Task.fromBlockingIO {
 *    // Reads a file from disk
 *    Files.readString(Paths.get("file.txt"))
 * }
 *
 * task
 *   // Ensuring the task runs on a different thread
 *   .ensureRunningOnExecutor()
 *   // Blocking the current thread for the result (JVM API)
 *   .runBlocking()
 * ```
 *
 * @param executor is the [Executor] used as an override. If `null`, then
 * the executor injected (e.g., in [runAsync]) will be used.
 */
public expect fun <T> Task<T>.ensureRunningOnExecutor(executor: Executor? = null): Task<T>

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
 * Executes the task concurrently and returns a [Fiber] that can be
 * used to wait for the result or cancel the task.
 *
 * Similar to [runAsync], this method starts the execution on a different thread.
 *
 * @param executor is the [Executor] that may be used to run the task.
 *
 * @return a [Fiber] that can be used to wait for the outcome,
 * or to cancel the running fiber.
 */
public expect fun <T> Task<T>.runFiber(executor: Executor? = null): Fiber<T>

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
public expect fun <T> Task.Companion.fromAsync(
    start: (Executor, Callback<T>) -> Cancellable
): Task<T>
