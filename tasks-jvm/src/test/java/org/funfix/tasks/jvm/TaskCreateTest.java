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

    protected abstract  <T> Task<T> createTask(final AsyncFun<? extends T> builder);

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

        final Task<String> task = createTask((executor, cb) -> {
            cb.onSuccess("Hello, world!");
            // callback is idempotent
            cb.onSuccess("Hello, world! (2)");
            noErrors.countDown();
            return Cancellable.EMPTY;
        });

        final String result = task.executeBlockingTimed(TimedAwait.TIMEOUT);
        assertEquals("Hello, world!", result);
        TimedAwait.latchAndExpectCompletion(noErrors, "noErrors");
        assertNull(reportedException.get(), "reportedException.get()");
    }

    @Test
    void failed() throws InterruptedException {
        final var noErrors = new CountDownLatch(1);
        final var reportedException = new AtomicReference<@Nullable Throwable>(null);
        final Task<String> task = createTask((executor, cb) -> {
            Thread.setDefaultUncaughtExceptionHandler((t, ex) -> reportedException.set(ex));
            cb.onFailure(new RuntimeException("Sample exception"));
            // callback is idempotent
            cb.onFailure(new RuntimeException("Sample exception (2)"));
            noErrors.countDown();
            return Cancellable.EMPTY;
        });
        try {
            if (executor != null)
                task.executeBlockingTimed(executor, TimedAwait.TIMEOUT);
            else
                task.executeBlockingTimed(TimedAwait.TIMEOUT);
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

        final Task<String> task = createTask((executor, cb) -> () -> {
            Thread.setDefaultUncaughtExceptionHandler((t, ex) -> reportedException.set(ex));
            cb.onCancellation();
            // callback is idempotent
            cb.onCancellation();
            noErrors.countDown();
        });

        final var fiber =
                executor != null
                        ? task.executeFiber(executor)
                        : task.executeFiber();
        try {
            fiber.cancel();
            // call is idempotent
            fiber.cancel();
            fiber.joinBlocking();
            Objects.requireNonNull(fiber.outcome()).getOrThrow();
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
    protected <T> Task<T> createTask(final AsyncFun<? extends T> builder) {
        return Task.create(builder);
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

    @Override
    protected <T> Task<T> createTask(final AsyncFun<? extends T> builder) {
        return Task.create(builder);
    }
}

@NullMarked
abstract class BaseTaskCreateAsyncTest extends BaseTaskCreateTest {
    @Override
    protected <T> Task<T> createTask(final AsyncFun<? extends T> builder) {
        return Task.createAsync(builder);
    }

    @Test
    void java21Plus() throws ExecutionException, InterruptedException {
        assumeTrue(VirtualThreads.areVirtualThreadsSupported(), "Requires Java 21+");

        final Task<String> task = createTask((executor, cb) -> {
            cb.onSuccess(Thread.currentThread().getName());
            return Cancellable.EMPTY;
        });

        final var result =
                executor != null
                        ? task.executeBlocking(executor)
                        : task.executeBlocking();
        assertTrue(
                Objects.requireNonNull(result).matches("tasks-io-virtual-\\d+"),
                "result.matches(\"tasks-io-virtual-\\\\d+\")"
        );
    }

    @Test
    void olderJava() throws ExecutionException, InterruptedException {
        assumeFalse(VirtualThreads.areVirtualThreadsSupported(), "Requires older Java versions");

        final Task<String> task = createTask((executor, cb) -> {
            cb.onSuccess(Thread.currentThread().getName());
            return Cancellable.EMPTY;
        });

        final var result =
                executor != null
                        ? task.executeBlocking(executor)
                        : task.executeBlocking();
        assertTrue(
                Objects.requireNonNull(result).matches("tasks-io-platform-\\d+"),
                "result.matches(\"tasks-io-virtual-\\\\d+\")"
        );
    }
}

@NullMarked
class TaskCreateAsyncDefaultExecutorJava21Test extends BaseTaskCreateAsyncTest {
    @BeforeEach
    void setUp() {
        closeable = SysProp.withVirtualThreads(true);
        executor = null;
    }
}

@NullMarked
class TaskCreateAsyncDefaultExecutorOlderJavaTest extends BaseTaskCreateAsyncTest {
    @BeforeEach
    void setUp() {
        closeable = SysProp.withVirtualThreads(false);
        executor = null;
    }
}

@NullMarked
class TaskCreateAsyncCustomExecutorOlderJavaTest extends BaseTaskCreateAsyncTest {
    @BeforeEach
    void setUp() {
        closeable = SysProp.withVirtualThreads(false);
        executor = TaskExecutors.global();
    }
}

@NullMarked
class TaskCreateAsyncCustomExecutorJava21Test extends BaseTaskCreateAsyncTest {
    @BeforeEach
    void setUp() {
        closeable = SysProp.withVirtualThreads(true);
        executor = TaskExecutors.global();
    }
}
