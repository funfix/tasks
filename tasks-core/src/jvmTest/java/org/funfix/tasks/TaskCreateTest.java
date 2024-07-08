package org.funfix.tasks;

import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;

import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class TaskCreateTest {
    protected <T> Task<T> createTask(final AsyncFun<? extends T> builder) {
        return Task.create(builder);
    }

    @Test
    void successful() throws ExecutionException, InterruptedException, TimeoutException {
        final var noErrors = new AtomicBoolean(false);
        final Task<String> task = createTask(cb -> {
            cb.onSuccess("Hello, world!");
            // callback is idempotent
            cb.onSuccess("Hello, world! (2)");
            noErrors.set(true);
            return Cancellable.EMPTY;
        });
        final String result = task.executeBlockingTimed(TimedAwait.TIMEOUT);
        assertEquals("Hello, world!", result);
        assertTrue(noErrors.get(), "noErrors.get");
    }

    @Test
    void failed() throws InterruptedException {
        final var noErrors = new AtomicBoolean(false);
        final Task<String> task = createTask(cb -> {
            cb.onFailure(new RuntimeException("Sample exception"));
            // callback is idempotent
            cb.onFailure(new RuntimeException("Sample exception (2)"));
            noErrors.set(true);
            return Cancellable.EMPTY;
        });
        try {
            task.executeBlockingTimed(TimedAwait.TIMEOUT);
        } catch (final ExecutionException | TimeoutException ex) {
            assertEquals("Sample exception", ex.getCause().getMessage());
        }
        assertTrue(noErrors.get(), "noErrors.get");
    }

    @Test
    void cancelled() throws InterruptedException, ExecutionException {
        final var noErrors = new AtomicInteger(0);
        final Task<String> task = createTask(cb -> () -> {
            cb.onCancel();
            // callback is idempotent
            cb.onCancel();
            noErrors.incrementAndGet();
        });
        final var fiber = task.executeConcurrently();
        try {
            fiber.cancel();
            // call is idempotent
            fiber.cancel();
            fiber.joinBlocking();
            Objects.requireNonNull(fiber.outcome()).getOrThrow();
        } catch (final CancellationException ex) {
            // Expected
        }
        assertEquals(1, noErrors.get());
    }
}

@NullMarked
class TaskCreateAsync1Test extends TaskCreateTest {
    @Override
    protected <T> Task<T> createTask(final AsyncFun<? extends T> builder) {
        return Task.createAsync(builder);
    }

    @Test
    void java21Plus() throws ExecutionException, InterruptedException {
        assumeTrue(VirtualThreads.areVirtualThreadsSupported(), "Requires Java 21+");

        final Task<String> task = createTask(cb -> {
            cb.onSuccess(Thread.currentThread().getName());
            return Cancellable.EMPTY;
        });

        final var result = task.executeBlocking();
        assertTrue(result.matches("common-io-virtual-\\d+"), "result.matches(\"common-io-virtual-\\\\d+\")");
    }

    @Test
    void olderJava() throws ExecutionException, InterruptedException {
        final var ignored = SysProp.withVirtualThreads(false);
        try {
            final Task<String> task = createTask(cb -> {
                cb.onSuccess(Thread.currentThread().getName());
                return Cancellable.EMPTY;
            });

            final var result = task.executeBlocking();
            assertTrue(result.matches("common-io-platform-\\d+"), "result.matches(\"common-io-virtual-\\\\d+\")");
        } finally {
            ignored.close();
        }
    }
}

@NullMarked
class TaskCreateAsync2Test extends TaskCreateTest {
    @Override
    protected <T> Task<T> createTask(final AsyncFun<? extends T> builder) {
        return Task.createAsync(
            ThreadPools.sharedIO(),
            builder
        );
    }

    @Test
    void java21Plus() throws ExecutionException, InterruptedException {
        assumeTrue(VirtualThreads.areVirtualThreadsSupported(), "Requires Java 21+");

        final Task<String> task = createTask(cb -> {
            cb.onSuccess(Thread.currentThread().getName());
            return Cancellable.EMPTY;
        });

        final var result = task.executeBlocking();
        assertTrue(result.matches("common-io-virtual-\\d+"), "result.matches(\"common-io-virtual-\\\\d+\")");
    }


    @Test
    void olderJava() throws ExecutionException, InterruptedException {
        final var ignored = SysProp.withVirtualThreads(false);
        try {
            final Task<String> task = createTask(cb -> {
                cb.onSuccess(Thread.currentThread().getName());
                return Cancellable.EMPTY;
            });

            final var result = task.executeBlocking();
            assertTrue(result.matches("common-io-platform-\\d+"), "result.matches(\"common-io-virtual-\\\\d+\")");
        } finally {
            ignored.close();
        }
    }
}

@NullMarked
class TaskCreateAsync3Test extends TaskCreateTest {
    @Override
    @SuppressWarnings("deprecation")
    protected <T> Task<T> createTask(final AsyncFun<? extends T> builder) {
        return Task.createAsync(
            r -> {
                final var t = new Thread(r);
                t.setName("my-thread-" + t.getId());
                return t;
            },
            builder
        );
    }

    @Test
    void threadName() throws ExecutionException, InterruptedException {
        final Task<String> task = createTask(cb -> {
            cb.onSuccess(Thread.currentThread().getName());
            return Cancellable.EMPTY;
        });

        final var result = task.executeBlocking();
        assertTrue(result.matches("my-thread-\\d+"), "result.matches(\"common-io-virtual-\\\\d+\")");
    }
}
