package org.funfix.tasks;

import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

public class SysProp implements AutoCloseable {
    private static final ConcurrentMap<String, ReentrantLock> locks =
        new java.util.concurrent.ConcurrentHashMap<>();

    private final String key;
    private final @Nullable String oldValue;
    private final ReentrantLock lock;

    public SysProp(final String key, final @Nullable String value) {
        this.lock = locks.computeIfAbsent(key, k -> new ReentrantLock());
        lock.lock();

        this.key = key;
        this.oldValue = System.getProperty(key);
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }

    @Override
    public void close() {
        if (oldValue == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, oldValue);
        }
        lock.unlock();
    }

    public static SysProp with(final String key, final @Nullable String value) {
        return new SysProp(key, value);
    }

    public static SysProp withVirtualThreads(final boolean enabled) {
        return with("funfix.tasks.virtual-threads", enabled ? "true" : "false");
    }
}
