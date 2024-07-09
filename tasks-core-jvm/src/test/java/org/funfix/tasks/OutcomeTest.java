package org.funfix.tasks;

import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

@NullMarked
public class OutcomeTest {
    @Test
    void outcomeBuildSuccess() {
        final var outcome1 = new Outcome.Success<>("value");
        final Outcome<String, ?> outcome2 = Outcome.success("value");
        assertEquals(outcome1, outcome2);

        if (outcome2 instanceof Outcome.Success<String, ?> succeeded) {
            assertEquals("value", succeeded.value());
        } else {
            fail("Expected Success");
        }

        assertEquals("value", outcome1.getOrThrow());
        try {
            assertEquals("value", outcome2.getOrThrow());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void outcomeBuildTypedFailure() {
        final var e = new SampleException("error");
        final var outcome1 = new Outcome.TypedFailure<>(e);
        final Outcome<?, SampleException> outcome2 = Outcome.typedFailure(e);
        assertEquals(outcome1, outcome2);

        if (outcome2 instanceof Outcome.TypedFailure<?, SampleException> failed) {
            assertEquals("error", failed.exception().getMessage());
        } else {
            fail("Expected Failure");
        }

        try {
            outcome1.getOrThrow();
            fail("Expected ExecutionException");
        } catch (SampleException received) {
            assertEquals(received, e);
        }
        try {
            outcome2.getOrThrow();
            fail("Expected ExecutionException");
        } catch (SampleException received) {
            assertEquals(received, e);
        } catch (CancellationException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Test
    void outcomeBuildCancelled() throws SampleException {
        final var outcome1 = new Outcome.Cancellation<>();
        final Outcome<String, SampleException> outcome2 = Outcome.cancellation();
        assertEquals(outcome1, outcome2);

        if (!(outcome2 instanceof Outcome.Cancellation<?,?>)) {
            fail("Expected Canceled");
        }

        try {
            outcome1.getOrThrow();
            fail("Expected CancellationException");
        } catch (CancellationException ignored) {
        }
        try {
            outcome2.getOrThrow();
            fail("Expected CancellationException");
        } catch (CancellationException ignored) {
        } 
    }

    @Test
    void outcomeBuildRuntimeFailure() throws SampleException {
        final var e = new RuntimeException("error");
        final var outcome1 = new Outcome.RuntimeFailure<>(e);
        final Outcome<String, SampleException> outcome2 = Outcome.runtimeFailure(e);
        assertEquals(outcome1, outcome2);

        if (outcome2 instanceof Outcome.RuntimeFailure<?, ?> failed) {
            assertEquals("error", failed.exception().getMessage());
        } else {
            fail("Expected Failure");
        }

        try {
            outcome1.getOrThrow();
            fail("Expected RuntimeException");
        } catch (RuntimeException received) {
            assertEquals(received, e);
        }
        try {
            outcome2.getOrThrow();
            fail("Expected RuntimeException");
        } catch (RuntimeException received) {
            assertEquals(received, e);
        } catch (CancellationException ex) {
            throw new RuntimeException(ex);
        }
    }
}

