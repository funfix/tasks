package org.funfix.tasks.kotlin

import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertTrue

class CoroutinesJvmTest {
    @Test
    fun runSuspendedUsesCurrentDispatcherWhenExecutorNull() = runTest {
        val executor = Executors.newSingleThreadExecutor { r ->
            val thread = Thread(r)
            thread.isDaemon = true
            thread.name = "coroutines-test-${thread.id}"
            thread
        }
        val dispatcher = executor.asCoroutineDispatcher()
        try {
            val threadName = withContext(dispatcher) {
                Task.fromBlockingIO { Thread.currentThread().name }.runSuspended()
            }

            assertTrue(threadName.startsWith("coroutines-test-"))
        } finally {
            dispatcher.close()
            executor.shutdown()
        }
    }
}
