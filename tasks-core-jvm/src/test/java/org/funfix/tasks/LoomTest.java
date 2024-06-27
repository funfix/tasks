package org.funfix.tasks;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.funfix.tasks.VirtualThreads.areVirtualThreadsSupported;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class LoomTest {
    @Test
    public void commonPoolInJava21() throws InterruptedException {
        assumeTrue(areVirtualThreadsSupported(), "Requires Java 21+");

        try (final var commonPool = ThreadPools.unlimitedThreadPoolForIO("common-io")) {
            final var latch = new CountDownLatch(1);
            final var isVirtual = new AtomicBoolean(false);
            final var name = new AtomicReference<String>();

            commonPool.execute(() -> {
                isVirtual.set(VirtualThreads.isVirtualThread(Thread.currentThread()));
                name.set(Thread.currentThread().getName());
                latch.countDown();
            });

            assertTrue(latch.await(5, java.util.concurrent.TimeUnit.SECONDS), "latch");
            assertTrue(isVirtual.get(), "isVirtual");
            assertTrue(
                name.get().matches("common-io-virtual-\\d+"),
                "name.matches(\"common-io-virtual-\\\\d+\")"
            );
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

        assertTrue(latch.await(5, java.util.concurrent.TimeUnit.SECONDS), "latch");
        assertTrue(isVirtual.get(), "isVirtual");
        assertTrue(
            name.get().matches("my-vt-\\d+"),
            "name.matches(\"my-vt-\\\\d+\")"
        );
    }

    @Test
    public void canInitializeExecutorInJava21() throws InterruptedException, VirtualThreads.NotSupportedException {
        assumeTrue(areVirtualThreadsSupported(), "Requires Java 21+");

        try (final var executor = VirtualThreads.executorService("my-vt-")) {
            assertNotNull(executor, "executor");

            final var latch = new CountDownLatch(1);
            final var isVirtual = new AtomicBoolean(false);
            final var name = new AtomicReference<String>();
            executor.execute(() -> {
                isVirtual.set(VirtualThreads.isVirtualThread(Thread.currentThread()));
                name.set(Thread.currentThread().getName());
                latch.countDown();
            });

            assertTrue(latch.await(5, java.util.concurrent.TimeUnit.SECONDS), "latch");
            assertTrue(isVirtual.get(), "isVirtual");
            assertTrue(
                name.get().matches("my-vt-\\d+"),
                "name.matches(\"my-vt-\\\\d+\")"
            );
        }
    }

    @Test
    public void cannotInitializeLoomUtilsInOlderJava() {
        try (final var r = SysProp.withVirtualThreads(false)) {
            try {
                final var factory = VirtualThreads.factory("common-io");
                VirtualThreads.executorService("common-io").close();
            } catch (final VirtualThreads.NotSupportedException ignored) {
            }
        }
    }

    @Test
    public void commonPoolInOlderJava() throws InterruptedException {
        try (
            final var r = SysProp.withVirtualThreads(false);
            final var commonPool = ThreadPools.unlimitedThreadPoolForIO("common-io")
        ) {
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

            assertTrue(latch.await(5, java.util.concurrent.TimeUnit.SECONDS), "latch");
            assertFalse(isVirtual.get(), "isVirtual");
            assertTrue(
                name.get().matches("^common-io-platform-\\d+$"),
                "name.matches(\"^common-io-platform-\\\\d+$\")"
            );
        }
    }
}
