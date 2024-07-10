package org.funfix.tasks;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Objects;
import java.util.concurrent.*;

/**
 * Provides utilities for working with [Executor] instances, optimized
 * for common use-cases.
 */
@NullMarked
final class ThreadPools {
    private static volatile @Nullable Executor sharedVirtualIORef = null;
    private static volatile @Nullable Executor sharedPlatformIORef = null;
    private static volatile @Nullable ScheduledExecutorService sharedScheduledExecutorRef = null;

    /**
     * Returns a shared {@link Executor} meant for blocking I/O tasks.
     * The reference gets lazily initialized on the first call.
     * <p>
     * Uses {@link #unlimitedThreadPoolForIO(String)} to create the executor,
     * which will use virtual threads on Java 21+, or a plain
     * {@code Executors.newCachedThreadPool()} on older JVM versions.
     */
    public static Executor sharedIO() {
        if (VirtualThreads.areVirtualThreadsSupported()) {
            return sharedVirtualIO();
        } else {
            return sharedPlatformIO();
        }
    }

    private static Executor sharedPlatformIO() {
        // Using double-checked locking to avoid synchronization
        if (sharedPlatformIORef == null) {
            synchronized (ThreadPools.class) {
                if (sharedPlatformIORef == null) {
                    sharedPlatformIORef = unlimitedThreadPoolForIO("common-io");
                }
            }
        }
        return Objects.requireNonNull(sharedPlatformIORef);
    }

    private static Executor sharedVirtualIO() {
        // Using double-checked locking to avoid synchronization
        if (sharedVirtualIORef == null) {
            synchronized (ThreadPools.class) {
                if (sharedVirtualIORef == null) {
                    sharedVirtualIORef = unlimitedThreadPoolForIO("common-io");
                }
            }
        }
        return Objects.requireNonNull(sharedVirtualIORef);
    }

    /**
     * Creates an {@code Executor} meant for blocking I/O tasks, with an
     * unlimited number of threads.
     * <p>
     * On Java 21 and above, the created {@code Executor} will run tasks on virtual threads.
     * On older JVM versions, it returns a plain {@code Executors.newCachedThreadPool}.
     */
    public static ExecutorService unlimitedThreadPoolForIO(final String prefix) {
        if (VirtualThreads.areVirtualThreadsSupported())
            try {
                return VirtualThreads.executorService(prefix + "-virtual-");
            } catch (final VirtualThreads.NotSupportedException ignored) {}

        return java.util.concurrent.Executors.newCachedThreadPool(r -> {
            final var t = new Thread(r);
            t.setName(prefix + "-platform-" + t.getId());
            return t;
        });
    }

    public static ScheduledExecutorService sharedScheduledExecutor() {
        var ref = sharedScheduledExecutorRef;
        if (ref == null)
            synchronized (ThreadPools.class) {
                ref = sharedScheduledExecutorRef;
                if (ref == null) {
                    final var poolSize = Math.max(
                            1,
                            java.lang.Runtime.getRuntime().availableProcessors());
                    sharedScheduledExecutorRef = ref =
                            Executors.newScheduledThreadPool(
                                    poolSize,
                                    r -> {
                                        final var t = new Thread(r);
                                        t.setName("io-shared-scheduler-" + t.getId());
                                        t.setDaemon(true);
                                        return t;
                                    }
                            );
                }
            }
        return Objects.requireNonNull(ref);
    }
}

/**
 * Internal utilities — not exposed yet, because lacking Loom support is only
 * temporary.
 */
@NullMarked
final class VirtualThreads {
    private static final @Nullable MethodHandle newThreadPerTaskExecutorMethodHandle;

    public static final class NotSupportedException extends Exception {
        public NotSupportedException(final String feature) {
            super(feature + " is not supported on this JVM");
        }
    }

    static {
        MethodHandle tempHandle;
        try {
            final var executorsClass = Class.forName("java.util.concurrent.Executors");
            final var lookup = MethodHandles.lookup();
            tempHandle = lookup.findStatic(
                    executorsClass,
                    "newThreadPerTaskExecutor",
                    MethodType.methodType(ExecutorService.class, ThreadFactory.class));
        } catch (final Throwable e) {
            tempHandle = null;
        }
        newThreadPerTaskExecutorMethodHandle = tempHandle;
    }

