package org.funfix.tasks;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@NullMarked
public class TaskFromBlockingFutureTest {
    @Nullable
    ExecutorService es;

    @SuppressWarnings("deprecation")
    @BeforeEach
    void setup() {
        es = Executors.newCachedThreadPool(r -> {
            final var th = new Thread(r);
            th.setName("es-sample-" + th.getId());
            th.setDaemon(true);
            return th;
        });
    }

    @AfterEach
    void tearDown() {
        Objects.requireNonNull(es).shutdown();
    }

    @Test
    void happyPath() throws ExecutionException, InterruptedException {
        Objects.requireNonNull(es);

        final var name = new AtomicReference<>("");
        final var task = Task.fromBlockingFuture(es, () -> {
            name.set(Thread.currentThread().getName());
            return es.submit(() -> "Hello, world!");
        });

        final var r = task.executeBlocking();
        assertEquals("Hello, world!", r);
        assertTrue(name.get().startsWith("es-sample-"));
    }

    @SuppressWarnings("KotlinInternalInJava")
    @Test
    void loomHappyPath() throws ExecutionException, InterruptedException {
        assumeTrue(VirtualThreads.areVirtualThreadsSupported(), "Requires Java 21+");
        Objects.requireNonNull(es);

        final var name = new AtomicReference<>("");
        final var task = Task.fromBlockingFuture(() -> {
            name.set(Thread.currentThread().getName());
            return es.submit(() -> "Hello, world!");
        });

        final var r = task.executeBlocking();
        assertEquals("Hello, world!", r);
        assertTrue(name.get().startsWith("common-io-virtual-"));
    }

    @Test
    void throwExceptionInBuilder() throws InterruptedException {
        Objects.requireNonNull(es);

        try {
            Task.fromBlockingFuture(() -> {
                throw new RuntimeException("Error");
            }).executeBlocking();
        } catch (final ExecutionException ex) {
            assertEquals("Error", ex.getCause().getMessage());
        }
    }

    @Test
    void throwExceptionInFuture() throws InterruptedException {
        Objects.requireNonNull(es);
        try {
            Task.fromBlockingFuture(() -> es.submit(() -> {
                    throw new RuntimeException("Error");
                }))
                .executeBlocking();
        } catch (final ExecutionException ex) {
            assertEquals("Error", ex.getCause().getMessage());
        }
    }

    @Test
    void builderCanBeCancelled() throws InterruptedException, ExecutionException, TimeoutException {
        Objects.requireNonNull(es);

        final var wasStarted = new CountDownLatch(1);
        final var latch = new CountDownLatch(1);

        final var fiber = Task
            .<Void>fromBlockingFuture(() -> {
                wasStarted.countDown();
                try {
                    Thread.sleep(10000);
                    return null;
                } catch (final InterruptedException e) {
                    latch.countDown();
                    throw e;
                }
            }).executeConcurrently();

        TimedAwait.latchAndExpectCompletion(wasStarted, "wasStarted");
        fiber.cancel();
        fiber.joinBlockingTimed(TimedAwait.TIMEOUT);

        try {
            Objects.requireNonNull(fiber.outcome()).getOrThrow();
        } catch (final CancellationException ignored) {
        }
        TimedAwait.latchAndExpectCompletion(latch, "latch");
    }

    @Test
    void futureCanBeCancelled() throws InterruptedException, ExecutionException, TimeoutException {
        Objects.requireNonNull(es);

        final var latch = new CountDownLatch(1);
        final var wasStarted = new CountDownLatch(1);

        final var fiber = Task
            .fromBlockingFuture(() -> es.submit(() -> {
                wasStarted.countDown();
                try {
                    Thread.sleep(10000);
                } catch (final InterruptedException e) {
                    latch.countDown();
                }
            })).executeConcurrently();

        wasStarted.await();
        fiber.cancel();
        fiber.joinBlockingTimed(TimedAwait.TIMEOUT);

        try {
            Objects.requireNonNull(fiber.outcome()).getOrThrow();
        } catch (final CancellationException ignored) {
        }
        TimedAwait.latchAndExpectCompletion(latch, "latch");
    }
}
