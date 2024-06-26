package org.funfix.tasks;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;

@NullMarked
public final class VirtualThreads {
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
            //noinspection JavaLangInvokeHandleSignature
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

    /**
     * Create a virtual thread factory, returns `null` if failed.
     * <p>
     * This function only returns a {@code ThreadFactory} if the current JVM supports
     * virtual threads, therefore, it may only return a non-{@code null} value if
     * running on Java 21 or later.
     *
     * @throws NotSupportedException if the current JVM does not support virtual threads.
     */
    public static ThreadFactory factory(final String prefix) throws NotSupportedException {
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
            //noinspection JavaLangInvokeHandleSignature
            tempHandle = lookup.findVirtual(
                threadClass,
                "isVirtual",
                MethodType.methodType(boolean.class));
        } catch (final Throwable e) {
            tempHandle = null;
        }
        isVirtualMethodHandle = tempHandle;
    }

    /**
     * Returns {@code true} if the given thread is a virtual thread.
     * <p>
     * This function only returns {@code true} if the current JVM supports virtual threads.
     */
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
        return isVirtualMethodHandle != null && newThreadPerTaskExecutorMethodHandle != null;
    }
}
