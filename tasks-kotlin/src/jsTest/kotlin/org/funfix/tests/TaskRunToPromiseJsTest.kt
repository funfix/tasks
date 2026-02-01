package org.funfix.tests

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.funfix.tasks.kotlin.Cancellable
import org.funfix.tasks.kotlin.Outcome
import org.funfix.tasks.kotlin.Task
import org.funfix.tasks.kotlin.TaskCancellationException
import org.funfix.tasks.kotlin.fromAsync
import org.funfix.tasks.kotlin.runToCancellablePromise
import org.funfix.tasks.kotlin.runToPromise

class TaskRunToPromiseJsTest {
    @Test
    fun `runToPromise (success)`() = runTest {
        val task = Task.fromAsync<Int> { _, cb ->
            cb(Outcome.Success(1))
            Cancellable.empty
        }
        val deferred = CompletableDeferred<Int>()
        task.runToPromise().then({ value ->
            deferred.complete(value)
        }, { error ->
            deferred.completeExceptionally(error)
        })
        assertEquals(1, deferred.await())
    }

    @Test
    fun `runToPromise (failure)`() = runTest {
        val ex = RuntimeException("Boom!")
        val task = Task.fromAsync<Int> { _, cb ->
            cb(Outcome.Failure(ex))
            Cancellable.empty
        }
        val deferred = CompletableDeferred<Throwable>()
        task.runToPromise().then({ _ ->
            deferred.complete(RuntimeException("Unexpected"))
        }, { error ->
            deferred.complete(error)
        })
        assertEquals(ex, deferred.await())
    }

    @Test
    fun `runToPromise (cancellation)`() = runTest {
        val task = Task.fromAsync<Int> { _, cb ->
            cb(Outcome.Cancellation)
            Cancellable.empty
        }
        val deferred = CompletableDeferred<Throwable>()
        task.runToPromise().then({ _ ->
            deferred.complete(RuntimeException("Unexpected"))
        }, { error ->
            deferred.complete(error)
        })
        val error = deferred.await()
        assertTrue(error is TaskCancellationException)
    }

    @Test
    fun `runToCancellablePromise cancelAndJoin`() = runTest {
        val task = Task.fromAsync<Int> { _, cb ->
            Cancellable {
                cb(Outcome.Cancellation)
            }
        }
        val cp = task.runToCancellablePromise()
        val error = CompletableDeferred<Throwable>()
        val joined = CompletableDeferred<Unit>()
        cp.promise.then({ _ ->
            error.complete(RuntimeException("Unexpected"))
        }, { err ->
            error.complete(err)
        })
        cp.cancelAndJoin().then({
            joined.complete(Unit)
        }, {
            joined.complete(Unit)
        })
        joined.await()
        assertTrue(error.await() is TaskCancellationException)
    }
}
