package org.funfix.tasks

import org.funfix.tasks.internals.VirtualThreads
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

    @JvmStatic
    fun sharedThreadFactory(): ThreadFactory {
        if (VirtualThreads.areVirtualThreadsSupported())
            try {
                return VirtualThreads.factory()
            } catch (ignored: VirtualThreads.NotSupportedException) {
            }
        return platformThreadFactory("common-io")
    }

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
    fun unlimitedThreadPoolForIO(prefix: String): ExecutorService {
        if (VirtualThreads.areVirtualThreadsSupported())
            try {
                return VirtualThreads.executorService("$prefix-virtual-")
            } catch (ignored: VirtualThreads.NotSupportedException) {
            }
        return Executors.newCachedThreadPool(platformThreadFactory(prefix))
    }

    @Suppress("DEPRECATION")
    private fun platformThreadFactory(prefix: String): ThreadFactory =
        ThreadFactory { r ->
            Thread(r).apply {
                name = "$prefix-platform-$id"
                isDaemon = true
            }
        }
}
