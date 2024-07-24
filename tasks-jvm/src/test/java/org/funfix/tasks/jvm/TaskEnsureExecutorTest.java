package org.funfix.tasks.jvm;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@NullMarked
public class TaskEnsureExecutorTest {
    @Test
    void testEnsureExecutorOnFiber() throws ExecutionException, InterruptedException {
        final var task = Task
            .fromBlockingIO(() ->
                Thread.currentThread().getName()
            );

        final var r1 = task.runBlocking();
        final var r2 = task.ensureRunningOnExecutor().runBlocking();

        assertEquals(
            Thread.currentThread().getName(),
            r1
        );
        assertTrue(
            Objects.requireNonNull(r2).startsWith("tasks-io-"),
            "Expected thread name to start with 'tasks-io-', but was: " + r2
        );
    }

    @Test
    void ensureRunningOnSpecificExecutor() throws ExecutionException, InterruptedException {
        final var ec = Executors.newCachedThreadPool(
            th -> {
                final var t = new Thread(th);
                t.setName("my-threads-" + t.getId());
                return t;
            });
        try {
            final var task = Task
                .fromBlockingIO(() ->
                    Thread.currentThread().getName()
                );

            final var r = task.ensureRunningOnExecutor(ec).runBlocking();
            assertTrue(
                Objects.requireNonNull(r).startsWith("my-threads-"),
                "Expected thread name to start with 'my-threads-', but was: " + r
            );

        } finally {
            ec.shutdown();
        }
    }

    @Test
    void switchesBackOnCallbackForRunAsync() throws InterruptedException {
        final var ec1 = Executors.newCachedThreadPool(
            th -> {
                final var t = new Thread(th);
                t.setName("executing-thread-" + t.getId());
                return t;
            });
        final var ec2 = Executors.newCachedThreadPool(
            th -> {
                final var t = new Thread(th);
                t.setName("callback-thread-" + t.getId());
                return t;
            });
        try {
            final var threadName1 = new AtomicReference<@Nullable String>(null);
            final var threadName2 = new AtomicReference<@Nullable String>(null);
            final var isDone = new CountDownLatch(1);
            final var r = Task
                .fromBlockingIO(() -> Thread.currentThread().getName())
                .ensureRunningOnExecutor(ec1)
                .runAsync(ec2, new CompletionCallback<>() {
                    @Override
                    public void onSuccess(String value) {
                        threadName1.set(value);
                        threadName2.set(Thread.currentThread().getName());
                        isDone.countDown();
                    }

                    @Override
                    public void onFailure(Throwable e) {
                        UncaughtExceptionHandler.logOrRethrow(e);
                        isDone.countDown();
                    }

                    @Override
                    public void onCancellation() {
                        isDone.countDown();
                    }
                });

            TimedAwait.latchAndExpectCompletion(isDone, "isDone");
            assertTrue(
                Objects.requireNonNull(threadName1.get()).startsWith("executing-thread-"),
                "Expected thread name to start with 'executing-thread-', but was: " + threadName1
            );
            assertTrue(
                Objects.requireNonNull(threadName2.get()).startsWith("callback-thread-"),
                "Expected thread name to start with 'callback-thread-', but was: " + threadName2
            );
        } finally {
            ec1.shutdown();
            ec2.shutdown();
        }
    }

    @Test
    void testEnsureExecutorOnFiberAfterCompletion() throws ExecutionException, InterruptedException, TaskCancellationException {
        final var task = Task.fromBlockingIO(() -> Thread.currentThread().getName());
        final var fiber = task.runFiber();

        final var r1 = fiber.awaitBlocking();
        assertTrue(
            Objects.requireNonNull(r1).startsWith("tasks-io-"),
            "Expected thread name to start with 'tasks-io-', but was: " + r1
        );

        final var isComplete = new CountDownLatch(1);
        final var r2 = new AtomicReference<@Nullable Outcome<String>>(null);
        fiber.awaitAsync(new CallbackBasedOnOutcome<String>() {
            @Override
            public void onOutcome(Outcome<String> outcome) {
                r2.set(outcome);
                isComplete.countDown();
            }
        });

        TimedAwait.latchAndExpectCompletion(isComplete, "isComplete");
        final var r2Value = Objects.requireNonNull(r2.get().getOrThrow());
        assertTrue(
            r2Value.startsWith("tasks-io-"),
            "Expected thread name to start with 'tasks-io-', but was: " + r2Value
        );
    }

}
