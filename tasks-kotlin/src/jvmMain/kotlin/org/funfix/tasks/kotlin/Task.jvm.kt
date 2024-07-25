@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
@file:JvmName("TaskJvmKt")

package org.funfix.tasks.kotlin

import org.jetbrains.annotations.Blocking
import org.jetbrains.annotations.NonBlocking
import java.util.concurrent.CompletionStage
import java.util.concurrent.Future
import java.util.concurrent.TimeoutException
import kotlin.jvm.Throws
import kotlin.time.Duration
import kotlin.time.toJavaDuration

public actual typealias PlatformTask<T> = org.funfix.tasks.jvm.Task<T>

@JvmInline
public actual value class Task<out T> public actual constructor(
    public actual val asPlatform: PlatformTask<out T>
) {
    /**
     * Converts this task to a `jvm.Task`.
     */
    public val asJava: PlatformTask<out T> get() = asPlatform

    public actual companion object
}

@NonBlocking
public actual fun <T> Task<T>.ensureRunningOnExecutor(executor: Executor?): Task<T> =
    Task(when (executor) {
        null -> asPlatform.ensureRunningOnExecutor()
        else -> asPlatform.ensureRunningOnExecutor(executor)
    })

@NonBlocking
public actual fun <T> Task<T>.runAsync(
    executor: Executor?,
    callback: Callback<T>
): Cancellable =
    when (executor) {
        null -> asPlatform.runAsync(callback.asJava())
        else -> asPlatform.runAsync(executor, callback.asJava())
    }

@NonBlocking
public actual fun <T> Task<T>.runFiber(executor: Executor?): Fiber<T> =
    Fiber(when (executor) {
        null -> asPlatform.runFiber()
        else -> asPlatform.runFiber(executor)
    })

/**
 * Executes the task and blocks until it completes, or the current thread gets
 * interrupted (in which case the task is also cancelled).
 *
 * Given that the intention is to block the current thread for the result, the
 * task starts execution on the current thread.
 *
 * @param executor the [Executor] that may be used to run the task.
 * @return the successful result of the task.
 *
 * @throws InterruptedException if the current thread is interrupted, which also
 * cancels the running task. Note that on interruption, the running concurrent
 * task must also be interrupted, as this method always blocks for its
 * interruption or completion.
 *
 * @throws Throwable if the task fails with an exception
 */
@Blocking
@Throws(InterruptedException::class)
public fun <T> Task<T>.runBlocking(executor: Executor? = null): T =
    when (executor) {
        null -> asPlatform.runBlocking()
        else -> asPlatform.runBlocking(executor)
    }

/**
 * Executes the task and blocks until it completes, the timeout is reached, or
 * the current thread is interrupted.
 *
 * **EXECUTION MODEL:** Execution starts on a different thread, by necessity,
 * otherwise the execution could block the current thread indefinitely, without
 * the possibility of interrupting the task after the timeout occurs.
 *
 * @param timeout the maximum time to wait for the task to complete before
 * throwing a [TimeoutException].
 *
 * @param executor the [Executor] that may be used to run the task. If one isn't
 * provided, the execution will use [BlockingIOExecutor] as the default.
 *
 * @return the successful result of the task.
 *
 * @throws InterruptedException if the current thread is interrupted. The
 * running task is also cancelled, and this method does not return until
 * `onCancel` is signaled.
 *
 * @throws TimeoutException if the task doesn't complete within the specified
 * timeout. The running task is also cancelled on timeout, and this method does
 * not return until `onCancel` is signaled.
 *
 * @throws Throwable if the task fails with an exception.
 */
@Blocking
@Throws(InterruptedException::class, TimeoutException::class)
public fun <T> Task<T>.runBlockingTimed(timeout: Duration, executor: Executor? = null): T =
    when (executor) {
        null -> asPlatform.runBlockingTimed(timeout.toJavaDuration())
        else -> asPlatform.runBlockingTimed(executor, timeout.toJavaDuration())
    }

// Builders

@NonBlocking
public actual fun <T> Task.Companion.fromAsync(
    start: (Executor, Callback<T>) -> Cancellable
): Task<T> =
    Task(PlatformTask.fromAsync { executor, cb ->
        start(executor, cb.asKotlin())
    })

/**
 * Creates a task from a function executing blocking I/O.
 *
 * This uses Java's interruption protocol (i.e., [Thread.interrupt]) for
 * cancelling the task.
 */
@NonBlocking
public fun <T> Task.Companion.fromBlockingIO(block: () -> T): Task<T> =
    Task(PlatformTask.fromBlockingIO(block))

/**
 * Creates a task from a [Future] builder.
 *
 * This is compatible with Java's interruption protocol and [Future.cancel],
 * with the resulting task being cancellable.
 *
 * **NOTE:** Use [fromCompletionStage] for directly converting
 * [java.util.concurrent.CompletableFuture] builders, because it is not possible
 * to cancel such values, and the logic needs to reflect it. Better yet, use
 * [fromCancellableFuture] for working with [CompletionStage] values that can be
 * cancelled.
 *
 * @param builder is the function that will create the [Future] upon this task's
 * execution.
 *
 * @return a new task that will complete with the result of the created `Future`
 * upon execution
 *
 * @see fromCompletionStage
 * @see fromCancellableFuture
 */
@NonBlocking
public fun <T> Task.Companion.fromBlockingFuture(builder: () -> Future<out T>): Task<T> =
    Task(PlatformTask.fromBlockingFuture(builder))

/**
 * Creates tasks from a builder of [CompletionStage].
 *
 * **NOTE:** `CompletionStage` isn't cancellable, and the resulting task should
 * reflect this (i.e., on cancellation, the listener should not receive an
 * `onCancel` signal until the `CompletionStage` actually completes).
 *
 * Prefer using [fromCancellableFuture] for working with [CompletionStage]
 * values that can be cancelled.
 *
 * @param builder is the function that will create the [CompletionStage]
 *                value. It's a builder because `Task` values are cold values
 *                (lazy, not executed yet).
 *
 * @return a new task that upon execution will complete with the result of
 * the created `CancellableCompletionStage`
 *
 * @see fromCancellableFuture
 */
@NonBlocking
public fun <T> Task.Companion.fromCompletionStage(builder: () -> CompletionStage<out T>): Task<T> =
    Task(PlatformTask.fromCompletionStage(builder))

/**
 * Creates tasks from a builder of [CancellableFuture].
 *
 * This is the recommended way to work with [CompletionStage] builders, because
 * cancelling such values (e.g., [java.util.concurrent.CompletableFuture])
 * doesn't work for cancelling the connecting computation. As such, the user
 * should provide an explicit [Cancellable] token that can be used.
 *
 * @param builder the function that will create the [CancellableFuture] value.
 * It's a builder because [Task] values are cold values (lazy, not executed
 * yet).
 *
 * @return a new task that upon execution will complete with the result of the
 * created [CancellableFuture]
 */
@NonBlocking
public fun <T> Task.Companion.fromCancellableFuture(builder: () -> CancellableFuture<out T>): Task<T> =
    Task(PlatformTask.fromCancellableFuture(builder))
