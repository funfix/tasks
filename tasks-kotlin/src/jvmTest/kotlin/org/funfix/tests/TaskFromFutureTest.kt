package org.funfix.tests

import org.funfix.tasks.jvm.Cancellable
import org.funfix.tasks.jvm.CancellableFuture
import org.funfix.tasks.kotlin.Outcome
import org.funfix.tasks.kotlin.Task
import org.funfix.tasks.kotlin.fromBlockingFuture
import org.funfix.tasks.kotlin.fromCancellableFuture
import org.funfix.tasks.kotlin.fromCompletionStage
import org.funfix.tasks.kotlin.joinBlocking
import org.funfix.tasks.kotlin.outcomeOrNull
import org.funfix.tasks.kotlin.runBlocking
import org.funfix.tasks.kotlin.runFiber
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class TaskFromFutureTest {
    @Test
    fun `fromBlockingFuture (success)`() {
        val ec = Executors.newCachedThreadPool()
        try {
            val task = Task.fromBlockingFuture {
                ec.submit(Callable { 1 })
            }
            assertEquals(1, task.runBlocking())
        } finally {
            ec.shutdown()
        }
    }

    @Test
    fun `fromBlockingFuture (failure)`() {
        val ex = RuntimeException("Boom!")
        val ec = Executors.newCachedThreadPool()
        try {
            val task = Task.fromBlockingFuture<Any> {
                ec.submit { throw ex }
            }
            try {
                task.runBlocking()
                fail("Expected exception")
            } catch (e: RuntimeException) {
                assertEquals(ex, e)
            }
        } finally {
            ec.shutdown()
        }
    }

    @Test
    fun `fromBlockingFuture (cancellation)`() {
        val ec = Executors.newCachedThreadPool()
        val wasStarted = CountDownLatch(1)
        val wasCancelled = CountDownLatch(1)
        try {
            val task = Task.fromBlockingFuture {
                ec.submit(Callable {
                    wasStarted.countDown()
                    try {
                        Thread.sleep(10000)
                        1
                    } catch (e: InterruptedException) {
                        wasCancelled.countDown()
                        throw e
                    }
                })
            }
            val fiber = task.runFiber()
            TimedAwait.latchAndExpectCompletion(wasStarted, "wasStarted")
            fiber.cancel()
            fiber.joinBlocking()
            assertEquals(Outcome.Cancellation, fiber.outcomeOrNull)
            TimedAwait.latchAndExpectCompletion(wasCancelled, "wasCancelled")
        } finally {
            ec.shutdown()
        }
    }

    @Test
    fun `fromCompletionStage (success)`() {
        val task = Task.fromCompletionStage {
            CompletableFuture.supplyAsync { 1 }
        }
        assertEquals(1, task.runBlocking())
    }

    @Test
    fun `fromCompletionStage (failure)`() {
        val ex = RuntimeException("Boom!")
        val task = Task.fromCompletionStage<Any> {
            CompletableFuture.supplyAsync {
                throw ex
            }
        }
        try {
            task.runBlocking()
            fail("Expected exception")
        } catch (e: RuntimeException) {
            assertEquals(ex, e)
        }
    }

    @Test
    fun `fromCancellableFuture (success)`() {
        val ec = Executors.newCachedThreadPool()
        try {
            val task = Task.fromCancellableFuture {
                CancellableFuture(
                    CompletableFuture.supplyAsync { 1 },
                    Cancellable.getEmpty()
                )
            }
            assertEquals(1, task.runBlocking())
        } finally {
            ec.shutdown()
        }
    }

    @Test
    fun `fromCancellableFuture (failure)`() {
        val ex = RuntimeException("Boom!")
        val ec = Executors.newCachedThreadPool()
        try {
            val task = Task.fromCancellableFuture<Any> {
                CancellableFuture(
                    CompletableFuture.supplyAsync {
                        throw ex
                    },
                    Cancellable.getEmpty()
                )
            }
            try {
                task.runBlocking()
                fail("Expected exception")
            } catch (e: RuntimeException) {
                assertEquals(ex, e)
            }
        } finally {
            ec.shutdown()
        }
    }

    @Test
    fun `fromCancellableFuture (cancellation)`() {
        val wasStarted = CountDownLatch(1)
        val wasCancelled = CountDownLatch(1)
        val task = Task.fromCancellableFuture {
            CancellableFuture(
                CompletableFuture.supplyAsync {
                    wasStarted.countDown()
                    TimedAwait.latchNoExpectations(wasCancelled)
                }
            ) {
                wasCancelled.countDown()
            }
        }
        val fiber = task.runFiber()
        TimedAwait.latchAndExpectCompletion(wasStarted, "wasStarted")
        fiber.cancel()
        fiber.joinBlocking()
        assertEquals(Outcome.Cancellation, fiber.outcomeOrNull)
        TimedAwait.latchAndExpectCompletion(wasCancelled, "wasCancelled")
    }
}
