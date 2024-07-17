package org.funfix.tasks

import kotlinx.coroutines.await
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import kotlin.js.Promise
import kotlin.test.assertEquals

class ExecutorsTest {
    @Test
    fun sharedIOReferenceWorks() = runTest {
        val p = Promise { resolve, _ ->
            TaskExecutors.global.execute {
                resolve(1)
            }
        }
        val r = p.await()
        assertEquals(1, r)
    }

    @Test
    fun trampolineReferenceWorks() = runTest {
        val p = Promise { resolve, _ ->
            TaskExecutors.trampoline.execute {
                resolve(1)
            }
        }
        val r = p.await()
        assertEquals(1, r)
    }

    @Test
    fun trampolined() = runTest {
        JSExecutor.withExecType(JSExecutor.ExecType.Trampolined) {
            val p = Promise { resolve, _ ->
                JSExecutor.execute {
                    resolve(1)
                }
            }
            val r = p.await()
            assertEquals(1, r)
        }
    }

    @Test
    fun setTimeout() = runTest {
        JSExecutor.withExecType(JSExecutor.ExecType.ViaSetTimeout) {
            val p = Promise { resolve, _ ->
                JSExecutor.execute {
                    resolve(1)
                }
            }
            val r = p.await()
            assertEquals(1, r)
        }
    }


    @Test
    fun setInterval() = runTest {
        JSExecutor.withExecType(JSExecutor.ExecType.ViaSetInterval) {
            val p = Promise { resolve, _ ->
                JSExecutor.execute {
                    resolve(1)
                }
            }
            val r = p.await()
            assertEquals(1, r)
        }
    }
}
