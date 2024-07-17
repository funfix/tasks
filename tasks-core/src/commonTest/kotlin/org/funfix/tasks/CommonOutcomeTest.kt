package org.funfix.tasks

import org.funfix.tasks.support.ExecutionException
import kotlin.test.Test
import kotlin.test.assertEquals

class CommonOutcomeTest {
    @Test
    fun succeeded() {
        val outcome1: Outcome<Int> = Outcome.succeeded(1)
        val outcome2: Outcome<Int> = Outcome.Succeeded(1)

        assertEquals(1, outcome1.getOrThrow())
        assertEquals(1, outcome2.getOrThrow())

        when (outcome1) {
            is Outcome.Succeeded -> assertEquals(1, outcome1.value)
            else -> error("Expected succeeded")
        }
        when (outcome2) {
            is Outcome.Succeeded -> assertEquals(1, outcome2.value)
            else -> error("Expected succeeded")
        }
    }

    @Test
    fun failed() {
        val e = RuntimeException("Boom")
        val outcome1: Outcome<Int> = Outcome.failed(e)
        val outcome2: Outcome<Int> = Outcome.Failed(e)

        assertEquals(outcome1, outcome2)
        try {
            outcome1.getOrThrow()
            error("Expected exception")
        } catch (ex: ExecutionException) {
            assertEquals(e, ex.cause)
        }
    }

    @Test
    fun cancelled() {
        val outcome1: Outcome<Int> = Outcome.cancelled()
        val outcome2: Outcome<Int> = Outcome.Cancelled

        assertEquals(outcome1, outcome2)
        try {
            outcome1.getOrThrow()
            error("Expected exception")
        } catch (ex: TaskCancellationException) {
            // expected
        }
    }
}
