package org.funfix.tasks;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@NullMarked
abstract class TaskFromBlockingIOTestBase {
    abstract void testThreadName(String name);
    abstract <T> Task<T> fromBlockingIO(DelayedFun<T> delayed);

    @Test
    public void happyPath() throws ExecutionException, InterruptedException {
        final var name = new AtomicReference<>("");
        final var r =
            fromBlockingIO(() -> {
                name.set(Thread.currentThread().getName());
                return "Hello, world!";
            }).executeBlocking();
        assertEquals("Hello, world!", r);
        testThreadName(name.get());
    }

    @Test
    public void canFail() throws InterruptedException {
        try {
            fromBlockingIO(() -> { throw new RuntimeException("Error"); }).executeBlocking();
            fail("Should have thrown an exception");
        } catch (final ExecutionException ex) {
            assertEquals("Error", ex.getCause().getMessage());
        }
    }

    @Test
    public void isCancellable() throws InterruptedException, ExecutionException {
        final var latch = new CountDownLatch(1);
        final Task<@Nullable Void> task = fromBlockingIO(() -> {
            latch.countDown();
            Thread.sleep(30000);
            return null;
        });
        final var fiber = task.executeConcurrently();
        assertTrue(
            latch.await(5, TimeUnit.SECONDS),
            "latch"
        );

        fiber.cancel();
        fiber.joinBlocking();
        try {
            Objects.requireNonNull(fiber.outcome()).getOrThrow();
            fail("Should have thrown a CancellationException");
        } catch (final CancellationException ignored) {}
    }
}

@NullMarked
final class TaskFromBlockingWithExecutorIOTest extends TaskFromBlockingIOTestBase {
    @Nullable ExecutorService es;

    @Override
    void testThreadName(final String name) {
        assertTrue(
            name.matches("^es-sample-\\d+$"),
            "name.matches(es-sample-)"
        );
    }

    @SuppressWarnings("deprecation")
    @BeforeEach
    void setup() {
        es = Executors.newCachedThreadPool(r -> {
            final var th = new Thread(r);
            th.setName("es-sample-" + th.getId());
            return th;
        });
    }

    @AfterEach
    void tearDown() {
        Objects.requireNonNull(es).shutdown();
    }

    @Override
    <T> Task<T> fromBlockingIO(final DelayedFun<T> builder) {
        Objects.requireNonNull(es);
        return Task.fromBlockingIO(es, builder);
    }
}

@NullMarked
final class TaskFromBlockingSimpleVirtualTest extends TaskFromBlockingIOTestBase {
    private @Nullable SysProp prop;

    @BeforeEach
    void setup() {
        prop = SysProp.withVirtualThreads(true);
    }

    @AfterEach
    void tearDown() {
        Objects.requireNonNull(prop).close();
    }

    @Override
    void testThreadName(final String name) {
        if (VirtualThreads.areVirtualThreadsSupported())
            assertTrue(
                name.matches("^common-io-virtual-\\d+$"),
                "name.matches(common-io-virtual-)"
            );
        else
            assertTrue(
                name.matches("^common-io-platform-\\d+$"),
                "name.matches(common-io-virtual-)"
            );
    }

    @Override
    <T> Task<T> fromBlockingIO(final DelayedFun<T> builder) {
        return Task.fromBlockingIO(builder);
    }
}

@NullMarked
final class TaskFromBlockingSimplePlatformTest extends TaskFromBlockingIOTestBase {
    private @Nullable SysProp prop;

    @BeforeEach
    void setup() {
        prop = SysProp.withVirtualThreads(false);
    }

    @AfterEach
    void tearDown() {
        Objects.requireNonNull(prop).close();
    }

    @Override
    void testThreadName(final String name) {
        assertTrue(
            name.matches("^common-io-platform-\\d+$"),
            "name.matches(common-io-platform-)"
        );
    }

    @Override
    <T> Task<T> fromBlockingIO(final DelayedFun<T> builder) {
        return Task.fromBlockingIO(builder);
    }
}

@NullMarked
final class TaskFromBlockingWithThreadFactoryTest extends TaskFromBlockingIOTestBase {
    @Override
    void testThreadName(final String name) {
        assertTrue(
            name.matches("^sample-\\d+$"),
            "name.matches(common-io-virtual-)"
        );
    }

    @SuppressWarnings("deprecation")
    @Override
    <T> Task<T> fromBlockingIO(final DelayedFun<T> builder) {
        return Task.fromBlockingIO(
            r -> {
                final var th = new Thread(r);
                th.setName("sample-" + th.getId());
                th.setDaemon(true);
                return th;
            },
            builder
        );
    }
}
