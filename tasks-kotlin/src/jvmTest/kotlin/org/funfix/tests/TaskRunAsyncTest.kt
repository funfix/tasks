package org.funfix.tests

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail
import org.funfix.tasks.jvm.TaskCancellationException
import org.funfix.tasks.kotlin.Outcome
import org.funfix.tasks.kotlin.Task
import org.funfix.tasks.kotlin.fromBlockingIO
import org.funfix.tasks.kotlin.runAsync
import org.junit.jupiter.api.Assertions.assertTrue

class TaskRunAsyncTest {
    @Test
    fun `runAsync (success)`() {
        val latch = CountDownLatch(1)
        val outcomeRef = AtomicReference<Outcome<Int>?>(null)
        val task = Task.fromBlockingIO { 1 }
        task.runAsync { outcome ->
            outcomeRef.set(outcome)
            latch.countDown()
        }

        TimedAwait.latchAndExpectCompletion(latch)
        assertEquals(Outcome.Success(1), outcomeRef.get())
        assertEquals(1, outcomeRef.get()!!.orThrow)
    }

    @Test
    fun `runAsync (failure)`() {
        val latch = CountDownLatch(1)
        val outcomeRef = AtomicReference<Outcome<Int>?>(null)
        val ex = RuntimeException("Boom!")
        val task = Task.fromBlockingIO { throw ex }
        task.runAsync { outcome ->
            outcomeRef.set(outcome)
            latch.countDown()
        }

        TimedAwait.latchAndExpectCompletion(latch)
        assertEquals(Outcome.Failure(ex), outcomeRef.get())
        try {
            outcomeRef.get()!!.orThrow
            fail("Expected exception")
        } catch (e: RuntimeException) {
            assertEquals(ex, e)
        }
    }

    @Test
    fun `runAsync (cancellation)`() {
        val latch = CountDownLatch(1)
        val wasStarted = CountDownLatch(1)
        val outcomeRef = AtomicReference<Outcome<Int>?>(null)
        val task = Task.fromBlockingIO {
            wasStarted.countDown()
            Thread.sleep(10000)
            1
        }
        val cancel = task.runAsync { outcome ->
            outcomeRef.set(outcome)
            latch.countDown()
        }

        TimedAwait.latchAndExpectCompletion(wasStarted, "wasStarted")
        cancel.cancel()
        TimedAwait.latchAndExpectCompletion(latch)
        assertEquals(Outcome.Cancellation, outcomeRef.get())
        try {
            outcomeRef.get()!!.orThrow
            fail("Expected exception")
        } catch (e: TaskCancellationException) {
            // expected
        }
    }

    @Test
    @Suppress("DEPRECATION")
    fun `runAsync runs with given executor`() {
        val ec = Executors.newCachedThreadPool { r ->
            val t = Thread(r)
            t.isDaemon = true
            t.name = "my-thread-${t.id}"
            t
        }
        try {
            val latch = CountDownLatch(1)
            val outcomeRef = AtomicReference<Outcome<String>?>(null)
            val task = Task.fromBlockingIO {
                Thread.currentThread().name
            }
            task.runAsync(ec) { outcome ->
                outcomeRef.set(outcome)
                latch.countDown()
            }
            TimedAwait.latchAndExpectCompletion(latch)
            assertTrue(outcomeRef.get()!!.orThrow.startsWith("my-thread-"))
        } finally {
            ec.shutdown()
        }
    }
}
