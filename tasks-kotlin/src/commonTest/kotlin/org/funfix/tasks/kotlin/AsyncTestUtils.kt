package org.funfix.tasks.kotlin

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

interface AsyncTestUtils {
    fun runTest(
        context: CoroutineContext = EmptyCoroutineContext,
        testBody: suspend TestScope.() -> Unit
    ) {
        kotlinx.coroutines.test.runTest(context) {
            withContext(Dispatchers.Unconfined) {
                testBody()
            }
        }
    }
}
