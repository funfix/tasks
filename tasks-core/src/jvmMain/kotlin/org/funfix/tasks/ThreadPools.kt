package org.funfix.tasks

import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory

/**
 * Provides utilities for working with [Executor] instances, optimized
 * for common use-cases.
 */
object ThreadPools {
    @Volatile
    private var sharedVirtualIORef: Executor? = null

    @Volatile
    private var sharedPlatformIORef: Executor? = null

    /**
     * Returns a shared [Executor] meant for blocking I/O tasks.
     * The reference gets lazily initialized on the first call.
     *
     * Uses [unlimitedThreadPoolForIO] to create the executor,
     * which will use virtual threads on Java 21+, or a plain
     * [Executors.newCachedThreadPool] on older JVM versions.
     */
    @JvmStatic
    fun sharedIO(): Executor =
        if (VirtualThreads.areVirtualThreadsSupported())
            sharedVirtualIO()
        else
            sharedPlatformIO()

    private fun sharedPlatformIO(): Executor {
        // Using double-checked locking to avoid synchronization
        if (sharedPlatformIORef == null) {
            synchronized(ThreadPools::class.java) {
                if (sharedPlatformIORef == null) {
                    sharedPlatformIORef = unlimitedThreadPoolForIO("common-io")
                }
            }
        }
        return sharedPlatformIORef!!
    }

    private fun sharedVirtualIO(): Executor {
        // Using double-checked locking to avoid synchronization
        if (sharedVirtualIORef == null) {
            synchronized(ThreadPools::class.java) {
                if (sharedVirtualIORef == null) {
                    sharedVirtualIORef = unlimitedThreadPoolForIO("common-io")
                }
            }
        }
        return sharedVirtualIORef!!
    }

    /**
     * Creates an [Executor] meant for blocking I/O tasks, with an
     * unlimited number of threads.
     *
     * On Java 21 and above, the created [Executor] will run tasks on virtual threads.
     * On older JVM versions, it returns a plain [Executors.newCachedThreadPool].
     */
    @JvmStatic
    fun unlimitedThreadPoolForIO(prefix: String): ExecutorService =
        if (VirtualThreads.areVirtualThreadsSupported())
            try {
                VirtualThreads.executorService("$prefix-virtual-")
            } catch (ignored: VirtualThreads.NotSupportedException) {
                Executors.newCachedThreadPool { r ->
                    Thread(r).apply {
                        name = "$prefix-platform-${threadId()}"
                    }
                }
            }
        else
            Executors.newCachedThreadPool { r ->
                Thread(r).apply {
                    name = "$prefix-platform-${threadId()}"
                }
            }
}

/**
 * Internal utilities — not exposed yet, because lacking Loom support is only
 * temporary.
 */
private object VirtualThreads {
    private val newThreadPerTaskExecutorMethodHandle: MethodHandle?

    class NotSupportedException(feature: String) : Exception("$feature is not supported on this JVM")

    init {
        var tempHandle: MethodHandle?
        try {
            val executorsClass = Class.forName("java.util.concurrent.Executors")
            val lookup = MethodHandles.lookup()
            tempHandle = lookup.findStatic(
                executorsClass,
                "newThreadPerTaskExecutor",
                MethodType.methodType(ExecutorService::class.java, ThreadFactory::class.java)
            )
        } catch (e: Throwable) {
            tempHandle = null
        }
        newThreadPerTaskExecutorMethodHandle = tempHandle
    }

    /**
     * Create a virtual thread executor, returns `null` if failed.
     *
     * This function can only return a non-`null` value if running on Java 21 or later,
     * as it uses reflection to access the `Executors.newVirtualThreadPerTaskExecutor`.
     *
     * @throws NotSupportedException if the current JVM does not support virtual threads.
     */
    @JvmStatic
    @Throws(NotSupportedException::class)
    fun executorService(prefix: String): ExecutorService {
        if (!areVirtualThreadsSupported()) {
            throw NotSupportedException("Executors.newThreadPerTaskExecutor")
        }
        var thrown: Throwable? = null
        try {
            val factory = factory(prefix)
            if (newThreadPerTaskExecutorMethodHandle != null) {
                return newThreadPerTaskExecutorMethodHandle.invoke(factory) as ExecutorService
            }
        } catch (e: NotSupportedException) {
            throw e
        } catch (e: Throwable) {
            thrown = e
        }
        val e2 = NotSupportedException("Executors.newThreadPerTaskExecutor")
        if (thrown != null) e2.addSuppressed(thrown)
        throw e2
    }

    @JvmStatic
    @Throws(NotSupportedException::class)
    fun factory(prefix: String = VIRTUAL_THREAD_NAME_PREFIX): ThreadFactory {
        if (!areVirtualThreadsSupported()) {
            throw NotSupportedException("Thread.ofVirtual")
        }
        try {
            val builderClass = Class.forName("java.lang.Thread\$Builder")
            val ofVirtualClass = Class.forName("java.lang.Thread\$Builder\$OfVirtual")
            val lookup = MethodHandles.lookup()
            val ofVirtualMethod = lookup.findStatic(Thread::class.java, "ofVirtual", MethodType.methodType(ofVirtualClass))
            var builder = ofVirtualMethod.invoke()
            val nameMethod = lookup.findVirtual(ofVirtualClass, "name", MethodType.methodType(ofVirtualClass, String::class.java, Long::class.javaPrimitiveType))
            val factoryMethod = lookup.findVirtual(builderClass, "factory", MethodType.methodType(ThreadFactory::class.java))
            builder = nameMethod.invoke(builder, prefix, 0L)
            return factoryMethod.invoke(builder) as ThreadFactory
        } catch (e: Throwable) {
            val e2 = NotSupportedException("Thread.ofVirtual")
            e2.addSuppressed(e)
            throw e2
        }
    }

    private val isVirtualMethodHandle: MethodHandle?

    init {
        var tempHandle: MethodHandle?
        try {
            val threadClass = Class.forName("java.lang.Thread")
            val lookup = MethodHandles.lookup()
            tempHandle = lookup.findVirtual(
                threadClass,
                "isVirtual",
                MethodType.methodType(Boolean::class.javaPrimitiveType)
            )
        } catch (e: Throwable) {
            tempHandle = null
        }
        isVirtualMethodHandle = tempHandle
    }

    @JvmStatic
    fun isVirtualThread(th: Thread): Boolean {
        try {
            if (isVirtualMethodHandle != null) {
                return isVirtualMethodHandle.invoke(th) as Boolean
            }
        } catch (e: Throwable) {
            // Ignored
        }
        return false
    }

    @JvmStatic
    fun areVirtualThreadsSupported(): Boolean {
        val sp = System.getProperty("funfix.tasks.virtual-threads")
        val disableFeature = sp.equals("off", ignoreCase = true)
                || sp.equals("false", ignoreCase = true)
                || sp.equals("no", ignoreCase = true)
                || sp == "0"
                || sp.equals("disabled", ignoreCase = true)
        return !disableFeature && isVirtualMethodHandle != null && newThreadPerTaskExecutorMethodHandle != null
    }

    const val VIRTUAL_THREAD_NAME_PREFIX = "common-io-virtual-"
}
