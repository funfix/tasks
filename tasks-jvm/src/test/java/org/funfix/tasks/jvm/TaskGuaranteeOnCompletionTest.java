package org.funfix.tasks.jvm;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.funfix.tasks.jvm.TestSettings.TIMEOUT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class TaskGuaranteeOnCompletionTest {
    @Test
    void guaranteeOnSuccess() throws ExecutionException, InterruptedException {
        final var ref1 = new AtomicReference<@Nullable Outcome<String>>(null);
        final var ref2 = new AtomicReference<@Nullable Outcome<String>>(null);
        final var outcome1 = Task
            .fromBlockingIO(() -> "Success")
            .onCompletion(ref1::set)
            .onCompletion(ref2::set)
            .runBlocking();

        assertEquals("Success", outcome1);
        assertEquals(Outcome.success("Success"), ref1.get());
        assertEquals(Outcome.success("Success"), ref2.get());
    }

    @Test
    void guaranteeOnSuccessWithFibers() throws ExecutionException, InterruptedException, TaskCancellationException {
        final var ref1 = new AtomicReference<@Nullable Outcome<String>>(null);
        final var ref2 = new AtomicReference<@Nullable Outcome<String>>(null);
        final var fiber = Task
            .fromBlockingIO(() -> "Success")
            .onCompletion(ref1::set)
            .onCompletion(ref2::set)
            .runFiber();

        assertEquals("Success", fiber.awaitBlocking());
        assertEquals(Outcome.success("Success"), ref1.get());
        assertEquals(Outcome.success("Success"), ref2.get());
    }

    @Test
    void guaranteeOnFailure() throws InterruptedException {
        final var ref1 = new AtomicReference<@Nullable Outcome<String>>(null);
        final var ref2 = new AtomicReference<@Nullable Outcome<String>>(null);
        final var error = new RuntimeException("Failure");

        try {
            Task.<String>fromBlockingIO(() -> {
                    throw error;
                })
                .onCompletion(ref1::set)
                .onCompletion(ref2::set)
                .runBlocking();
            fail("Expected ExecutionException");
        } catch (ExecutionException e) {
            assertEquals(error, e.getCause());
        }

        assertEquals(Outcome.failure(error), ref1.get());
        assertEquals(Outcome.failure(error), ref2.get());
    }

    @Test
    void guaranteeOnFailureWithFibers() throws InterruptedException, TaskCancellationException {
        final var ref1 = new AtomicReference<@Nullable Outcome<String>>(null);
        final var ref2 = new AtomicReference<@Nullable Outcome<String>>(null);
        final var error = new RuntimeException("Failure");

        try {
            Task.<String>fromBlockingIO(() -> {
                    throw error;
                })
                .onCompletion(ref1::set)
                .onCompletion(ref2::set)
                .runFiber()
                .awaitBlocking();
            fail("Expected ExecutionException");
        } catch (ExecutionException e) {
            assertEquals(error, e.getCause());
        }

        assertEquals(Outcome.failure(error), ref1.get());
        assertEquals(Outcome.failure(error), ref2.get());
    }

    @Test
    void guaranteeOnCancellation() throws InterruptedException, ExecutionException, TimeoutException {
        final var ref1 = new AtomicReference<@Nullable Outcome<String>>(null);
        final var ref2 = new AtomicReference<@Nullable Outcome<String>>(null);
        final var latch = new CountDownLatch(1);

        final var task = Task
            .fromBlockingIO(() -> {
                latch.await();
                return "Should not complete";
            })
            .onCompletion(ref1::set)
            .onCompletion(ref2::set);

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
