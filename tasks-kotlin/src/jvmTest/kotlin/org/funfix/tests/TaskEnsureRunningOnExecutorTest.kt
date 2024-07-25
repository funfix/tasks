package org.funfix.tests

import org.funfix.tasks.kotlin.Task
import org.funfix.tasks.kotlin.ensureRunningOnExecutor
import org.funfix.tasks.kotlin.fromBlockingIO
import org.funfix.tasks.kotlin.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals

class TaskEnsureRunningOnExecutorTest {
    @Test
    @Suppress("DEPRECATION")
    fun `ensureRunningOnExecutorWorks`() {
        val ex = Executors.newCachedThreadPool { r ->
            val th = Thread(r)
            th.name = "my-thread-" + th.id
            th
        }
        try {
            val t = Task.fromBlockingIO { Thread.currentThread().name }
            val n1 = t.runBlocking()
            val n2 = t.ensureRunningOnExecutor().runBlocking()
            val n3 = t.ensureRunningOnExecutor(ex).runBlocking()

            assertEquals(Thread.currentThread().name, n1)
            assertTrue(n2.startsWith("tasks-io-"), "tasks-io")
            assertTrue(n3.startsWith("my-thread-"), "my-thread")
        } finally {
            ex.shutdown()
        }
    }
}
