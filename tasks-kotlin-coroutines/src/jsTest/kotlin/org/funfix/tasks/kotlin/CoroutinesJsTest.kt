package org.funfix.tasks.kotlin

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CoroutinesJsTest {
    @Test
    fun runSuspendedUsesProvidedExecutor() = runTest {
        var executed = false
        val executor = Executor { command ->
            executed = true
            command.run()
        }
        val task = Task.fromAsync<Int> { exec, cb ->
            exec.execute { cb(Outcome.Success(7)) }
            Cancellable {}
        }

        val result = task.runSuspended(executor)

        assertEquals(7, result)
        assertTrue(executed)
    }
}
