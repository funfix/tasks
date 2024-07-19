package org.funfix.tasks.kt

import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.funfix.tasks.Cancellable
import org.funfix.tasks.Task
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class AsyncTests {
    @Test
    fun createAsync() = runTest {
        val task = Task.create { executor, callback ->
            executor.execute {
                callback.onSuccess(1 + 1)
            }
            Cancellable.EMPTY
        }

        val r = task.executeSuspended()
        assertEquals(2, r)
    }

    @Test
    fun fromSuspendedHappy() = runTest {
        val task = Task.fromSuspended {
            yield()
            1 + 1
        }

        val r = task.executeSuspended()
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
            task.executeSuspended()
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
            task.executeSuspended() + 1
        }

        val r = task2.executeSuspended()
        assertEquals(3, r)
    }

    @Test
    fun fiberChaining() = runTest {
        val task = Task.fromSuspended {
            yield()
            1 + 1
        }

        val task2 = Task.fromSuspended {
            yield()
            task.executeFiber().awaitSuspended() + 1
        }

        val r = task2.executeSuspended()
        assertEquals(3, r)
    }

    @Test
    fun complexChaining() = runTest {
        val task = Task.fromSuspended {
            yield()
            1 + 1
        }

        val task2 = Task.fromSuspended {
            yield()
            task.executeSuspended() + 1
        }

        val task3 = Task.fromSuspended {
            yield()
            task2.executeFiber().awaitSuspended() + 1
        }

        val task4 = Task.fromSuspended {
            yield()
            val deferred = async { task3.executeSuspended() }
            deferred.await() + 1
        }

        val r = task4.executeSuspended()
        assertEquals(5, r)
    }
}
