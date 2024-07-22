package org.funfix.tasks.jvm;

import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

@NullMarked
public class OutcomeTest {
    @Test
    void outcomeBuildSuccess() {
        final var outcome1 = new Outcome.Success<>("value");
        final Outcome<String> outcome2 = Outcome.success("value");
        assertEquals(outcome1, outcome2);

        if (outcome2 instanceof Outcome.Success) {
            final var success = (Outcome.Success<String>) outcome2;
            assertEquals("value", success.value());
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
    void outcomeBuildCancellation() throws ExecutionException {
        final var outcome1 = new Outcome.Cancellation<>();
        final Outcome<String> outcome2 = Outcome.cancellation();
        assertEquals(outcome1, outcome2);

        if (!(outcome2 instanceof Outcome.Cancellation)) {
            fail("Expected Canceled");
        }

        try {
            outcome1.getOrThrow();
            fail("Expected CancellationException");
        } catch (TaskCancellationException ignored) {
        }
        try {
            outcome2.getOrThrow();
            fail("Expected CancellationException");
        } catch (TaskCancellationException ignored) {
        } 
    }

    @Test
    void outcomeBuildRuntimeFailure() {
        final var e = new RuntimeException("error");
        final var outcome1 = new Outcome.Failure<>(e);
        final Outcome<String> outcome2 = Outcome.failure(e);
        assertEquals(outcome1, outcome2);

        if (outcome2 instanceof Outcome.Failure) {
            final var failure = (Outcome.Failure<String>) outcome2;
            assertEquals("error", failure.exception().getMessage());
        } else {
            fail("Expected Failure");
        }

        try {
            outcome1.getOrThrow();
            fail("Expected RuntimeException");
        } catch (ExecutionException received) {
            assertEquals(received.getCause(), e);
        }
        try {
            outcome2.getOrThrow();
            fail("Expected RuntimeException");
        } catch (ExecutionException received) {
            assertEquals(received.getCause(), e);
        } catch (TaskCancellationException ex) {
            throw new RuntimeException(ex);
        }
    }
}

