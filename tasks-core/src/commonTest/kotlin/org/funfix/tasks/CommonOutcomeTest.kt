package org.funfix.tasks

import org.funfix.tasks.support.ExecutionException
import kotlin.test.Test
import kotlin.test.assertEquals

class CommonOutcomeTest {
    @Test
    fun success() {
        val outcome1: Outcome<Int> = Outcome.success(1)
        val outcome2: Outcome<Int> = Outcome.Success(1)

        assertEquals(1, outcome1.getOrThrow())
        assertEquals(1, outcome2.getOrThrow())

        when (outcome1) {
            is Outcome.Success -> assertEquals(1, outcome1.value)
            else -> error("Expected Success")
        }
        when (outcome2) {
            is Outcome.Success -> assertEquals(1, outcome2.value)
            else -> error("Expected Success")
        }
    }

    @Test
    fun failure() {
        val e = RuntimeException("Boom")
        val outcome1: Outcome<Int> = Outcome.failure(e)
        val outcome2: Outcome<Int> = Outcome.Failure(e)

        assertEquals(outcome1, outcome2)
        try {
            outcome1.getOrThrow()
            error("Expected exception")
        } catch (ex: ExecutionException) {
            assertEquals(e, ex.cause)
        }
    }

    @Test
    fun cancellation() {
        val outcome1: Outcome<Int> = Outcome.cancellation()
        val outcome2: Outcome<Int> = Outcome.Cancellation

        assertEquals(outcome1, outcome2)
        try {
            outcome1.getOrThrow()
            error("Expected exception")
        } catch (ex: TaskCancellationException) {
            // expected
        }
    }
}
