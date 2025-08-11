package org.funfix.tasks.jvm;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.funfix.tasks.jvm.TestSettings.CONCURRENCY_REPEATS;
import static org.funfix.tasks.jvm.TestSettings.TIMEOUT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class TaskWithOnCompletionTest {
    @Test
    void guaranteeOnSuccess() throws ExecutionException, InterruptedException {
        for (int t = 0; t < CONCURRENCY_REPEATS; t++) {
            final var ref1 = new AtomicReference<@Nullable Outcome<String>>(null);
            final var ref2 = new AtomicReference<@Nullable Outcome<String>>(null);
            final var outcome1 = Task
                .fromBlockingIO(() -> "Success")
                .withOnComplete(ref1::set)
                .withOnComplete(ref2::set)
                .runBlocking();

            assertEquals("Success", outcome1);
            assertEquals(Outcome.success("Success"), ref1.get());
            assertEquals(Outcome.success("Success"), ref2.get());
        }
    }

    @Test
    void guaranteeOnSuccessWithFibers() throws ExecutionException, InterruptedException, TaskCancellationException {
        for (int t = 0; t < CONCURRENCY_REPEATS; t++) {
            final var ref1 = new AtomicReference<@Nullable Outcome<String>>(null);
            final var ref2 = new AtomicReference<@Nullable Outcome<String>>(null);
            final var fiber = Task
                .fromBlockingIO(() -> "Success")
                .withOnComplete(ref1::set)
                .withOnComplete(ref2::set)
                .runFiber();

            assertEquals("Success", fiber.awaitBlocking());
            assertEquals(Outcome.success("Success"), ref1.get());
            assertEquals(Outcome.success("Success"), ref2.get());
        }
    }

    @Test
    void guaranteeOnSuccessWithBlockingIO() throws ExecutionException, InterruptedException, TimeoutException {
        for (int t = 0; t < CONCURRENCY_REPEATS; t++) {
            final var ref1 = new AtomicReference<@Nullable Outcome<String>>(null);
            final var ref2 = new AtomicReference<@Nullable Outcome<String>>(null);
            final var r = Task
                .fromBlockingIO(() -> "Success")
                .withOnComplete(ref1::set)
                .withOnComplete(ref2::set)
                .runBlockingTimed(TIMEOUT);

            assertEquals("Success", r);
            assertEquals(Outcome.success("Success"), ref1.get());
            assertEquals(Outcome.success("Success"), ref2.get());
        }
    }


    @Test
    void guaranteeOnFailure() throws InterruptedException {
        for (int t = 0; t < CONCURRENCY_REPEATS; t++) {
            final var ref1 = new AtomicReference<@Nullable Outcome<String>>(null);
            final var ref2 = new AtomicReference<@Nullable Outcome<String>>(null);
            final var error = new RuntimeException("Failure");

            try {
                Task.<String>fromBlockingIO(() -> {
                        throw error;
                    })
                    .withOnComplete(ref1::set)
                    .withOnComplete(ref2::set)
                    .runBlocking();
                fail("Expected ExecutionException");
            } catch (ExecutionException e) {
                assertEquals(error, e.getCause());
            }

            assertEquals(Outcome.failure(error), ref1.get());
            assertEquals(Outcome.failure(error), ref2.get());
        }
    }

    @Test
    void guaranteeOnFailureWithFibers() throws InterruptedException, TaskCancellationException {
        for (int t = 0; t < CONCURRENCY_REPEATS; t++) {
            final var ref1 = new AtomicReference<@Nullable Outcome<String>>(null);
            final var ref2 = new AtomicReference<@Nullable Outcome<String>>(null);
            final var error = new RuntimeException("Failure");

            try {
                Task.<String>fromBlockingIO(() -> {
                        throw error;
                    })
                    .withOnComplete(ref1::set)
                    .withOnComplete(ref2::set)
                    .runFiber()
                    .awaitBlocking();
                fail("Expected ExecutionException");
            } catch (ExecutionException e) {
                assertEquals(error, e.getCause());
            }

            assertEquals(Outcome.failure(error), ref1.get());
            assertEquals(Outcome.failure(error), ref2.get());
        }
    }

    @Test
    void guaranteeOnFailureBlockingIO() throws InterruptedException {
        for (int t = 0; t < CONCURRENCY_REPEATS; t++) {
            final var ref1 = new AtomicReference<@Nullable Outcome<String>>(null);
            final var ref2 = new AtomicReference<@Nullable Outcome<String>>(null);
            final var error = new RuntimeException("Failure");

            try {
                Task.<String>fromBlockingIO(() -> {
                        throw error;
                    })
                    .withOnComplete(ref1::set)
                    .withOnComplete(ref2::set)
                    .runBlockingTimed(TIMEOUT);
                fail("Expected ExecutionException");
            } catch (ExecutionException | TimeoutException e) {
                assertEquals(error, e.getCause());
            }

            assertEquals(Outcome.failure(error), ref1.get());
            assertEquals(Outcome.failure(error), ref2.get());
        }
    }

    @Test
    void guaranteeOnCancellation() throws InterruptedException, ExecutionException, TimeoutException {
        for (int t = 0; t < CONCURRENCY_REPEATS; t++) {
            final var ref1 = new AtomicReference<@Nullable Outcome<String>>(null);
            final var ref2 = new AtomicReference<@Nullable Outcome<String>>(null);
            final var latch = new CountDownLatch(1);

            final var task = Task
                .fromBlockingIO(() -> {
                    latch.await();
                    return "Should not complete";
                })
                .ensureRunningOnExecutor()
                .withOnComplete(ref1::set)
                .withOnComplete(ref2::set);

            final var fiber = task.runFiber();
            fiber.cancel();

            try {
                fiber.awaitBlockingTimed(TIMEOUT);
                fail("Expected TaskCancellationException");
            } catch (TaskCancellationException e) {
                // Expected
            } catch (TimeoutException e) {
                latch.countDown();
                throw e;
            }

            assertEquals(Outcome.cancellation(), ref1.get());
            assertEquals(Outcome.cancellation(), ref2.get());
        }
    }

    @Test
    void callOrdering() throws InterruptedException {
        for (int t = 0; t < CONCURRENCY_REPEATS; t++) {
            final var latch = new CountDownLatch(1);
            final var ref = new ConcurrentLinkedQueue<Integer>();

            Task.fromBlockingIO(() -> 0)
                .ensureRunningOnExecutor()
                .withOnComplete((ignored) -> ref.add(1))
                .withOnComplete((ignored) -> ref.add(2))
                .runAsync((ignored) -> {
                    ref.add(3);
                    latch.countDown();
                });

            TimedAwait.latchAndExpectCompletion(latch, "latch");
            assertEquals(3, ref.size(), "Expected 3 calls to onCompletion");
            final var arr = ref.toArray(new Integer[0]);
            for (int i = 0; i < 3; i++) {
                assertEquals(i + 1, arr[i]);
            }
        }
    }

    @Test
    void callOrderingViaFibers() throws Exception {
        for (int t = 0; t < CONCURRENCY_REPEATS; t++) {
            final var ref = new ConcurrentLinkedQueue<Integer>();

            final var fiber = Task.fromBlockingIO(() -> 0)
                .ensureRunningOnExecutor()
                .withOnComplete((ignored) -> ref.add(1))
                .withOnComplete((ignored) -> ref.add(2))
                .runFiber();

            assertEquals(0, fiber.awaitBlockingTimed(TIMEOUT));
            assertEquals(2, ref.size(), "Expected 2 calls to onCompletion");
            final var arr = ref.toArray(new Integer[0]);
            for (int i = 0; i < arr.length; i++) {
                assertEquals(i + 1, arr[i]);
            }
        }
    }

}
