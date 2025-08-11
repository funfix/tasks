package org.funfix.tasks.jvm;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class TaskWithCancellationTest {
    @Test
    void testTaskWithCancellation() throws InterruptedException {
        for (int r = 0; r < TestSettings.CONCURRENCY_REPEATS; r++) {
            final var cancelTokensRef = new ConcurrentLinkedQueue<Integer>();
            final var outcomeRef = new AtomicReference<@Nullable Outcome<String>>(null);

            final var startedLatch = new CountDownLatch(1);
            final var taskLatch = new CountDownLatch(1);
            final var cancelTokensLatch = new CountDownLatch(2);
            final var completedLatch = new CountDownLatch(1);
            final var runningTask = Task
                .fromBlockingIO(() -> {
                    try {
                        startedLatch.countDown();
                        taskLatch.await();
                        return "Completed";
                    } catch (final InterruptedException e) {
                        TimedAwait.latchNoExpectations(cancelTokensLatch);
                        cancelTokensRef.add(3);
                        throw e;
                    }
                })
                .ensureRunningOnExecutor()
                .withCancellation(() -> {
                    cancelTokensRef.add(1);
                    cancelTokensLatch.countDown();
                })
                .withCancellation(() -> {
                    cancelTokensRef.add(2);
                    cancelTokensLatch.countDown();
                })
                .runAsync(outcome -> {
                    outcomeRef.set(outcome);
                    completedLatch.countDown();
                });

            TimedAwait.latchAndExpectCompletion(startedLatch, "startedLatch");
            runningTask.cancel();
            TimedAwait.latchAndExpectCompletion(completedLatch, "completedLatch");
            assertEquals(Outcome.cancellation(), outcomeRef.get());

            final var arr = cancelTokensRef.toArray(new Integer[0]);
            for (int i = 0; i < arr.length; i++) {
                assertEquals(i + 1, arr[i], "cancelTokensRef[" + i + "] should be " + (i + 1));
            }
            assertEquals(3, arr.length, "cancelTokensRef should have 3 elements");
        }
    }

    @Test
    void testTaskWithCancellationAndFibers() throws Exception {
        for (int r = 0; r < TestSettings.CONCURRENCY_REPEATS; r++) {
            final var cancelTokensRef = new ConcurrentLinkedQueue<Integer>();
            final var startedLatch = new CountDownLatch(1);
            final var taskLatch = new CountDownLatch(1);
            final var cancelTokensLatch = new CountDownLatch(2);

            final var fiber = Task
                .fromBlockingIO(() -> {
                    try {
                        startedLatch.countDown();
                        taskLatch.await();
                        return "Completed";
                    } catch (final InterruptedException e) {
                        TimedAwait.latchNoExpectations(cancelTokensLatch);
                        cancelTokensRef.add(3);
                        throw e;
                    }
                })
                .ensureRunningOnExecutor()
                .withCancellation(() -> {
                    cancelTokensRef.add(1);
                    cancelTokensLatch.countDown();
                })
                .withCancellation(() -> {
                    cancelTokensRef.add(2);
                    cancelTokensLatch.countDown();
                })
                .runFiber();

            TimedAwait.latchAndExpectCompletion(startedLatch, "startedLatch");
            fiber.cancel();
            TimedAwait.fiberAndExpectCancellation(fiber);
            try {
                fiber.getResultOrThrow();
                fail("Should have thrown a TaskCancellationException");
            } catch (final TaskCancellationException e) {
                // Expected
            }

            final var arr = cancelTokensRef.toArray(new Integer[0]);
            for (int i = 0; i < arr.length; i++) {
                assertEquals(i + 1, arr[i], "cancelTokensRef[" + i + "] should be " + (i + 1));
            }
            assertEquals(3, arr.length, "cancelTokensRef should have 3 elements");
        }
    }

}
