package org.funfix.tests

import kotlin.js.Promise
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.funfix.tasks.kotlin.Cancellable
import org.funfix.tasks.kotlin.CancellablePromise
import org.funfix.tasks.kotlin.Outcome
import org.funfix.tasks.kotlin.Task
import org.funfix.tasks.kotlin.fromCancellablePromise
import org.funfix.tasks.kotlin.fromPromise
import org.funfix.tasks.kotlin.runAsync

class TaskFromPromiseJsTest {
    private fun <T> joinOf(promise: Promise<T>): Promise<Unit> =
        Promise { resolve, _ ->
            promise.then(
                { resolve(Unit) },
                { resolve(Unit) }
            )
        }

    @Test
    fun `fromPromise (success)`() = runTest {
        val task = Task.fromPromise {
            Promise { resolve, _ -> resolve(1) }
        }
        val deferred = CompletableDeferred<Outcome<Int>>()
        task.runAsync { outcome -> deferred.complete(outcome) }
        assertEquals(Outcome.Success(1), deferred.await())
    }

    @Test
    fun `fromPromise (failure)`() = runTest {
        val ex = RuntimeException("Boom!")
        val task = Task.fromPromise<Int> {
            Promise { _, reject -> reject(ex) }
        }
        val deferred = CompletableDeferred<Outcome<Int>>()
        task.runAsync { outcome -> deferred.complete(outcome) }
        assertEquals(Outcome.Failure(ex), deferred.await())
    }

    @Test
    fun `fromPromise (cancellation waits for completion)`() = runTest {
        lateinit var resolve: (Int) -> Unit
        val task = Task.fromPromise {
            Promise { res, _ -> resolve = res }
        }
        val deferred = CompletableDeferred<Outcome<Int>>()
        val cancel = task.runAsync { outcome -> deferred.complete(outcome) }
        cancel.cancel()
        yield()
        assertFalse(deferred.isCompleted)
        resolve(1)
        assertEquals(Outcome.Success(1), deferred.await())
    }

    @Test
    fun `fromCancellablePromise (success)`() = runTest {
        val task = Task.fromCancellablePromise {
            val promise = Promise { resolve, _ -> resolve(1) }
            CancellablePromise(promise, joinOf(promise), Cancellable.empty)
        }
        val deferred = CompletableDeferred<Outcome<Int>>()
        task.runAsync { outcome -> deferred.complete(outcome) }
        assertEquals(Outcome.Success(1), deferred.await())
    }

    @Test
    fun `fromCancellablePromise (failure)`() = runTest {
        val ex = RuntimeException("Boom!")
        val task = Task.fromCancellablePromise<Int> {
            val promise = Promise<Int> { _, reject -> reject(ex) }
            CancellablePromise(promise, joinOf(promise), Cancellable.empty)
        }
        val deferred = CompletableDeferred<Outcome<Int>>()
        task.runAsync { outcome -> deferred.complete(outcome) }
        assertEquals(Outcome.Failure(ex), deferred.await())
    }

    @Test
    fun `fromCancellablePromise (cancellation waits and triggers token)`() = runTest {
        lateinit var resolve: (Int) -> Unit
        var cancelled = false
        val task = Task.fromCancellablePromise {
            val promise = Promise { res, _ -> resolve = res }
            CancellablePromise(promise, joinOf(promise), Cancellable { cancelled = true })
        }
        val deferred = CompletableDeferred<Outcome<Int>>()
        val cancel = task.runAsync { outcome -> deferred.complete(outcome) }
        cancel.cancel()
        yield()
        assertTrue(cancelled)
        assertFalse(deferred.isCompleted)
        resolve(1)
        assertEquals(Outcome.Cancellation, deferred.await())
    }
}
