package org.funfix.tests;

import org.funfix.tasks.CancellationException;
import org.funfix.tasks.Task;
import org.junit.jupiter.api.Test;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class TaskFromBlockingIOTest {
    @Test
    public void happyPath() throws ExecutionException, InterruptedException {
        final var es = Executors.newCachedThreadPool();
        try {
            final var r = Task.fromBlockingIO(es, () -> "Hello, world!").executeBlocking();
            assertEquals("Hello, world!", r);
        } finally {
            es.shutdown();
        }
    }

    @Test
    public void canFail() throws InterruptedException {
        final var es = Executors.newCachedThreadPool();
        try {
            Task.fromBlockingIO(es, () -> {
                throw new RuntimeException("Error");
            }).executeBlocking();
            fail("Should have thrown an exception");
        } catch (final ExecutionException ex) {
            assertEquals("Error", ex.getCause().getMessage());
        } finally {
            es.shutdown();
        }
    }

    @Test
    public void isCancellable() throws InterruptedException, ExecutionException {
        final var es = Executors.newCachedThreadPool();
        final var latch = new CountDownLatch(1);
        try {
            final Task<Void> task = Task.fromBlockingIO(es, () -> {
                latch.countDown();
                Thread.sleep(30000);
                return null;
            });
            final var start = System.nanoTime();
            final var fiber = task.executeConcurrently();
            assertTrue(
                latch.await(5, TimeUnit.SECONDS),
                "latch"
            );

            fiber.cancel();
            fiber.joinBlocking();
            try {
                Objects.requireNonNull(fiber.outcome()).getOrThrow();
                fail("Should have thrown a CancellationException");
            } catch (final CancellationException ignored) {}
        } finally {
            es.shutdown();
        }
    }
}
