package org.funfix.tests

import org.funfix.tasks.kotlin.Outcome
import org.funfix.tasks.kotlin.Task
import org.funfix.tasks.kotlin.fromBlockingIO
import org.funfix.tasks.kotlin.joinBlocking
import org.funfix.tasks.kotlin.outcomeOrNull
import org.funfix.tasks.kotlin.runBlocking
import org.funfix.tasks.kotlin.runFiber
import kotlin.test.Test
import kotlin.test.assertEquals

class TaskFromBlockingIOTest {
    @Test
    fun `fromBlockingIO (success)`() {
        val task = Task.fromBlockingIO { 1 }
        assertEquals(1, task.runBlocking())
    }

    @Test
    fun `fromBlockingIO (failure)`() {
        val ex = RuntimeException("Boom!")
        val task = Task.fromBlockingIO<Int> { throw ex }
        try {
            task.runBlocking()
        } catch (e: RuntimeException) {
            assertEquals(ex, e)
        }
    }

    @Test
    fun `fromBlockingIO (cancellation)`() {
        val task: Task<Int> = Task.fromBlockingIO {
            Thread.sleep(10000)
            1
        }
        val fiber = task.runFiber()
        fiber.cancel()
        fiber.joinBlocking()
        assertEquals(Outcome.Cancellation, fiber.outcomeOrNull)
    }
}
