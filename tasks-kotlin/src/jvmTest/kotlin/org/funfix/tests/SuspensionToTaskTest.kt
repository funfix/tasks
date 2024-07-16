package org.funfix.tests

import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.yield
import org.funfix.tasks.Task
import org.funfix.tasks.fromSuspended
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals

class SuspensionToTaskTest {
    @Test
    fun `successful test`() {
        val task = Task.fromSuspended {
            yield()
            1 + 1
        }

        val r = awaitBlockingTask(task)
        assertEquals(r, 2)
    }

    @Test
    fun failure() {
        val e = RuntimeException("Boom!")
        val task = Task.fromSuspended {
            yield()
            throw e
        }

        assertThrows<ExecutionException>("java.lang.RuntimeException: Boom!") {
            awaitBlockingTask(task)
        }
    }

    @Test
    fun cancellation() {
        val wasStarted = CountDownLatch(1)
        val hits = AtomicInteger(0)

        val task = Task.fromSuspended {
            try {
                runInterruptible {
                    wasStarted.countDown()
                    try {
                        Thread.sleep(100000)
                    } catch (_: InterruptedException) {
                        hits.incrementAndGet()
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                hits.incrementAndGet()
                throw e
            }
        }

        val fiber = task.executeConcurrently()
        awaitLatchWithExpectation(wasStarted, "wasStarted")
        fiber.cancel()
        awaitFiberJoin(fiber)
        assertEquals(2, hits.get())
    }

    @Test
    fun `executed on the right fiber-executor (synchronous)`() {
        val task = Task.fromSuspended {
            Thread.currentThread().name
        }

        val name = awaitBlockingTask(task)
        assertStartsWith("common-io-", name)
    }

    @Test
    fun `executed on the right fiber-executor (after yield)`() {
        val task = Task.fromSuspended {
            yield()
            Thread.currentThread().name
        }

        val name = awaitBlockingTask(task)
        assertStartsWith("common-io-", name)
    }
}
