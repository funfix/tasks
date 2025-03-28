package org.funfix.tasks.jvm;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

public class TaskFromCompletableFutureTest {
    @Test
    public void happyPath()
        throws ExecutionException, InterruptedException {

        final var es = Executors.newCachedThreadPool();
        try {
            final var isSuspended = new AtomicBoolean(true);
            final var task =
                Task.fromCompletionStage(() -> {
                    isSuspended.set(false);
                    return CompletableFuture.supplyAsync(
                        () -> "Hello, world!",
                        es
                    );
                });
            // Test that the future is suspended
            assertTrue(isSuspended.get(), "Future should be suspended");

            final var result = task.runBlocking();
            assertFalse(isSuspended.get(), "Future should have been executed");
            assertEquals("Hello, world!", result);
        } finally {
            es.shutdown();
        }
    }

    @Test
    public void yieldingErrorInsideFuture() throws InterruptedException {
        final var es = Executors.newCachedThreadPool();
        try {
            final Task<String> task =
                Task.fromCompletionStage(() ->
                    CompletableFuture.supplyAsync(
                        () -> {
                            throw new SampleException("Error");
                        },
                        es
                    )
                );

            try {
                task.runBlocking();
                fail("Should have thrown an exception");
            } catch (final ExecutionException ex) {
                assertInstanceOf(SampleException.class, ex.getCause(), "Should have received a SampleException");
            }
        } finally {
            es.shutdown();
        }
    }

    @Test
    public void yieldingErrorInBuilder() throws InterruptedException {
        final Task<String> task =
            Task.fromCompletionStage(() -> {
                throw new SampleException("Error");
            });
        try {
            task.runBlocking();
            fail("Should have thrown an exception");
        } catch (final ExecutionException ex) {
            assertInstanceOf(SampleException.class, ex.getCause(), "Should have received a SampleException");
        }
    }

    static final class SampleException extends RuntimeException {
        public SampleException(final String message) {
            super(message);
        }
    }
}
