package org.funfix.tests;

import org.funfix.tasks.Task;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

public class TaskFromCompletableFutureTest {
    @Test
    public void happyPath()
        throws ExecutionException, InterruptedException {

        final ExecutorService es = Executors.newCachedThreadPool();
        try {
            final AtomicBoolean isSuspended = new AtomicBoolean(true);
            Task<String> task =
                Task.fromCompletableFuture(() -> {
                    isSuspended.set(false);
                    return CompletableFuture.supplyAsync(
                        () -> "Hello, world!",
                        es
                    );
                });
            // Test that the future is suspended
            assertTrue(isSuspended.get(), "Future should be suspended");

            String result = task.executeBlocking();
            assertFalse(isSuspended.get(), "Future should have been executed");
            assertEquals("Hello, world!", result);
        } finally {
            es.shutdown();
        }
    }

    @Test
    public void yieldingErrorInsideFuture() throws InterruptedException {
        ExecutorService es = Executors.newCachedThreadPool();
        Task<String> task =
            Task.fromCompletableFuture(() ->
                CompletableFuture.supplyAsync(
                    () -> {
                        throw new SampleException("Error");
                    },
                    es
                )
            );

        try {
            task.executeBlocking();
            fail("Should have thrown an exception");
        } catch (ExecutionException ex) {
            assertInstanceOf(SampleException.class, ex.getCause(), "Should have received a SampleException");
        } finally {
            es.shutdown();
        }
    }

    @Test
    public void yieldingErrorInBuilder() throws InterruptedException {
        ExecutorService es = Executors.newCachedThreadPool();
        Task<String> task =
            Task.fromCompletableFuture(() -> {
                throw new SampleException("Error");
            });

        try {
            task.executeBlocking();
            fail("Should have thrown an exception");
        } catch (ExecutionException ex) {
            assertInstanceOf(SampleException.class, ex.getCause(), "Should have received a SampleException");
        } finally {
            es.shutdown();
        }
    }

    static final class SampleException extends RuntimeException {
        public SampleException(String message) {
            super(message);
        }
    }
}
