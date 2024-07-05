package org.funfix.tests;

import org.funfix.tasks.CancellationException;
import org.funfix.tasks.Fiber;
import org.funfix.tasks.NotCompletedException;
import org.funfix.tasks.Task;
import org.junit.jupiter.api.Test;

import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

public class TaskFromBlockingIOTest {
    @Test
    public void happyPath() throws ExecutionException, InterruptedException {
        ExecutorService es = Executors.newCachedThreadPool();
        try {
            String r = Task.blockingIO(es, () -> "Hello, world!").executeBlocking();
            assertEquals("Hello, world!", r);
        } finally {
            es.shutdown();
        }
    }

    @Test
    public void canFail() throws InterruptedException {
        ExecutorService es = Executors.newCachedThreadPool();
        try {
            Task.blockingIO(es, () -> {
                throw new RuntimeException("Error");
            }).executeBlocking();
            fail("Should have thrown an exception");
        } catch (ExecutionException ex) {
            assertEquals("Error", ex.getCause().getMessage());
        } finally {
            es.shutdown();
        }
    }

    @Test
    public void isCancellable() throws InterruptedException, ExecutionException, NotCompletedException {
        ExecutorService es = Executors.newCachedThreadPool();
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Task<Void> task = Task.blockingIO(es, () -> {
                latch.countDown();
                Thread.sleep(30000);
                return null;
            });
            long start = System.nanoTime();
            Fiber<Void> fiber = task.executeConcurrently();
            assertTrue(
                latch.await(5, TimeUnit.SECONDS),
                "latch"
            );

            fiber.cancel();
            fiber.joinBlocking();
            try {
                fiber.resultOrThrow();
                fail("Should have thrown a CancellationException");
            } catch (CancellationException ignored) {}
        } finally {
            es.shutdown();
        }
    }
}
