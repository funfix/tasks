@file:JvmName("FiberJvmKt")

package org.funfix.tasks.kotlin

import org.jetbrains.annotations.Blocking
import org.jetbrains.annotations.NonBlocking
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeoutException
import kotlin.jvm.Throws
import kotlin.time.Duration
import kotlin.time.toJavaDuration

public typealias PlatformFiber<T> = org.funfix.tasks.jvm.Fiber<T>

/**
 * A fiber is a running task being executed concurrently, and that can be
 * joined/awaited or cancelled.
 *
 * This is the equivalent of Kotlin's `Deferred` type.
 *
 * This is designed to be a compile-time type that's going to be erased at
 * runtime. Therefore, for the JVM at least, when using it in your APIs, it
 * won't pollute it with Kotlin-specific wrappers.
 */
@JvmInline
public value class Fiber<out T> public constructor(
    public val asPlatform: PlatformFiber<out T>
): Cancellable {
    /**
     * Cancels the fiber, which will eventually stop the running fiber (if
     * it's still running), completing it via "cancellation".
     *
     * This manifests either in a [TaskCancellationException] being thrown by
     * [resultOrThrow], or in the completion callback being triggered.
     */
    @NonBlocking
    override fun cancel(): Unit = asPlatform.cancel()
}

/**
 * Converts the source to a [Kotlin Fiber][Fiber].
 *
 * E.g., can convert from a `jvm.Fiber` to a `kotlin.Fiber`.
 */
public fun <T> PlatformFiber<out T>.asKotlin(): Fiber<T> =
    Fiber(this)

/**
 * Returns the result of the completed fiber.
 *
 * This method does not block for the result. In case the fiber is not
 * completed, it throws [FiberNotCompletedException]. Therefore, by contract,
 * it should be called only after the fiber was "joined".
 *
 * @return the result of the concurrent task, if successful.
 * @throws TaskCancellationException if the task was cancelled concurrently,
 * thus being completed via cancellation.
 * @throws FiberNotCompletedException if the fiber is not completed yet.
 * @throws Throwable if the task finished with an exception.
 */
public val <T> Fiber<T>.resultOrThrow: T
    @NonBlocking
    @Throws(TaskCancellationException::class, FiberNotCompletedException::class)
    get() = asPlatform.resultOrThrow

/**
 * Returns the [Outcome] of the completed fiber, or `null` in case the
 * fiber is not completed yet.
 *
 * This method does not block for the result. In case the fiber is not
 * completed, it returns `null`. Therefore, it should be called after
 * the fiber was "joined".
 */
public val <T> Fiber<T>.outcomeOrNull: Outcome<T>? get() =
    try {
        Outcome.Success(asPlatform.resultOrThrow)
    } catch (e: TaskCancellationException) {
        Outcome.Cancellation
    } catch (e: ExecutionException) {
        Outcome.Failure(e.cause ?: e)
    } catch (e: Throwable) {
        UncaughtExceptionHandler.rethrowIfFatal(e)
        Outcome.Failure(e)
    }

/**
 * Waits until the fiber completes, and then runs the given callback to
 * signal its completion.
 *
 * Completion includes cancellation. Triggering [Fiber.cancel] before
 * [joinAsync] will cause the fiber to get cancelled, and then the
 * "join" back-pressures on cancellation.
 *
 * @param onComplete is the callback to run when the fiber completes
 *                   (successfully, or with failure, or cancellation)
 */
@NonBlocking
public fun <T> Fiber<T>.joinAsync(onComplete: Runnable): Cancellable =
    asPlatform.joinAsync(onComplete)

/**
 * Waits until the fiber completes, and then runs the given callback
 * to signal its completion.
 *
 * This method can be executed as many times as necessary, with the
 * result of the `Fiber` being memoized. It can also be executed
 * after the fiber has completed, in which case the callback will be
 * executed immediately.
 *
 * @param callback will be called with the result when the fiber completes.
 *
 * @return a [Cancellable] that can be used to unregister the callback,
 * in case the caller is no longer interested in the result. Note this
 * does not cancel the fiber itself.
 */
@NonBlocking
public fun <T> Fiber<T>.awaitAsync(callback: Callback<T>): Cancellable =
    asPlatform.awaitAsync(callback.asJava())

/**
 * Blocks the current thread until the fiber completes.
 *
 * This method does not return the outcome of the fiber. To check
 * the outcome, use [resultOrThrow].
 *
 * @throws InterruptedException if the current thread is interrupted, which
 * will just stop waiting for the fiber, but will not cancel the running
 * task.
 */
@Blocking
@Throws(InterruptedException::class)
public fun <T> Fiber<T>.joinBlocking(): Unit =
    asPlatform.joinBlocking()

/**
 * Blocks the current thread until the fiber completes, then returns the
 * result of the fiber.
 *
 * @throws InterruptedException if the current thread is interrupted, which
 * will just stop waiting for the fiber, but will not cancel the running task.
 *
 * @throws TaskCancellationException if the fiber was cancelled concurrently.
 *
 * @throws Throwable if the task failed with an exception.
 */
@Blocking
@Throws(InterruptedException::class, TaskCancellationException::class)
public fun <T> Fiber<T>.awaitBlocking(): T =
    try {
        asPlatform.awaitBlocking()
    } catch (e: ExecutionException) {
        throw e.cause ?: e
    }

/**
 * Blocks the current thread until the fiber completes, or until the
 * timeout is reached.
 *
 * This method does not return the outcome of the fiber. To check the
 * outcome, use [resultOrThrow].
 *
 * @throws InterruptedException if the current thread is interrupted, which
 * will just stop waiting for the fiber, but will not cancel the running
 * task.
 *
 * @throws TimeoutException if the timeout is reached before the fiber
 * completes.
 */
@Blocking
@Throws(InterruptedException::class, TimeoutException::class)
public fun <T> Fiber<T>.joinBlockingTimed(timeout: Duration): Unit =
    asPlatform.joinBlockingTimed(timeout.toJavaDuration())

/**
 * Blocks the current thread until the fiber completes, then returns the result of the fiber.
 *
 * @param timeout the maximum time to wait for the fiber to complete, before
 * throwing a [TimeoutException].
 *
 * @return the result of the fiber, if successful.
 *
 * @throws InterruptedException if the current thread is interrupted, which
 * will just stop waiting for the fiber, but will not cancel the running
 * task.
 * @throws TimeoutException if the timeout is reached before the fiber completes.
 * @throws TaskCancellationException if the fiber was cancelled concurrently.
 * @throws Throwable if the task failed with an exception.
 */
@Blocking
@Throws(InterruptedException::class, TaskCancellationException::class, TimeoutException::class)
public fun <T> Fiber<T>.awaitBlockingTimed(timeout: Duration): T =
    try {
        asPlatform.awaitBlockingTimed(timeout.toJavaDuration())
    } catch (e: ExecutionException) {
        throw e.cause ?: e
    }

