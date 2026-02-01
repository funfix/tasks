package org.funfix.tasks.jvm;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PureTest {
    @Test
    void pureTask() throws ExecutionException, InterruptedException {
        final var task = Task.pure(42);
        for (int i = 0; i < 100; i++) {
            final var outcome = task.runBlocking();
            assertEquals(42, outcome);
        }
    }

    @Test
    void pureResource() throws ExecutionException, InterruptedException {
        final var resource = Resource.pure(42);
        for (int i = 0; i < 100; i++) {
            final var outcome = resource.useBlocking(value -> value);
            assertEquals(42, outcome);
        }
    }
}
