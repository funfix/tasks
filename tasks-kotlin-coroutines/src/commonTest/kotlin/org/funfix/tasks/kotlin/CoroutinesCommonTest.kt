package org.funfix.tasks.kotlin

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CoroutinesCommonTest {
    @Test
    fun runSuspendedSuccess() = runTest {
        val task = Task.fromAsync<Int> { _, cb ->
            cb(Outcome.Success(42))
            Cancellable {}
        }

        val result = task.runSuspended()

        assertEquals(42, result)
    }

    @Test
    fun runSuspendedFailure() = runTest {
        val ex = RuntimeException("Boom")
        val task = Task.fromAsync<Int> { _, cb ->
            cb(Outcome.Failure(ex))
            Cancellable {}
        }

        val thrown = assertFailsWith<RuntimeException> { task.runSuspended() }

        assertEquals("Boom", thrown.message)
    }

    @Test
    fun runSuspendedCancelsTaskToken() = runTest {
        val cancelled = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()
        val task = Task.fromAsync<Int> { _, cb ->
            started.complete(Unit)
            Cancellable {
                cancelled.complete(Unit)
                cb(Outcome.Cancellation)
            }
        }

        val deferred = async { task.runSuspended() }
        started.await()
        deferred.cancel()

        assertFailsWith<CancellationException> { deferred.await() }
        cancelled.await()
    }

    @Test
    fun fromSuspendedSuccess() = runTest {
        val task = Task.fromSuspended {
            21 + 21
        }
        val deferred = CompletableDeferred<Outcome<Int>>()
        task.runAsync { outcome -> deferred.complete(outcome) }

        assertEquals(Outcome.Success(42), deferred.await())
    }

    @Test
    fun fromSuspendedFailure() = runTest {
        val ex = RuntimeException("Boom")
        val task = Task.fromSuspended<Int> {
            throw ex
        }
        val deferred = CompletableDeferred<Outcome<Int>>()
        task.runAsync { outcome -> deferred.complete(outcome) }

        assertEquals(Outcome.Failure(ex), deferred.await())
    }

    @Test
    fun fromSuspendedCancellation() = runTest {
        val started = CompletableDeferred<Unit>()
        val task = Task.fromSuspended<Unit> {
            started.complete(Unit)
            awaitCancellation()
        }
        val deferred = CompletableDeferred<Outcome<Unit>>()
        val cancel = task.runAsync { outcome -> deferred.complete(outcome) }

        started.await()
        cancel.cancel()

        assertEquals(Outcome.Cancellation, deferred.await())
    }

}
