package org.funfix.tasks;

import org.junit.jupiter.api.Test;
import java.util.concurrent.ExecutionException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class OutcomeTest {
    @Test
    public void outcomeBuildSuccess() {
        final var outcome1 = new Outcome.Succeeded<>("value");
        final var outcome2 = Outcome.succeeded("value");
        assertEquals(outcome1, outcome2);

        if (outcome2 instanceof Outcome.Succeeded<?>) {
            assertEquals("value", ((Outcome.Succeeded<String>)outcome2).getValue());
        } else {
            fail("Expected Success");
        }

        assertEquals("value", outcome1.getOrThrow());
        try {
            assertEquals("value", outcome2.getOrThrow());
        } catch (ExecutionException | CancellationException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void outcomeBuildFailure() {
        final var e = new RuntimeException("error");
        final var outcome1 = new Outcome.Failed(e);
        final var outcome2 = Outcome.failed(e);
        assertEquals(outcome1, outcome2);

        if (outcome2 instanceof Outcome.Failed) {
            assertEquals(
                    "error",
                    ((Outcome.Failed) outcome2).getException().getMessage()
            );
        } else {
            fail("Expected Failure");
        }

        try {
            outcome1.getOrThrow();
            fail("Expected ExecutionException");
        } catch (ExecutionException ex) {
            assertEquals(ex.getCause(), e);
        }
        try {
            outcome2.getOrThrow();
            fail("Expected ExecutionException");
        } catch (ExecutionException | CancellationException ex) {
            assertEquals(ex.getCause(), e);
        }
    }

    @Test
    public void outcomeBuildCancelled() {
        final var outcome1 = Outcome.Cancelled.INSTANCE;
        final var outcome2 = Outcome.<String>cancelled();
        assertEquals(outcome1, outcome2);

        if (!(outcome2 instanceof Outcome.Cancelled)) {
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
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }
}
