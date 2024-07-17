@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.funfix.tasks

import org.jetbrains.annotations.NonBlockingExecutor
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.util.*
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import kotlin.concurrent.getOrSet

/**
 * Provides utilities for working with [Executor] instances, optimized
 * for common use-cases.
 */
public actual object TaskExecutors {
    // Creating 2 reusable references that look the same , but are not.
    // The returned value of `VirtualThreads.areVirtualThreadsSupported`
    // can be disabled and re-enabled for testing purposes, and we need
    // to make sure that the references are different.

    private val sharedVirtualIORef by lazy {
        if (!VirtualThreads.areVirtualThreadsSupported())
            throw IllegalStateException("Virtual threads are not supported")
        unlimitedThreadPoolForIO("tasks-io")
    }

    private val sharedPlatformIORef by lazy {
        if (VirtualThreads.areVirtualThreadsSupported())
            throw IllegalStateException("Virtual threads should be supported")
        unlimitedThreadPoolForIO("tasks-io")
    }

    /**
     * Returns a shared [Executor] meant for blocking I/O tasks.
     * The reference gets lazily initialized on the first call.
     *
     * Uses [unlimitedThreadPoolForIO] to create the executor,
     * which will use virtual threads on Java 21+, or a plain
     * [Executors.newCachedThreadPool] on older JVM versions.
     */
    @JvmStatic
    public actual val global: Executor
        @JvmName("global")
        get() = if (VirtualThreads.areVirtualThreadsSupported())
            sharedVirtualIORef
        else
            sharedPlatformIORef

    /**
     * Creates an [Executor] meant for blocking I/O tasks, with an
     * unlimited number of threads.
     *
     * On Java 21 and above, the created [Executor] will run tasks on virtual threads.
     * On older JVM versions, it returns a plain [Executors.newCachedThreadPool].
     */
    @JvmStatic
    public fun unlimitedThreadPoolForIO(prefix: String): ExecutorService {
        if (VirtualThreads.areVirtualThreadsSupported())
            try {
                return VirtualThreads.executorService("$prefix-virtual-")
            } catch (ignored: VirtualThreads.NotSupportedException) {
            }
        return Executors.newCachedThreadPool(platformThreadFactory(prefix))
    }

    /**
     * Creates an [Executor] that executes thunks on the current thread, using
     * an internal [Trampoline](https://en.wikipedia.org/wiki/Trampoline_(computing))
     * to avoid stack overflows.
     */
    @JvmStatic
    public actual val trampoline: @NonBlockingExecutor Executor
        @JvmName("trampoline")
        get() = Trampoline

    private fun platformThreadFactory(prefix: String): ThreadFactory =
        ThreadFactory { r ->
            Thread(r).apply {
                name = "$prefix-platform-$id"
                isDaemon = true
            }
        }
}

/**
 * INTERNAL API, do not use!
 */
@NonBlockingExecutor
private object Trampoline: Executor {
    private val queue = ThreadLocal<LinkedList<Runnable>>()

    private fun eventLoop() {
        while (true) {
            val current = queue.get()
            if (current == null || current.isEmpty()) {
                return
            }
            val next = current.pollFirst()
            try {
                next?.run()
            } catch (e: Exception) {
                UncaughtExceptionHandler.logOrRethrow(e)
            }
        }
    }

    override fun execute(command: Runnable) {
        val current = queue.getOrSet { LinkedList() }
        current.add(command)
        if (current.size == 1) {
            try {
                eventLoop()
            } finally {
                queue.remove()
            }
        }
    }
}

/**
 * Provides utilities for working with virtual threads (from Project Loom,
 * shipped in Java 21+), in case the runtime supports them.
 *
 * NOTE — even if the runtime supports virtual threads, support can be
 * disabled by setting the system property `funfix.tasks.virtual-threads`
 * to `off`.
 */
public object VirtualThreads {
    private val newThreadPerTaskExecutorMethodHandle: MethodHandle?

    /**
     * Exception thrown when virtual threads aren't supported by the current JVM.
     */
    public class NotSupportedException(feature: String) : Exception("$feature is not supported on this JVM")

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
     * @param prefix the prefix to use for the names of the virtual threads. The
     *              default prefix is `tasks-io-virtual-`.
     *
     * @throws NotSupportedException if the current JVM does not support virtual threads.
     *
     * @return a new [ExecutorService] that uses virtual threads, the equivalent
     *         of `Executors.newThreadPerTaskExecutor`.
     */
    @JvmStatic
    @Throws(NotSupportedException::class)
    public fun executorService(prefix: String = DEFAULT_VIRTUAL_THREAD_NAME_PREFIX): ExecutorService {
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

    /**
     * Returns a [ThreadFactory] that creates virtual threads. The equivalent
     * of `Thread.ofVirtual`.
     *
     * @param prefix the prefix to use for the names of the virtual threads. If
     *               not specified, the default prefix is `tasks-io-virtual-`.
     *
     * @throws NotSupportedException if the current JVM does not support virtual
     * threads, or if support is turned off via the `funfix.tasks.virtual-threads`
     * system property.
     */
    @JvmStatic
    @Throws(NotSupportedException::class)
    public fun factory(prefix: String = DEFAULT_VIRTUAL_THREAD_NAME_PREFIX): ThreadFactory {
        if (!areVirtualThreadsSupported()) {
            throw NotSupportedException("Thread.ofVirtual")
        }
        try {
            val builderClass = Class.forName("java.lang.Thread\$Builder")
            val ofVirtualClass = Class.forName("java.lang.Thread\$Builder\$OfVirtual")
            val lookup = MethodHandles.lookup()
            val ofVirtualMethod = lookup.findStatic(Thread::class.java, "ofVirtual",
                MethodType.methodType(ofVirtualClass)
            )
            var builder = ofVirtualMethod.invoke()
            val nameMethod = lookup.findVirtual(ofVirtualClass, "name",
                MethodType.methodType(ofVirtualClass, String::class.java, Long::class.javaPrimitiveType)
            )
            val factoryMethod = lookup.findVirtual(builderClass, "factory",
                MethodType.methodType(ThreadFactory::class.java)
            )
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

    /**
     * Returns `true` if the given [Thread] is a virtual thread, or `false`
     * otherwise.
     */
    @JvmStatic
    public fun isVirtualThread(th: Thread): Boolean {
        try {
            if (isVirtualMethodHandle != null) {
                return isVirtualMethodHandle.invoke(th) as Boolean
            }
        } catch (e: Throwable) {
            // Ignored
        }
        return false
    }

    /**
     * Returns `true` if virtual threads are supported by the current JVM,
     * and the feature is not disabled via the `funfix.tasks.virtual-threads`
     * system property.
     */
    @JvmStatic
    public fun areVirtualThreadsSupported(): Boolean {
        val sp = System.getProperty("funfix.tasks.virtual-threads")
        val disableFeature = sp.equals("off", ignoreCase = true)
            || sp.equals("false", ignoreCase = true)
            || sp.equals("no", ignoreCase = true)
            || sp == "0"
            || sp.equals("disabled", ignoreCase = true)
        return !disableFeature && isVirtualMethodHandle != null && newThreadPerTaskExecutorMethodHandle != null
    }

    private const val DEFAULT_VIRTUAL_THREAD_NAME_PREFIX =
        "tasks-io-virtual-"
}
