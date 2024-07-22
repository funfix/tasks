package org.funfix.tasks.kotlin

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class AsyncTests: AsyncTestUtils {
    @Test
    fun createAsync() = runTest {
        val task = Task.create { executor, callback ->
            executor.execute {
                callback(Outcome.Success(1 + 1))
            }
            EmptyCancellable
        }

        val r = task.await()
        assertEquals(2, r)
    }

    @Test
    fun fromSuspendedHappy() = runTest {
        val task = Task.fromSuspended {
            yield()
            1 + 1
        }

        val r = task.await()
        assertEquals(2, r)
    }

    @Test
    fun fromSuspendedFailure() = runTest {
        val e = RuntimeException("Boom")
        val task = Task.fromSuspended<Int> {
            yield()
            throw e
        }

        try {
            task.await()
            fail("Should have thrown")
        } catch (e: RuntimeException) {
            assertEquals("Boom", e.message)
        }
    }

    @Test
    fun simpleSuspendedChaining() = runTest {
        val task = Task.fromSuspended {
            yield()
            1 + 1
        }

        val task2 = Task.fromSuspended {
            yield()
            task.await() + 1
        }

        val r = task2.await()
        assertEquals(3, r)
    }

//    @Test
//    fun fiberChaining() = runTest {
//        val task = Task.fromSuspended {
//            yield()
//            1 + 1
//        }
//
//        val task2 = Task.fromSuspended {
//            yield()
//            task.executeFiber().awaitSuspended() + 1
//        }
//
//        val r = task2.executeSuspended()
//        assertEquals(3, r)
//    }
//
//    @Test
//    fun complexChaining() = runTest {
//        val task = Task.fromSuspended {
//            yield()
//            1 + 1
//        }
//
//        val task2 = Task.fromSuspended {
//            yield()
//            task.executeSuspended() + 1
//        }
//
//        val task3 = Task.fromSuspended {
//            yield()
//            task2.executeFiber().awaitSuspended() + 1
//        }
//
//        val task4 = Task.fromSuspended {
//            yield()
//            val deferred = async { task3.executeSuspended() }
//            deferred.await() + 1
//        }
//
//        val r = task4.executeSuspended()
//        assertEquals(5, r)
//    }
//
//    @Test
//    fun cancellation() = runTest {
//        val lock = Mutex()
//        val latch = CompletableDeferred<Unit>()
//        val wasCancelled = CompletableDeferred<Unit>()
//        lock.lock()
//
//        val job = async {
//            Task.fromSuspended {
//                yield()
//                latch.complete(Unit)
//                try {
//                    lock.lock()
//                } finally {
//                    wasCancelled.complete(Unit)
//                    lock.unlock()
//                }
//            }.executeSuspended()
//        }
//
//        withTimeout(5000) { latch.await() }
//        job.cancel()
//
//        withTimeout(5000) {
//            wasCancelled.await()
//        }
//    }
}
