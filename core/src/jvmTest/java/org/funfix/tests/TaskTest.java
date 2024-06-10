package org.funfix.tests;

import org.funfix.tasks.Task;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.*;

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
}
