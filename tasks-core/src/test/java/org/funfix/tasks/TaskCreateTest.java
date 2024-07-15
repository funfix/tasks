package org.funfix.tasks;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@NullMarked
abstract class BaseTaskCreateTest {
    @Nullable protected AutoCloseable closeable;
    @Nullable protected FiberExecutor executor;
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

        final String result = task.executeBlockingTimed(TimedAwait.TIMEOUT, executor);
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

        final Task<String> task = createTask((executor, cb) -> () -> {
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
        final var javaExecutor = Executors.newCachedThreadPool();
        executor = FiberExecutor.fromExecutor(javaExecutor);
        closeable = javaExecutor::shutdown;
    }

    @Override
    protected <T> Task<T> createTask(final AsyncFun<? extends T> builder) {
        return Task.create(builder);
    }
}

@NullMarked
class TaskCreateSimpleCustomJavaThreadFactoryTest extends BaseTaskCreateTest {
    @BeforeEach
    void setUp() {
        executor = FiberExecutor.fromThreadFactory(
            r -> {
                final var t = new Thread(r);
                t.setName("my-thread-" + t.getId());
                return t;
            }
        );
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

        final var result = task.executeBlocking(executor);
        assertTrue(result.matches("common-io-virtual-\\d+"), "result.matches(\"common-io-virtual-\\\\d+\")");
    }

    @Test
    void olderJava() throws ExecutionException, InterruptedException {
        assumeFalse(VirtualThreads.areVirtualThreadsSupported(), "Requires older Java versions");

        final Task<String> task = createTask((executor, cb) -> {
            cb.onSuccess(Thread.currentThread().getName());
            return Cancellable.EMPTY;
        });

        final var result = task.executeBlocking(executor);
        assertTrue(result.matches("common-io-platform-\\d+"), "result.matches(\"common-io-virtual-\\\\d+\")");
    }
}

class TaskCreateAsyncDefaultExecutorJava21Test extends BaseTaskCreateAsyncTest {
    @BeforeEach
    void setUp() {
        closeable = SysProp.withVirtualThreads(true);
        executor = null;
    }
}

class TaskCreateAsyncDefaultExecutorOlderJavaTest extends BaseTaskCreateAsyncTest {
    @BeforeEach
    void setUp() {
        closeable = SysProp.withVirtualThreads(false);
        executor = null;
    }
}

class TaskCreateAsyncCustomExecutorOlderJavaTest extends BaseTaskCreateAsyncTest {
    @BeforeEach
    void setUp() {
        closeable = SysProp.withVirtualThreads(false);
        executor = FiberExecutor.fromExecutor(ThreadPools.sharedIO());
    }
}

class TaskCreateAsyncCustomExecutorJava21Test extends BaseTaskCreateAsyncTest {
    @BeforeEach
    void setUp() {
        closeable = SysProp.withVirtualThreads(true);
        executor = FiberExecutor.fromExecutor(ThreadPools.sharedIO());
    }
}
