package org.funfix.tests;

import org.funfix.tasks.Task;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

public class TaskTest {
    @Test
    public void fromThreadTest() throws ExecutionException, InterruptedException {
        Task<String> task =
            cb -> {
                Thread th = new Thread(() -> cb.onSuccess("Hello, world!"));
                th.start();
                return th::interrupt;
            };
        String result = task.executeBlocking();
        assertEquals("Hello, world!", result);
    }

    @Test
    public void fromExecutorTest() throws ExecutionException, InterruptedException {
        ExecutorService es = Executors.newCachedThreadPool();
        Task<String> task = Task.blockingIO(es, () -> "Hello, world!");
        try {
            String result = task.executeBlocking();
            assertEquals("Hello, world!", result);
        } finally {
            es.shutdown();
        }
    }

    @Test
    public void fromExecutorIsCancellableTest() throws ExecutionException, InterruptedException {
        final long timeoutMillis = 30000;
        ExecutorService es = Executors.newCachedThreadPool();

        Task<Void> task = Task.blockingIO(es, () -> {
            Thread.sleep(timeoutMillis);
            return null;
        });

        long start = System.nanoTime();
        try {
            task.executeBlockingTimed(Duration.ofMillis(50));
            fail("Should have thrown a TimeoutException");
        } catch (TimeoutException ignored) {
        } finally {
            long durationNanos = System.nanoTime() - start;
            assertTrue(durationNanos < Duration.ofMillis(timeoutMillis).toNanos());
            es.shutdown();
        }
    }
}
