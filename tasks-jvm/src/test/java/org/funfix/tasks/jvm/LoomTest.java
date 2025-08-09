package org.funfix.tasks.jvm;

import org.junit.jupiter.api.Test;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.funfix.tasks.jvm.VirtualThreads.areVirtualThreadsSupported;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class LoomTest {
    @Test
    public void commonPoolInJava21() throws InterruptedException {
        assumeTrue(areVirtualThreadsSupported(), "Requires Java 21+");

        final var commonPool = TaskExecutors.unlimitedThreadPoolForIO("tasks-io");
        try {
            final var latch = new CountDownLatch(1);
            final var isVirtual = new AtomicBoolean(false);
            final var name = new AtomicReference<String>();

            commonPool.execute(() -> {
                isVirtual.set(VirtualThreads.isVirtualThread(Thread.currentThread()));
                name.set(Thread.currentThread().getName());
                latch.countDown();
            });

            TimedAwait.latchAndExpectCompletion(latch);
            assertTrue(isVirtual.get(), "isVirtual");
            assertTrue(
                Objects.requireNonNull(name.get()).matches("tasks-io-virtual-\\d+"),
                "name.matches(\"tasks-io-virtual-\\\\d+\")"
            );
        } finally {
            commonPool.shutdown();
        }
    }

    @Test
    public void canInitializeFactoryInJava21() throws InterruptedException, VirtualThreads.NotSupportedException {
        assumeTrue(areVirtualThreadsSupported(), "Requires Java 21+");

        final var f = VirtualThreads.factory("my-vt-");
        assertNotNull(f);

        final var latch = new CountDownLatch(1);
        final var isVirtual = new AtomicBoolean(false);
        final var name = new AtomicReference<String>();

        f.newThread(() -> {
            isVirtual.set(VirtualThreads.isVirtualThread(Thread.currentThread()));
            name.set(Thread.currentThread().getName());
            latch.countDown();
        }).start();

        TimedAwait.latchAndExpectCompletion(latch);
        assertTrue(isVirtual.get(), "isVirtual");
        assertTrue(
                Objects.requireNonNull(name.get()).matches("my-vt-\\d+"),
                "name.matches(\"my-vt-\\\\d+\")"
        );
    }

    @Test
    public void canInitializeExecutorInJava21() throws InterruptedException, VirtualThreads.NotSupportedException {
        assumeTrue(areVirtualThreadsSupported(), "Requires Java 21+");

        final var executor = VirtualThreads.executorService("my-vt-");
        assertNotNull(executor, "executor");
        try {
            final var latch = new CountDownLatch(1);
            final var isVirtual = new AtomicBoolean(false);
            final var name = new AtomicReference<String>();
            executor.execute(() -> {
                isVirtual.set(VirtualThreads.isVirtualThread(Thread.currentThread()));
                name.set(Thread.currentThread().getName());
                latch.countDown();
            });

            TimedAwait.latchAndExpectCompletion(latch);
            assertTrue(isVirtual.get(), "isVirtual");
            assertTrue(
                    Objects.requireNonNull(name.get()).matches("my-vt-\\d+"),
                    "name.matches(\"my-vt-\\\\d+\")"
            );
        } finally {
            executor.shutdown();
        }
    }

    @Test
    public void cannotInitializeLoomUtilsInOlderJava() {
        final var r = SysProp.withVirtualThreads(false);
        try {
            try {
                VirtualThreads.factory("tasks-io");
                fail("Should throw");
            } catch (final VirtualThreads.NotSupportedException ignored) {
            }
            try {
                VirtualThreads.executorService("tasks-io").shutdown();
                fail("Should throw");
            } catch (final VirtualThreads.NotSupportedException ignored) {
            }
        } finally {
            r.close();
        }
    }

    @Test
    public void commonPoolInOlderJava() throws InterruptedException {
        final var r = SysProp.withVirtualThreads(false);
        final var commonPool = TaskExecutors.unlimitedThreadPoolForIO("tasks-io");
        try {
            assertFalse(areVirtualThreadsSupported(), "areVirtualThreadsSupported");
            assertNotNull(commonPool, "commonPool");

            final var latch = new CountDownLatch(1);
            final var isVirtual = new AtomicBoolean(true);
            final var name = new AtomicReference<String>();

            commonPool.execute(() -> {
                isVirtual.set(VirtualThreads.isVirtualThread(Thread.currentThread()));
                name.set(Thread.currentThread().getName());
                latch.countDown();
            });

            TimedAwait.latchAndExpectCompletion(latch);
            assertFalse(isVirtual.get(), "isVirtual");
            assertTrue(
                    Objects.requireNonNull(name.get()).matches("^tasks-io-platform-\\d+$"),
                    "name.matches(\"^tasks-io-platform-\\\\d+$\")"
            );
        } finally {
            r.close();
            commonPool.shutdown();
        }
    }
}
