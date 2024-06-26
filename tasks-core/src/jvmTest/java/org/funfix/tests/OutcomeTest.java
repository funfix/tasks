package org.funfix.tests;

import org.funfix.tasks.Outcome;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class OutcomeTest {
    @Test
    public void outcomeBuildSuccess() {
        var outcome1 = new Outcome.Success<>("value");
        var outcome2 = Outcome.success("value");
        assertEquals(outcome1, outcome2);

        if (outcome2 instanceof Outcome.Success<String> success) {
            assertEquals("value", success.value());
        } else {
            fail("Expected Success");
        }
    }
}
