package org.funfix.tests

import kotlinx.coroutines.Job
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeout
import org.funfix.tasks.Fiber
import org.funfix.tasks.Task
import org.jetbrains.annotations.Blocking
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeoutException
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

val TIMEOUT = 5.seconds

@Blocking
fun awaitLatchWithExpectation(latch: CountDownLatch, name: String = "latch", timeout: Duration = TIMEOUT) =
    assertTrue(
        latch.await(timeout.inWholeMilliseconds, java.util.concurrent.TimeUnit.MILLISECONDS),
        "`$name` did not complete in time"
    )

suspend fun awaitLatchWithExpectationSuspended(latch: CountDownLatch, name: String = "latch", timeout: Duration = TIMEOUT) =
    runInterruptible {
        awaitLatchWithExpectation(latch, name, timeout)
    }

fun <T> awaitFiberJoin(fiber: Fiber<T>, name: String = "fiber") =
    try { fiber.joinBlockingTimed(TIMEOUT.inWholeMilliseconds) }
    catch (_: TimeoutException) { fail("Timed out waiting for `$name` to complete") }

suspend fun awaitJoinWithTimeoutSuspended(job: Job, timeout: Duration = TIMEOUT) =
    withTimeout(timeout) { job.join() }

fun assertStartsWith(expectedPrefix: String, actual: String) {
    assertTrue(
        actual.startsWith(expectedPrefix),
        "Expected `$actual` to start with `$expectedPrefix`"
    )
}

fun <T> awaitBlockingTask(task: Task<T>, name: String = "task", timeout: Duration = TIMEOUT): T =
    try {
        task.executeBlockingTimed(timeout.inWholeMilliseconds)
    } catch (_: TimeoutException) {
        fail("Timed out waiting for `$name` to complete")
    }
