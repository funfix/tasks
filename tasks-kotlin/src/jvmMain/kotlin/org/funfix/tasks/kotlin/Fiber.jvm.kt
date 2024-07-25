@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
@file:JvmName("FiberJvmKt")

package org.funfix.tasks.kotlin

import org.jetbrains.annotations.Blocking
import org.jetbrains.annotations.NonBlocking
import java.util.concurrent.TimeoutException
import kotlin.jvm.Throws
import kotlin.time.Duration
import kotlin.time.toJavaDuration

public actual typealias PlatformFiber<T> = org.funfix.tasks.jvm.Fiber<T>

@JvmInline
public actual value class Fiber<out T> public actual constructor(
    public actual val asPlatform: PlatformFiber<out T>
)

@NonBlocking
@Throws(TaskCancellationException::class, FiberNotCompletedException::class)
public actual fun <T> Fiber<T>.resultOrThrow(): T =
    asPlatform.resultOrThrow

@NonBlocking
public actual fun <T> Fiber<T>.joinAsync(onComplete: Runnable): Cancellable =
    asPlatform.joinAsync(onComplete)

@NonBlocking
public actual fun <T> Fiber<T>.cancel(): Unit =
    asPlatform.cancel()

@NonBlocking
public actual fun <T> Fiber<T>.awaitAsync(callback: Callback<T>): Cancellable =
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
    asPlatform.awaitBlocking()

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
    asPlatform.awaitBlockingTimed(timeout.toJavaDuration())

