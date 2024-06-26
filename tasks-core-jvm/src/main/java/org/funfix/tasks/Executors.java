package org.funfix.tasks;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

/**
 * Provides utilities for working with [Executor] instances, optimized
 * for common use-cases.
 */
@NullMarked
public class Executors {
    private static volatile @Nullable Executor defaultPool = null;

    /**
     * Returns a shared [Executor] meant for blocking I/O tasks.
     * <p>
     * Uses {@link #createUnlimitedForIO(String)} to create the executor, which will use
     * virtual threads on Java 21+, or a plain `newCachedThreadPool` on older
     * JVM versions.
     */
    public static Executor commonIO() {
        // Using double-checked locking to avoid synchronization
        if (defaultPool == null) {
            synchronized (Executors.class) {
                if (defaultPool == null) {
                    defaultPool = createUnlimitedForIO("common-io");
                }
            }
        }
        return Objects.requireNonNull(defaultPool);
    }

    /**
     * Creates an {@code Executor} meant for blocking I/O tasks, with an
     * unlimited number of threads.
     * <p>
     * On Java 21 and above, the created {@code Executor} will run tasks on virtual threads.
     * On older JVM versions, it returns a plain {@code Executors.newCachedThreadPool}.
     */
    public static ExecutorService createUnlimitedForIO(final String prefix) {
        try {
            return VirtualThreads.executorService(prefix + "-virtual-");
        } catch (final VirtualThreads.NotSupportedException ignored) {}

        return java.util.concurrent.Executors.newCachedThreadPool(r -> {
            final var t = new Thread(r);
            t.setName(prefix + "-platform-" + t.getId());
            return t;
        });
    }
}
