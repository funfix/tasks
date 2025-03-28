package org.funfix.tests

import org.funfix.tasks.kotlin.Cancellable
import org.funfix.tasks.kotlin.Outcome
import org.funfix.tasks.kotlin.Task
import org.funfix.tasks.kotlin.fromAsync
import org.funfix.tasks.kotlin.joinBlocking
import org.funfix.tasks.kotlin.outcomeOrNull
import org.funfix.tasks.kotlin.runBlocking
import org.funfix.tasks.kotlin.runFiber
import java.util.concurrent.CountDownLatch
import kotlin.test.Test
import kotlin.test.assertEquals

class TaskFromAsyncTest {
    @Test
    fun `fromAsync happy path`() {
        val task = Task.fromAsync { _, cb ->
            cb(Outcome.success(1))
            Cancellable.getEmpty()
        }
        assertEquals(1, task.runBlocking())
    }

    @Test
    fun `fromAsync with error`() {
        val ex = RuntimeException("Boom!")
        val task = Task.fromAsync<Int> { _, cb ->
            cb(Outcome.failure(ex))
            Cancellable.getEmpty()
        }
        try {
            task.runBlocking()
        } catch (e: RuntimeException) {
            assertEquals(ex, e)
        }
    }

    @Test
    fun `fromAsync can be cancelled`() {
        val latch = CountDownLatch(1)
        val task = Task.fromAsync<Int> { executor, cb ->
            executor.execute {
                latch.await()
                cb(Outcome.Cancellation)
            }
            Cancellable {
                latch.countDown()
            }
        }
        val fiber = task.runFiber()
        fiber.cancel()
        fiber.joinBlocking()
        assertEquals(Outcome.Cancellation, fiber.outcomeOrNull)
    }
}
