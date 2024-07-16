package org.funfix.tasks;

import org.junit.jupiter.api.Test;
import java.util.concurrent.ExecutionException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class OutcomeTest {
    @Test
    public void outcomeBuildSuccess() {
        final var outcome1 = Outcome.Succeeded.instance("value");
        final var outcome2 = Outcome.succeeded("value");
        assertEquals(outcome1, outcome2);

        if (outcome2 instanceof Outcome.Succeeded<?>) {
            assertEquals("value", ((Outcome.Succeeded<String>)outcome2).value());
        } else {
            fail("Expected Success");
        }

        assertEquals("value", ((Outcome.Succeeded<String>) outcome1).getOrThrow());
        try {
            assertEquals("value", outcome2.getOrThrow());
        } catch (ExecutionException | TaskCancellationException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void outcomeBuildFailure() {
        final var e = new RuntimeException("error");
        final var outcome1 = Outcome.Failed.instance(e);
        final var outcome2 = Outcome.failed(e);
        assertEquals(outcome1, outcome2);

        if (outcome2 instanceof Outcome.Failed) {
            assertEquals(
                    "error",
                    ((Outcome.Failed) outcome2).exception().getMessage()
            );
        } else {
            fail("Expected Failure");
        }

        try {
            ((Outcome.Failed) outcome1).getOrThrow();
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
        final var outcome1 = Outcome.Cancelled.instance();
        final Outcome<String> outcome2 = Outcome.cancelled();
        assertEquals(outcome1, outcome2);

        if (!(outcome2 instanceof Outcome.Cancelled)) {
            fail("Expected Canceled");
        }

        try {
            ((Outcome.Cancelled)outcome1).getOrThrow();
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
