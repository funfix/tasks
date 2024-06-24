package org.funfix.tests;

import org.funfix.tasks.IOPool;
import org.funfix.tasks.VirtualThreads;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class LoomTest {
    int javaVersion() {
        String version = System.getProperty("java.version");
        if (version.startsWith("1.")) {
            return version.charAt(2) - '0';
        } else {
            return Integer.parseInt(version.split("\\.")[0]);
        }
    }

    @Test
    public void commonPoolInJava21() throws InterruptedException {
        assumeTrue(javaVersion() >= 21, "Requires Java 21+");

        Executor commonPool = IOPool.common();
        assertNotNull(commonPool);

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicBoolean isVirtual = new AtomicBoolean(false);
        final AtomicReference<String> name = new AtomicReference<>();

        commonPool.execute(() -> {
            isVirtual.set(VirtualThreads.isVirtualThread(Thread.currentThread()));
            name.set(Thread.currentThread().getName());
            latch.countDown();
        });

        assertTrue(latch.await(5, java.util.concurrent.TimeUnit.SECONDS), "latch");
        assertTrue(isVirtual.get(), "isVirtual");
        assertTrue(
            name.get().matches("io-common-virtual-thread-\\d+"),
            "name.matches(\"io-common-virtual-thread-\\\\d+\")"
        );
    }

    @Test
    public void canInitializeFactoryInJava21() throws InterruptedException {
        assumeTrue(javaVersion() >= 21, "Requires Java 21+");

        ThreadFactory f = VirtualThreads.factory("my-vt");
        assertNotNull(f);

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicBoolean isVirtual = new AtomicBoolean(false);
        final AtomicReference<String> name = new AtomicReference<>();

        f.newThread(() -> {
            isVirtual.set(VirtualThreads.isVirtualThread(Thread.currentThread()));
            name.set(Thread.currentThread().getName());
            latch.countDown();
        }).start();

        assertTrue(latch.await(5, java.util.concurrent.TimeUnit.SECONDS), "latch");
        assertTrue(isVirtual.get(), "isVirtual");
        assertTrue(
            name.get().matches("my-vt-virtual-thread-\\d+"),
            "name.matches(\"my-vt-virtual-thread-\\\\d+\")"
        );
    }

    @Test
    public void canInitializeExecutorInJava21() throws InterruptedException {
        assumeTrue(javaVersion() >= 21, "Requires Java 21+");

        ExecutorService executor = VirtualThreads.executorService("my-vt");
        assertNotNull(executor, "executor");

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicBoolean isVirtual = new AtomicBoolean(false);
        final AtomicReference<String> name = new AtomicReference<>();
        executor.execute(() -> {
            isVirtual.set(VirtualThreads.isVirtualThread(Thread.currentThread()));
            name.set(Thread.currentThread().getName());
            latch.countDown();
        });

        assertTrue(latch.await(5, java.util.concurrent.TimeUnit.SECONDS), "latch");
        assertTrue(isVirtual.get(), "isVirtual");
        assertTrue(
            name.get().matches("my-vt-virtual-thread-\\d+"),
            "name.matches(\"my-vt-virtual-thread-\\\\d+\")"
        );
    }

    @Test
    public void cannotInitializeLoomUtilsInOlderJava() {
        assumeTrue(javaVersion() < 21, "Requires Java older than 21");

        ThreadFactory factory = VirtualThreads.factory("io-common");
        assertNull(factory, "factory");

        ExecutorService executor = VirtualThreads.executorService("io-common");
        try {
            assertNull(executor, "executor");
        } finally {
            if (executor != null) executor.shutdown();
        }
    }

    @Test
    public void commonPoolInOlderJava() throws InterruptedException {
        assumeTrue(javaVersion() < 21, "Requires Java older than 21");

        Executor commonPool = IOPool.common();
        assertNotNull(commonPool, "commonPool");

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicBoolean isVirtual = new AtomicBoolean(true);
        final AtomicReference<String> name = new AtomicReference<>();

        commonPool.execute(() -> {
            isVirtual.set(VirtualThreads.isVirtualThread(Thread.currentThread()));
            name.set(Thread.currentThread().getName());
            latch.countDown();
        });

        assertTrue(latch.await(5, java.util.concurrent.TimeUnit.SECONDS), "latch");
        assertFalse(isVirtual.get(), "isVirtual");
        assertTrue(
            name.get().matches("^io-common-\\d+$"),
            "name.matches(\"^io-common-\\\\d+$\")"
        );
    }
}
