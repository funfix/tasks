package org.funfix.tasks.jvm;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@NullMarked
abstract class BaseTaskCreateTest {
    @Nullable
    protected AutoCloseable closeable;
    @Nullable protected Executor executor;

    protected abstract  <T> Task<T> fromAsyncTask(final AsyncFun<? extends T> builder);

    @AfterEach
    void tearDown() throws Exception {
        if (closeable != null) {
            closeable.close();
            closeable = null;
        }
    }

    @Test
    void successful() throws ExecutionException, InterruptedException, TimeoutException {
        final var noErrors = new CountDownLatch(1);
        final var reportedException = new AtomicReference<@Nullable Throwable>(null);

        final Task<String> task = fromAsyncTask((executor, cb) -> {
            cb.onSuccess("Hello, world!");
            // callback is idempotent
            cb.onSuccess("Hello, world! (2)");
            noErrors.countDown();
            return Cancellable.getEmpty();
        });

        final String result = task.runBlockingTimed(TimedAwait.TIMEOUT);
        assertEquals("Hello, world!", result);
        TimedAwait.latchAndExpectCompletion(noErrors, "noErrors");
        assertNull(reportedException.get(), "reportedException.get()");
    }

    @Test
    void failed() throws InterruptedException {
        final var noErrors = new CountDownLatch(1);
        final var reportedException = new AtomicReference<@Nullable Throwable>(null);
        final Task<String> task = fromAsyncTask((executor, cb) -> {
            Thread.setDefaultUncaughtExceptionHandler((t, ex) -> reportedException.set(ex));
            cb.onFailure(new RuntimeException("Sample exception"));
            // callback is idempotent
            cb.onFailure(new RuntimeException("Sample exception (2)"));
            noErrors.countDown();
            return Cancellable.getEmpty();
        });
        try {
            if (executor != null)
                task.runBlockingTimed(executor, TimedAwait.TIMEOUT);
            else
                task.runBlockingTimed(TimedAwait.TIMEOUT);
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
    void cancelled() throws InterruptedException, ExecutionException, Fiber.NotCompletedException {
        final var noErrors = new CountDownLatch(1);
        final var reportedException = new AtomicReference<@Nullable Throwable>(null);

        final Task<String> task = fromAsyncTask((executor, cb) -> () -> {
            Thread.setDefaultUncaughtExceptionHandler((t, ex) -> reportedException.set(ex));
            cb.onCancellation();
            // callback is idempotent
            cb.onCancellation();
            noErrors.countDown();
        });

        final var fiber =
                executor != null
                        ? task.runFiber(executor)
                        : task.runFiber();
        try {
            fiber.cancel();
            // call is idempotent
            fiber.cancel();
            fiber.joinBlocking();
            fiber.getResultOrThrow();
            fail("Should have thrown a TaskCancellationException");
        } catch (final TaskCancellationException ex) {
            // Expected
        }
        TimedAwait.latchAndExpectCompletion(noErrors, "noErrors");
        assertNull(reportedException.get(), "reportedException.get()");
    }
}

@NullMarked
class TaskCreateSimpleDefaultExecutorTest extends BaseTaskCreateTest {
    @BeforeEach
    void setUp() {
        executor = null;
    }

    @Override
    protected <T> Task<T> fromAsyncTask(final AsyncFun<? extends T> builder) {
        return Task.fromAsync(builder);
    }
}

@NullMarked
class TaskCreateSimpleCustomJavaExecutorTest extends BaseTaskCreateTest {
    @BeforeEach
    void setUp() {
        final var javaExecutor = java.util.concurrent.Executors.newCachedThreadPool();
        executor = javaExecutor;
        closeable = javaExecutor::shutdown;
    }

    protected <T> Task<T> fromAsyncTask(final AsyncFun<? extends T> builder) {
        return Task.fromAsync(builder);
    }
}

@NullMarked
abstract class BaseTaskCreateForkedAsyncTest extends BaseTaskCreateTest {
    @Override
    protected <T> Task<T> fromAsyncTask(final AsyncFun<? extends T> builder) {
        return Task.fromForkedAsync(builder);
    }

    @Test
    void java21Plus() throws ExecutionException, InterruptedException {
        assumeTrue(VirtualThreads.areVirtualThreadsSupported(), "Requires Java 21+");

        final Task<String> task = fromAsyncTask((executor, cb) -> {
            cb.onSuccess(Thread.currentThread().getName());
            return Cancellable.getEmpty();
        });

        final var result =
                executor != null
                        ? task.runBlocking(executor)
                        : task.runBlocking();
        assertTrue(
                Objects.requireNonNull(result).matches("tasks-io-virtual-\\d+"),
                "result.matches(\"tasks-io-virtual-\\\\d+\")"
        );
    }

    @Test
    void olderJava() throws ExecutionException, InterruptedException {
        assumeFalse(VirtualThreads.areVirtualThreadsSupported(), "Requires older Java versions");

        final Task<String> task = fromAsyncTask((executor, cb) -> {
            cb.onSuccess(Thread.currentThread().getName());
            return Cancellable.getEmpty();
        });

        final var result =
                executor != null
                        ? task.runBlocking(executor)
                        : task.runBlocking();
        assertTrue(
                Objects.requireNonNull(result).matches("tasks-io-platform-\\d+"),
                "result.matches(\"tasks-io-virtual-\\\\d+\")"
        );
    }
}

@NullMarked
class TaskCreateForkedAsyncDefaultExecutorJava21Test extends BaseTaskCreateForkedAsyncTest {
    @BeforeEach
    void setUp() {
        closeable = SysProp.withVirtualThreads(true);
        executor = null;
    }
}

@NullMarked
class TaskCreateForkedAsyncDefaultExecutorOlderJavaTest extends BaseTaskCreateForkedAsyncTest {
    @BeforeEach
    void setUp() {
        closeable = SysProp.withVirtualThreads(false);
        executor = null;
    }
}

@NullMarked
class TaskCreateForkedAsyncCustomExecutorOlderJavaTest extends BaseTaskCreateForkedAsyncTest {
    @BeforeEach
    void setUp() {
        closeable = SysProp.withVirtualThreads(false);
        executor = TaskExecutors.global();
    }
}

@NullMarked
class TaskCreateForkedAsyncCustomExecutorJava21Test extends BaseTaskCreateForkedAsyncTest {
    @BeforeEach
    void setUp() {
        closeable = SysProp.withVirtualThreads(true);
        executor = TaskExecutors.global();
    }
}
