package org.funfix.tests;

import org.funfix.tasks.CancellationException;
import org.funfix.tasks.Fiber;
import org.funfix.tasks.NotCompletedException;
import org.funfix.tasks.Task;
import org.junit.jupiter.api.Test;

import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

public class TaskFiberTest {
    @Test
    public void notCompletedException() throws CancellationException, ExecutionException, InterruptedException, NotCompletedException {
        ExecutorService es = Executors.newCachedThreadPool();
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Task<String> task = Task.blockingIO(es, () -> {
                latch.await();
                return "Hello, world!";
            });

            Fiber<String> fiber = task.executeConcurrently();
            try {
                fiber.resultOrThrow();
                fail("Should have thrown a NotCompletedException");
            } catch (NotCompletedException ignored) {}

            latch.countDown();
            fiber.joinBlocking();
            assertEquals("Hello, world!", fiber.resultOrThrow());
        } finally {
            es.shutdown();
        }
    }

    @Test
    public void joinAsync() throws InterruptedException, CancellationException, ExecutionException, NotCompletedException {
        ExecutorService es = Executors.newCachedThreadPool();
        CountDownLatch fiberStarted = new CountDownLatch(1);
        CountDownLatch fiberGo = new CountDownLatch(1);
        CountDownLatch awaitConsumers = new CountDownLatch(3);
        try {
            Fiber<String> fiber = Task
                .blockingIO(es, () -> {
                    fiberStarted.countDown();
                    fiberGo.await();
                    return "Hello, world!";
                }).executeConcurrently();

            assertTrue(fiberStarted.await(5, TimeUnit.SECONDS), "fiberStarted");
            // Adding multiple consumers
            for (int i = 0; i < 3; i++) {
                fiber.joinAsync(awaitConsumers::countDown);
            }
            fiberGo.countDown();
            assertTrue(awaitConsumers.await(5, TimeUnit.SECONDS), "awaitConsumers");
            assertEquals("Hello, world!", fiber.resultOrThrow());
        } finally {
            es.shutdown();
        }
    }

    @Test
    public void canFail() throws CancellationException, NotCompletedException, InterruptedException {
        ExecutorService es = Executors.newCachedThreadPool();
        try {
            Fiber<?> fiber = Task
                .blockingIO(es, () -> {
                    throw new RuntimeException("My Error");
                }).executeConcurrently();

            fiber.joinBlocking();
            try {
                fiber.resultOrThrow();
                fail("Should have thrown an exception");
            } catch (ExecutionException ex) {
                assertEquals("My Error", ex.getCause().getMessage());
            }
        } finally {
            es.shutdown();
        }
    }

    @Test
    public void resultIsMemoized() throws InterruptedException, CancellationException, ExecutionException, NotCompletedException {
        ExecutorService es = Executors.newCachedThreadPool();
        try {
            final Fiber<Integer> fiber = Task
                .blockingIO(es, () -> ThreadLocalRandom.current().nextInt())
                .executeConcurrently();

            fiber.joinBlocking();
            int result = fiber.resultOrThrow();

            fiber.joinBlocking();
            int result2 = fiber.resultOrThrow();

            assertEquals(result, result2);
        } finally {
            es.shutdown();
        }
    }

    @Test
    public void joinCanBeInterrupted() throws InterruptedException, ExecutionException, NotCompletedException, CancellationException {
        ExecutorService es = Executors.newCachedThreadPool();
        final CountDownLatch latch = new CountDownLatch(1);
        final CountDownLatch started = new CountDownLatch(1);
        try {
            Fiber<Boolean> fiber = Task
                .blockingIO(es, () -> latch.await(10, TimeUnit.SECONDS))
                .executeConcurrently();

            Fiber<Boolean> fiber2 = Task
                .blockingIO(es, () -> {
                    started.countDown();
                    fiber.joinBlocking();
                    return fiber.resultOrThrow();
                })
                .executeConcurrently();

            assertTrue(started.await(5, TimeUnit.SECONDS), "started");
            fiber2.cancel();
            fiber2.joinBlocking();
            try {
                fiber2.resultOrThrow();
                fail("Should have thrown a CancellationException");
            } catch (CancellationException ignored) {}

            latch.countDown();
            fiber.joinBlocking();
            assertTrue(fiber.resultOrThrow());
        } finally {
            es.shutdown();
        }
    }
}
