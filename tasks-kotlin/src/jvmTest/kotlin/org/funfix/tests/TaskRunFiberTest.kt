package org.funfix.tests

import org.funfix.tasks.jvm.TaskCancellationException
import org.funfix.tasks.kotlin.Outcome
import org.funfix.tasks.kotlin.Task
import org.funfix.tasks.kotlin.awaitAsync
import org.funfix.tasks.kotlin.awaitBlocking
import org.funfix.tasks.kotlin.awaitBlockingTimed
import org.funfix.tasks.kotlin.fromBlockingIO
import org.funfix.tasks.kotlin.joinAsync
import org.funfix.tasks.kotlin.joinBlocking
import org.funfix.tasks.kotlin.joinBlockingTimed
import org.funfix.tasks.kotlin.outcomeOrNull
import org.funfix.tasks.kotlin.resultOrThrow
import org.funfix.tasks.kotlin.runFiber
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.fail
import kotlin.time.toKotlinDuration

class TaskRunFiberTest {
    @Test
    fun `runFiber + joinAsync (success)`() {
        val fiber = Task
            .fromBlockingIO { 1 }
            .runFiber()

        val latch = CountDownLatch(1)
        val outcomeRef = AtomicReference<Outcome<Int>?>(null)
        fiber.joinAsync {
            outcomeRef.set(fiber.outcomeOrNull!!)
            latch.countDown()
        }

        TimedAwait.latchAndExpectCompletion(latch)
        assertEquals(Outcome.Success(1), outcomeRef.get())
    }

    @Test
    fun `runFiber + joinAsync (failure)`() {
        val ex = RuntimeException("Boom!")
        val fiber = Task
            .fromBlockingIO<Int> { throw ex }
            .runFiber()

        val latch = CountDownLatch(1)
        val outcomeRef = AtomicReference<Outcome<Int>?>(null)
        fiber.joinAsync {
            outcomeRef.set(fiber.outcomeOrNull!!)
            latch.countDown()
        }

        TimedAwait.latchAndExpectCompletion(latch)
        assertEquals(Outcome.Failure(ex), outcomeRef.get())
    }

    @Test
    fun `runFiber + joinAsync (cancellation)`() {
        val fiber = Task
            .fromBlockingIO { Thread.sleep(10000) }
            .runFiber()

        val latch = CountDownLatch(1)
        val outcomeRef = AtomicReference<Outcome<Unit>?>(null)
        fiber.joinAsync {
            outcomeRef.set(fiber.outcomeOrNull!!)
            latch.countDown()
        }

        fiber.cancel()
        TimedAwait.latchAndExpectCompletion(latch)
        assertEquals(Outcome.Cancellation, outcomeRef.get())
    }

    @Test
    fun `runFiber + awaitAsync (success)`() {
        val fiber = Task
            .fromBlockingIO { 1 }
            .runFiber()

        val latch = CountDownLatch(1)
        val outcomeRef = AtomicReference<Outcome<Int>?>(null)
        fiber.awaitAsync { outcome ->
            outcomeRef.set(outcome)
            latch.countDown()
        }

        TimedAwait.latchAndExpectCompletion(latch)
        assertEquals(Outcome.Success(1), outcomeRef.get())
    }

    @Test
    fun `runFiber + awaitAsync (failure)`() {
        val ex = RuntimeException("Boom!")
        val fiber = Task
            .fromBlockingIO<Int> { throw ex }
            .runFiber()

        val latch = CountDownLatch(1)
        val outcomeRef = AtomicReference<Outcome<Int>?>(null)
        fiber.awaitAsync { outcome ->
            outcomeRef.set(outcome)
            latch.countDown()
        }

        TimedAwait.latchAndExpectCompletion(latch)
        assertEquals(Outcome.Failure(ex), outcomeRef.get())
    }

    @Test
    fun `runFiber + awaitAsync (cancellation)`() {
        val fiber = Task
            .fromBlockingIO { Thread.sleep(10000) }
            .runFiber()

        val latch = CountDownLatch(1)
        val outcomeRef = AtomicReference<Outcome<Unit>?>(null)
        fiber.awaitAsync { outcome ->
            outcomeRef.set(outcome)
            latch.countDown()
        }

        fiber.cancel()
        TimedAwait.latchAndExpectCompletion(latch)
        assertEquals(Outcome.Cancellation, outcomeRef.get())
    }

