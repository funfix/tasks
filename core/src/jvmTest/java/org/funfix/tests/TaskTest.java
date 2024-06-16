package org.funfix.tests;

import org.funfix.tasks.Task;
import org.junit.jupiter.api.Test;

import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TaskTest {
    @Test
    public void fromFutureTest() throws ExecutionException, InterruptedException {
        ExecutorService es = Executors.newCachedThreadPool();
        CountDownLatch latch = new CountDownLatch(1);
        Task<String> task =
            Task.fromFuture(() ->
                CompletableFuture.supplyAsync(
                    () -> {
                        latch.countDown();
                        return "Hello, world!";
                    },
                    es
                )
            );
        assertEquals(1, latch.getCount());
        try {
            String result = task.executeBlocking();
            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertEquals("Hello, world!", result);
        } finally {
            es.shutdown();
        }
    }

    @Test
    public void fromAsyncTest() throws ExecutionException, InterruptedException {
        ExecutorService es = Executors.newCachedThreadPool();
        CountDownLatch latch = new CountDownLatch(1);
        Task<String> task =
            cb -> {
                Future<?> f = es.submit(() -> {
                    cb.success("Hello, world!");
                    latch.countDown();
                });
                return () -> {
                    if (f.cancel(false))
                        cb.cancel();
                };
            };
        assertEquals(1, latch.getCount());
        try {
            String result = task.executeBlocking();
            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertEquals("Hello, world!", result);
        } finally {
            es.shutdown();
        }
    }
}