    /**
     * Create a virtual thread executor, returns {@code null} if failed.
     * <p>
     * This function can only return a non-{@code null} value if running on Java 21 or later,
     * as it uses reflection to access the {@code Executors.newVirtualThreadPerTaskExecutor}.
     *
     * @throws NotSupportedException if the current JVM does not support virtual threads.
     */
    public static ExecutorService executorService(final String prefix)
            throws NotSupportedException {

        if (!areVirtualThreadsSupported()) {
            throw new NotSupportedException("Executors.newThreadPerTaskExecutor");
        }
        Throwable thrown = null;
        try {
            final var factory = factory(prefix);
            if (newThreadPerTaskExecutorMethodHandle != null) {
                return (ExecutorService) newThreadPerTaskExecutorMethodHandle.invoke(factory);
            }
        } catch (final NotSupportedException e) {
            throw e;
        } catch (final Throwable e) {
            thrown = e;
        }
        final var e2 = new NotSupportedException("Executors.newThreadPerTaskExecutor");
        if (thrown != null) e2.addSuppressed(thrown);
        throw e2;
    }

    public static ThreadFactory factory() throws NotSupportedException {
        return factory(VIRTUAL_THREAD_NAME_PREFIX);
    }

    public static ThreadFactory factory(final String prefix) throws NotSupportedException {
        if (!areVirtualThreadsSupported()) {
            throw new NotSupportedException("Thread.ofVirtual");
        }
        try {
            final var builderClass = Class.forName("java.lang.Thread$Builder");
            final var ofVirtualClass = Class.forName("java.lang.Thread$Builder$OfVirtual");
            final var lookup = MethodHandles.lookup();
            final var ofVirtualMethod = lookup.findStatic(Thread.class, "ofVirtual", MethodType.methodType(ofVirtualClass));
            var builder = ofVirtualMethod.invoke();
            final var nameMethod = lookup.findVirtual(ofVirtualClass, "name", MethodType.methodType(ofVirtualClass, String.class, long.class));
            final var factoryMethod = lookup.findVirtual(builderClass, "factory", MethodType.methodType(ThreadFactory.class));
            builder = nameMethod.invoke(builder, prefix, 0L);
            return (ThreadFactory) factoryMethod.invoke(builder);
        } catch (final Throwable e) {
            final var e2 = new NotSupportedException("Thread.ofVirtual");
            e2.addSuppressed(e);
            throw e2;
        }
    }

    private static final @Nullable MethodHandle isVirtualMethodHandle;

    static {
        MethodHandle tempHandle;
        try {
            final var threadClass = Class.forName("java.lang.Thread");
            final var lookup = MethodHandles.lookup();
            tempHandle = lookup.findVirtual(
                    threadClass,
                    "isVirtual",
                    MethodType.methodType(boolean.class));
        } catch (final Throwable e) {
            tempHandle = null;
        }
        isVirtualMethodHandle = tempHandle;
    }

    public static boolean isVirtualThread(final Thread th) {
        try {
            if (isVirtualMethodHandle != null) {
                return (boolean) isVirtualMethodHandle.invoke(th);
            }
        } catch (final Throwable e) {
            // Ignored
        }
        return false;
    }

    public static boolean areVirtualThreadsSupported() {
        final var sp = System.getProperty("funfix.tasks.virtual-threads");
        final var disableFeature = "off".equalsIgnoreCase(sp)
                || "false".equalsIgnoreCase(sp)
                || "no".equalsIgnoreCase(sp)
                || "0".equals(sp)
                || "disabled".equalsIgnoreCase(sp);
        return !disableFeature && isVirtualMethodHandle != null && newThreadPerTaskExecutorMethodHandle != null;
    }

    public static final String VIRTUAL_THREAD_NAME_PREFIX = "common-io-virtual-";
}
