package org.funfix.tests

import kotlinx.coroutines.*
import org.funfix.tasks.Task
import org.funfix.tasks.VirtualThreads
import org.funfix.tasks.awaitSuspended
import org.funfix.tasks.virtualThreadsOrBackup
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class TaskToSuspensionTest {
    @Test
    fun `pure computation`() = runBlocking {
        val task = Task.fromBlockingIO {
            1 + 1
        }

        val r = task.awaitSuspended()
        assertEquals(2, r)
    }

    @Test
    fun `can throw error`() = runBlocking {
        val exception = RuntimeException("Boom!")
        val task = Task.fromBlockingIO<String> {
            throw exception
        }

        try {
            task.awaitSuspended()
            fail("Should have thrown")
        } catch (e: RuntimeException) {
            assertEquals(exception.message, e.message)
        }
    }

    @Test
    fun `can be cancelled (1)`() = runBlocking {
        coroutineScope {
            val wasStarted = CountDownLatch(1)
            val latch = CountDownLatch(1)
            val wasCancelled = CountDownLatch(1)

            val task = Task.fromBlockingIO {
                wasStarted.countDown()
                try {
                    latch.await()
                } catch (_: InterruptedException) {
                    wasCancelled.countDown()
                }
            }

            val job = launch(Dispatchers.IO) {
                task.awaitSuspended()
            }
            awaitLatchWithExpectationSuspended(wasStarted, "wasStarted")
            job.cancel()
            awaitLatchWithExpectationSuspended(wasCancelled, "wasCancelled")
        }
    }

    @Test
    fun `can be cancelled (2)`() = runBlocking {
        coroutineScope {
            val wasStarted = CountDownLatch(1)
            val latch = CountDownLatch(1)
            val hits = AtomicInteger(0)

            val task = Task.fromBlockingIO {
                wasStarted.countDown()
                try {
                    latch.await()
                } catch (_: InterruptedException) {
                    hits.incrementAndGet()
                }
            }

            val job = launch(Dispatchers.IO) {
                task.awaitSuspended()
            }
            awaitLatchWithExpectationSuspended(wasStarted, "wasStarted")
            job.cancel()
            awaitJoinWithTimeoutSuspended(job)
            assertEquals(1, hits.get())
        }
    }

    @Test
    fun `task gets executed on the default dispatcher`() = runBlocking {
        val task = Task.fromBlockingIO {
            Thread.currentThread().name
        }
        val name = withContext(Dispatchers.Default) {
            task.awaitSuspended()
        }
        assertStartsWith("DefaultDispatcher-worker-", name)
    }


    @Test
    fun `task gets executed on the virtual-threads dispatcher`() = runBlocking {
        assumeTrue(Dispatchers.VirtualThreads != null)

        val task = Task.fromBlockingIO {
            Thread.currentThread().name
        }
        val name = withContext(Dispatchers.virtualThreadsOrBackup()) {
            task.awaitSuspended()
        }
        assertStartsWith("VirtualThreadsDispatcher-worker-", name)
    }

    @Test
    fun `task gets executed on the IO dispatcher`() = runBlocking {
        assumeTrue(Dispatchers.VirtualThreads == null)

        val task = Task.fromBlockingIO {
            Thread.currentThread().name
        }
        val name = withContext(Dispatchers.virtualThreadsOrBackup()) {
            task.awaitSuspended()
        }
        assertStartsWith("DefaultDispatcher-worker-", name)
    }
}
