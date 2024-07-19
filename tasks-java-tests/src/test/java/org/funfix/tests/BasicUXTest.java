package org.funfix.tests;

import org.funfix.tasks.Outcome;
import org.funfix.tasks.Task;
import org.funfix.tasks.TaskCancellationException;
import org.junit.jupiter.api.Test;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class BasicUXTest {
    @Test
    void executeBlocking() throws ExecutionException, InterruptedException {
        final var task = Task.fromBlockingIO(() -> "Hello, world!");
        final var r = task.executeBlocking();
        assertEquals("Hello, world!", r);
    }

    @Test
    void executeBlockingInterrupted() throws ExecutionException {
        final var task = Task.fromBlockingIO(() -> {
            throw new InterruptedException();
        });
        try {
            task.executeBlocking();
            fail("Should have thrown an exception");
        } catch (InterruptedException e) {
            // expected
        }
    }

    @Test
    void executeBlockingFailed() throws InterruptedException {
        final var task = Task.fromBlockingIO(() -> {
            throw new RuntimeException("Boom!");
        });
        try {
            task.executeBlocking();
            fail("Should have thrown an exception");
        } catch (ExecutionException e) {
            assertEquals("Boom!", e.getCause().getMessage());
        }
    }

    @Test
    void outcomeSuccess() throws TaskCancellationException, ExecutionException {
        final var outcome1 = Outcome.Success.instance("Hello, world!");
        final var outcome2 = Outcome.success("Hello, world!");
        assertEquals(outcome1, outcome2);
        assertEquals(outcome1.getOrThrow(), outcome2.getOrThrow());
        assertEquals("Hello, world!", outcome2.getOrThrow());
    }
}
