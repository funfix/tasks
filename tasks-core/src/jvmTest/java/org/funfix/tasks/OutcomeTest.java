package org.funfix.tasks;

import org.junit.jupiter.api.Test;
import java.util.concurrent.ExecutionException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class OutcomeTest {
    @Test
    public void outcomeBuildSuccess() {
        final var outcome1 = Outcome.Success.instance("value");
        final var outcome2 = Outcome.success("value");
        assertEquals(outcome1, outcome2);

        if (outcome2 instanceof Outcome.Success<?>) {
            assertEquals("value", ((Outcome.Success<String>)outcome2).value());
        } else {
            fail("Expected Success");
        }

        assertEquals("value", ((Outcome.Success<String>) outcome1).getOrThrow());
        try {
            assertEquals("value", outcome2.getOrThrow());
        } catch (final ExecutionException | TaskCancellationException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void outcomeBuildFailure() {
        final var e = new RuntimeException("error");
        final var outcome1 = Outcome.Failure.instance(e);
        final var outcome2 = Outcome.failure(e);
        assertEquals(outcome1, outcome2);

        if (outcome2 instanceof Outcome.Failure) {
            assertEquals(
                    "error",
                    ((Outcome.Failure) outcome2).exception().getMessage()
            );
        } else {
            fail("Expected Failure");
        }

        try {
            ((Outcome.Failure) outcome1).getOrThrow();
            fail("Expected ExecutionException");
        } catch (ExecutionException ex) {
            assertEquals(ex.getCause(), e);
        }
        try {
            outcome2.getOrThrow();
            fail("Expected ExecutionException");
        } catch (ExecutionException | TaskCancellationException ex) {
            assertEquals(ex.getCause(), e);
        }
    }

    @Test
    public void outcomeBuildCancelled() {
        final var outcome1 = Outcome.Cancellation.instance();
        final Outcome<String> outcome2 = Outcome.cancellation();
        assertEquals(outcome1, outcome2);

        if (!(outcome2 instanceof Outcome.Cancellation)) {
            fail("Expected Canceled");
        }

        try {
            ((Outcome.Cancellation)outcome1).getOrThrow();
            fail("Expected CancellationException");
        } catch (TaskCancellationException ignored) {
        }
        try {
            outcome2.getOrThrow();
            fail("Expected CancellationException");
        } catch (TaskCancellationException ignored) {
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }
}
