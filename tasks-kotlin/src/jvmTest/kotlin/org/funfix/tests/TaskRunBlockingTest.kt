package org.funfix.tests

import org.funfix.tasks.kotlin.BlockingIOExecutor
import org.funfix.tasks.kotlin.Task
import org.funfix.tasks.kotlin.fromBlockingIO
import org.funfix.tasks.kotlin.runBlocking
import org.funfix.tasks.kotlin.runBlockingTimed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail
import kotlin.time.toKotlinDuration

class TaskRunBlockingTest {
    @Test
    fun `runBlocking (success)`() {
        val task = Task.fromBlockingIO { 1 }

        assertEquals(1, task.runBlocking())
        assertEquals(1, task.runBlocking(BlockingIOExecutor))
    }

    @Test
    fun `runBlocking (failure)`() {
        val ex = RuntimeException("Boom!")
        val task = Task.fromBlockingIO<Int> { throw ex }

        try {
            task.runBlocking()
        } catch (e: RuntimeException) {
            assertEquals(ex, e)
        }

        try {
            task.runBlocking(BlockingIOExecutor)
        } catch (e: RuntimeException) {
            assertEquals(ex, e)
        }
    }

    @Test
    fun `runBlocking (cancellation)`() {
        val task: Task<Int> = Task.fromBlockingIO {
            throw InterruptedException()
        }
        try {
            task.runBlocking()
        } catch (e: InterruptedException) {
            // expected
        }
        try {
            task.runBlocking(BlockingIOExecutor)
        } catch (e: InterruptedException) {
            // expected
        }
    }

    @Test
    fun `runBlockingTimed (success)`() {
        val task = Task.fromBlockingIO { 1 }

        assertEquals(1, task.runBlockingTimed(
            TimedAwait.TIMEOUT.toKotlinDuration()
        ))
        assertEquals(1, task.runBlockingTimed(
            TimedAwait.TIMEOUT.toKotlinDuration(),
            BlockingIOExecutor
        ))
    }

    @Test
    fun `runBlockingTimed (failure)`() {
        val ex = RuntimeException("Boom!")
        val task = Task.fromBlockingIO<Int> { throw ex }

        try {
            task.runBlockingTimed(TimedAwait.TIMEOUT.toKotlinDuration())
        } catch (e: RuntimeException) {
            assertEquals(ex, e)
        }

        try {
            task.runBlockingTimed(TimedAwait.TIMEOUT.toKotlinDuration(), BlockingIOExecutor)
        } catch (e: RuntimeException) {
            assertEquals(ex, e)
        }
    }

    @Test
    fun `runBlockingTimed (cancellation)`() {
        val task: Task<Int> = Task.fromBlockingIO {
            throw InterruptedException()
        }
        try {
            task.runBlockingTimed(TimedAwait.TIMEOUT.toKotlinDuration())
            fail("Expected exception")
        } catch (e: InterruptedException) {
            // expected
        }
        try {
            task.runBlockingTimed(TimedAwait.TIMEOUT.toKotlinDuration(), BlockingIOExecutor)
            fail("Expected exception")
        } catch (e: InterruptedException) {
            // expected
        }
    }
}
