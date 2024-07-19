package org.funfix.tasks

import kotlinx.coroutines.await
import kotlinx.coroutines.test.runTest
import kotlin.js.Promise
import kotlin.test.Test
import kotlin.test.assertEquals

class PromisesTest: AsyncTestUtils {
    @Test
    fun fromUncancellablePromise() = runTest {
        val task = Task.fromUncancellablePromise {
            Promise { resolve, _ ->
                JSExecutor.yield().then {
                    resolve(1 + 1)
                }
            }
        }

        val p = task.executePromise()
        val r = p.promise.await()
        assertEquals(2, r)
    }
}
