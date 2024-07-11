package org.funfix.tasks;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@NullMarked
abstract class BaseTaskCreateTest {
    @Nullable protected FiberExecutor executor;
    protected abstract  <T> Task<T> createTask(final AsyncFun<? extends T> builder);

    @Test
    void successful() throws ExecutionException, InterruptedException, TimeoutException {
        final var noErrors = new CountDownLatch(1);
        final var reportedException = new AtomicReference<@Nullable Throwable>(null);

        final Task<String> task = createTask((cb, executor) -> {
            cb.onSuccess("Hello, world!");
            // callback is idempotent
            cb.onSuccess("Hello, world! (2)");
            noErrors.countDown();
            return Cancellable.EMPTY;
        });

        final String result = task.executeBlockingTimed(TimedAwait.TIMEOUT, executor);
        assertEquals("Hello, world!", result);
        TimedAwait.latchAndExpectCompletion(noErrors, "noErrors");
        assertNull(reportedException.get(), "reportedException.get()");
    }

    @Test
    void failed() throws InterruptedException {
        final var noErrors = new CountDownLatch(1);
        final var reportedException = new AtomicReference<@Nullable Throwable>(null);
        final Task<String> task = createTask((cb, executor) -> {
            Thread.setDefaultUncaughtExceptionHandler((t, ex) -> reportedException.set(ex));
            cb.onFailure(new RuntimeException("Sample exception"));
            // callback is idempotent
            cb.onFailure(new RuntimeException("Sample exception (2)"));
            noErrors.countDown();
            return Cancellable.EMPTY;
        });
        try {
            task.executeBlockingTimed(TimedAwait.TIMEOUT, executor);
        } catch (final ExecutionException | TimeoutException ex) {
            assertEquals("Sample exception", ex.getCause().getMessage());
        }
        TimedAwait.latchAndExpectCompletion(noErrors, "noErrors");
        assertNotNull(reportedException.get(), "reportedException.get()");
        assertEquals(
                "Sample exception (2)",
                Objects.requireNonNull(reportedException.get()).getMessage()
        );
    }

    @Test
    void cancelled() throws InterruptedException, ExecutionException {
        final var noErrors = new CountDownLatch(1);
        final var reportedException = new AtomicReference<@Nullable Throwable>(null);

        final Task<String> task = createTask((cb, executor) -> () -> {
            Thread.setDefaultUncaughtExceptionHandler((t, ex) -> reportedException.set(ex));
            cb.onCancel();
            // callback is idempotent
            cb.onCancel();
            noErrors.countDown();
        });

        final var fiber = task.executeConcurrently(executor);
        try {
            fiber.cancel();
            // call is idempotent
            fiber.cancel();
            fiber.joinBlocking();
            Objects.requireNonNull(fiber.outcome()).getOrThrow();
        } catch (final CancellationException ex) {
            // Expected
        }
        TimedAwait.latchAndExpectCompletion(noErrors, "noErrors");
        assertNull(reportedException.get(), "reportedException.get()");
    }
}

@NullMarked
class TaskCreateSimpleTest extends BaseTaskCreateTest {
    @Override
    protected <T> Task<T> createTask(AsyncFun<? extends T> builder) {
        return Task.create(builder);
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
