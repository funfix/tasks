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
        final Outcome<String> outcome2 = Outcome.succeeded("value");
        assertEquals(outcome1, outcome2);

        if (outcome2 instanceof Outcome.Success<String> success) {
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
    void outcomeBuildCancelled() throws ExecutionException {
        final var outcome1 = new Outcome.Cancelled<>();
        final Outcome<String> outcome2 = Outcome.cancelled();
        assertEquals(outcome1, outcome2);

        if (!(outcome2 instanceof Outcome.Cancelled<String>)) {
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
    void outcomeBuildRuntimeFailure() {
        final var e = new RuntimeException("error");
        final var outcome1 = new Outcome.Failed<>(e);
        final Outcome<String> outcome2 = Outcome.failed(e);
        assertEquals(outcome1, outcome2);

        if (outcome2 instanceof Outcome.Failed<?> failed) {
            assertEquals("error", failed.exception().getMessage());
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
        } catch (CancellationException ex) {
            throw new RuntimeException(ex);
        }
    }
}