    @Test
    fun `runFiber + joinBlocking (success)`() {
        val fiber = Task
            .fromBlockingIO { 1 }
            .runFiber()

        fiber.joinBlocking()
        assertEquals(1, fiber.resultOrThrow)
        assertEquals(Outcome.Success(1), fiber.outcomeOrNull)
    }

    @Test
    fun `runFiber + joinBlocking (failure)`() {
        val ex = RuntimeException("Boom!")
        val fiber = Task
            .fromBlockingIO<Int> { throw ex }
            .runFiber()

        fiber.joinBlocking()
        assertEquals(Outcome.Failure(ex), fiber.outcomeOrNull)
    }

    @Test
    fun `runFiber + joinBlocking (cancellation)`() {
        val fiber = Task
            .fromBlockingIO { Thread.sleep(10000) }
            .runFiber()

        fiber.cancel()
        fiber.joinBlocking()
        assertEquals(Outcome.Cancellation, fiber.outcomeOrNull)
    }

    @Test
    fun `runFiber + awaitBlocking (success)`() {
        val fiber = Task
            .fromBlockingIO { 1 }
            .runFiber()

        val result = fiber.awaitBlocking()
        assertEquals(1, result)
    }

    @Test
    fun `runFiber + awaitBlocking (failure)`() {
        val ex = RuntimeException("Boom!")
        val fiber = Task
            .fromBlockingIO<Int> { throw ex }
            .runFiber()

        try {
            fiber.awaitBlocking()
            fail("Expected exception")
        } catch (e: RuntimeException) {
            assertEquals(ex, e)
        }
    }

    @Test
    fun `runFiber + awaitBlocking (cancellation)`() {
        val fiber = Task
            .fromBlockingIO { Thread.sleep(10000) }
            .runFiber()

        fiber.cancel()
        try {
            fiber.awaitBlocking()
            fail("Expected exception")
        } catch (e: TaskCancellationException) {
            // expected
        }
    }

    @Test
    fun `runFiber + joinBlockingTimed (success)`() {
        val fiber = Task
            .fromBlockingIO { 1 }
            .runFiber()

        fiber.joinBlockingTimed(TimedAwait.TIMEOUT.toKotlinDuration())
        assertEquals(1, fiber.resultOrThrow)
        assertEquals(Outcome.Success(1), fiber.outcomeOrNull)
    }

    @Test
    fun `runFiber + joinBlockingTimed (failure)`() {
        val ex = RuntimeException("Boom!")
        val fiber = Task
            .fromBlockingIO<Int> { throw ex }
            .runFiber()

        fiber.joinBlockingTimed(TimedAwait.TIMEOUT.toKotlinDuration())
        assertEquals(Outcome.Failure(ex), fiber.outcomeOrNull)
    }

    @Test
    fun `runFiber + joinBlockingTimed (cancellation)`() {
        val fiber = Task
            .fromBlockingIO { Thread.sleep(10000) }
            .runFiber()

        fiber.cancel()
        fiber.joinBlockingTimed(TimedAwait.TIMEOUT.toKotlinDuration())
        assertEquals(Outcome.Cancellation, fiber.outcomeOrNull)
    }

    @Test
    fun `runFiber + awaitBlockingTimed (success)`() {
        val fiber = Task
            .fromBlockingIO { 1 }
            .runFiber()

        val result = fiber.awaitBlockingTimed(TimedAwait.TIMEOUT.toKotlinDuration())
        assertEquals(1, result)
    }

    @Test
    fun `runFiber + awaitBlockingTimed (failure)`() {
        val ex = RuntimeException("Boom!")
        val fiber = Task
            .fromBlockingIO<Int> { throw ex }
            .runFiber()

        try {
            fiber.awaitBlockingTimed(TimedAwait.TIMEOUT.toKotlinDuration())
            fail("Expected exception")
        } catch (e: RuntimeException) {
            assertEquals(ex, e)
        }
    }

    @Test
    fun `runFiber + awaitBlockingTimed (cancellation)`() {
        val fiber = Task
            .fromBlockingIO { Thread.sleep(10000) }
            .runFiber()

        fiber.cancel()
        try {
            fiber.awaitBlockingTimed(TimedAwait.TIMEOUT.toKotlinDuration())
            fail("Expected exception")
        } catch (e: TaskCancellationException) {
            // expected
        }
    }
}
