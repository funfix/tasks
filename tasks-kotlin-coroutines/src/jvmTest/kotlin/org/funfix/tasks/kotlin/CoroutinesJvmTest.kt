package org.funfix.tasks.kotlin

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.funfix.tasks.jvm.Cancellable
import org.funfix.tasks.jvm.Outcome
import org.funfix.tasks.jvm.Task
import org.funfix.tasks.jvm.TaskCancellationException

class CoroutinesJvmTest {
    @Test
    fun `runSuspending signals Success`() = runTest {
        val task = Task.fromAsync { _, cb ->
            cb.onSuccess(42)
            Cancellable {}
        }

        val result = task.runSuspending()

        assertEquals(42, result)
    }

    @Test
    fun `runSuspending signals Failure`() = runTest {
        val ex = RuntimeException("Boom")
        val task = Task.fromAsync<Int> { _, cb ->
            cb.onFailure(ex)
            Cancellable {}
        }

        val thrown = assertFailsWith<RuntimeException> { task.runSuspending() }

        assertEquals("Boom", thrown.message)
    }


    @Test
    fun `runSuspending cancels the task token`() = runTest {
        val cancelled = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()
        val task = Task.fromAsync<Int> { _, cb ->
            started.complete(Unit)
            Cancellable {
                cancelled.complete(Unit)
                cb.onCancellation()
            }
        }

        val deferred = async { task.runSuspending() }
        started.await()
        deferred.cancel()

        assertFailsWith<CancellationException> { deferred.await() }
        cancelled.await()
    }

    @Test
    fun `suspendAsTask signals Success`() = runTest {
        val task = suspendAsTask {
            21 + 21
        }
        val deferred = CompletableDeferred<Outcome<Int>>()
        task.runAsync { outcome -> deferred.complete(outcome) }

        assertEquals(Outcome.Success(42), deferred.await())
    }

    @Test
    fun `suspendAsTask signals Failure`() = runTest {
        val ex = RuntimeException("Boom")
        val task = suspendAsTask<Int> {
            throw ex
        }
        val deferred = CompletableDeferred<Outcome<Int>>()
        task.runAsync { outcome -> deferred.complete(outcome) }

        assertEquals(Outcome.Failure(ex), deferred.await())
    }

    @Test
    fun `suspendAsTask signals Cancellation`() = runTest {
        val started = CompletableDeferred<Unit>()
        val task = suspendAsTask<Unit> {
            started.complete(Unit)
            awaitCancellation()
        }
        val deferred = CompletableDeferred<Outcome<Unit>>()
        val cancel = task.runAsync { outcome -> deferred.complete(outcome) }

        started.await()
        cancel.cancel()

        assertEquals(Outcome.Cancellation(), deferred.await())
    }

    @Test
    fun `runSuspending uses current Dispatcher when Executor is null`() = runTest {
        val executor = Executors.newSingleThreadExecutor { r ->
            val thread = Thread(r)
            thread.isDaemon = true
            thread.name = "coroutines-test-${thread.id}"
            thread
        }
        val dispatcher = executor.asCoroutineDispatcher()
        try {
            val threadName = withContext(dispatcher) {
                Task.fromBlockingIO { Thread.currentThread().name }.runSuspending()
            }

            assertTrue(threadName.startsWith("coroutines-test-"))
        } finally {
            dispatcher.close()
            executor.shutdown()
        }
    }

    @Test
    fun `runSuspending translates async callback success`() = runTest {
        val task = Task.fromAsync { _, cb ->
            cb.onSuccess(42)
            Cancellable {}
        }

        assertEquals(42, task.runSuspending())
    }

    @Test
    fun `runSuspending translates async callback failure`() = runTest {
        val ex = RuntimeException("Boom")
        val task = Task.fromAsync<Int> { _, cb ->
            cb.onFailure(ex)
            Cancellable {}
        }

        val thrown = assertFailsWith<RuntimeException> { task.runSuspending() }

        assertEquals("Boom", thrown.message)
    }

    @Test
    fun `runSuspending resumes with task cancellation`() = runTest {
        val task = Task.fromAsync<Int> { _, cb ->
            cb.onCancellation()
            Cancellable {}
        }

        assertFailsWith<CancellationException> { task.runSuspending() }
    }

    @Test
    fun `runSuspending forwards runAsync failure`() = runTest {
        val ex = RuntimeException("Boom")
        val task = Task.fromAsync<Int> { _, _ ->
            throw ex
        }

        val thrown = assertFailsWith<RuntimeException> { task.runSuspending() }

        assertEquals("Boom", thrown.message)
    }

    @Test
    fun `runSuspending cancels task when coroutine job is cancelled`() = runTest {
        val started = CompletableDeferred<Unit>()
        val latch = CountDownLatch(1)
        val task = Task.fromBlockingIO {
            started.complete(Unit)
            latch.await()
            42
        }
        val executor = Executors.newSingleThreadExecutor()
        try {
            val deferred = async { task.runSuspending(executor) }

            started.await()
            deferred.cancel()
            latch.countDown()

            deferred.join()

            assertFailsWith<CancellationException> { deferred.await() }
        } finally {
            executor.shutdown()
        }
    }

    @Test
    fun `suspendAsTask maps CancellationException to cancellation`() = runTest {
        val task = suspendAsTask<Unit> {
            throw CancellationException("cancelled")
        }
        val deferred = CompletableDeferred<Outcome<Unit>>()
        task.runAsync { outcome -> deferred.complete(outcome) }

        assertEquals(Outcome.Cancellation(), deferred.await())
    }

    @Test
    fun `suspendAsTask maps TaskCancellationException to cancellation`() = runTest {
        val task = suspendAsTask<Unit> {
            throw TaskCancellationException("stop")
        }
        val deferred = CompletableDeferred<Outcome<Unit>>()
        task.runAsync { outcome -> deferred.complete(outcome) }

        assertEquals(Outcome.Cancellation(), deferred.await())
    }

    @Test
    fun `suspendAsTask maps InterruptedException to cancellation`() = runTest {
        val task = suspendAsTask<Unit> {
            throw InterruptedException("stop")
        }
        val deferred = CompletableDeferred<Outcome<Unit>>()
        task.runAsync { outcome -> deferred.complete(outcome) }

        assertEquals(Outcome.Cancellation(), deferred.await())
    }

    @Test
    fun `suspendAsTask cancels when coroutine is cancelled while blocked`() = runTest {
        val started = CompletableDeferred<Unit>()
        val latch = CountDownLatch(1)
        val wasTriggered = CompletableDeferred<Unit>()
        val task = suspendAsTask {
            runInterruptible {
                started.complete(Unit)
                try {
                    assertFalse("Should have been cancelled") {
                        latch.await(10, TimeUnit.SECONDS)
                    }
                } catch (_: InterruptedException) {
                    wasTriggered.complete(Unit)
                }
            }
        }
        val executor = Executors.newSingleThreadExecutor()
        try {
            val job = async {
                task.runSuspending(executor)
            }

            started.await()
            job.cancel()
            wasTriggered.await()
            
            assertTrue(job.isCancelled)
        } finally {
            executor.shutdown()
        }
    }
}
