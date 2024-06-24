package org.funfix.tasks

import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory

object IOPool {
    @Volatile
    private var defaultPool: Executor? = null

    @JvmStatic
    fun common(): Executor =
        // Using double-checked locking to avoid synchronization
        defaultPool ?: synchronized(this) {
            if (defaultPool == null) {
                val es = VirtualThreads.executorService("io-common")
                defaultPool = es ?: Executors.newCachedThreadPool { r ->
                    val t = Thread(r)
                    t.name = "io-common-" + t.id
                    t
                }
            }
            return@synchronized defaultPool!!
        }
}

object VirtualThreads {
    private val newThreadPerTaskExecutorMethodHandle =
        try {
            val cl = ClassLoader.getSystemClassLoader()
            val executorsClass = cl.loadClass("java.util.concurrent.Executors")
            val lookup = MethodHandles.lookup()
            lookup.findStatic(executorsClass, "newThreadPerTaskExecutor",
                MethodType.methodType(
                    ExecutorService::class.java,
                    ThreadFactory::class.java
                ))
        } catch (e: Throwable) {
            null
        }

    /**
     * Create a virtual thread executor, returns `null` if failed.
     *
     * This function can only return a non-`null` value if running on Java 21 or later,
     * as it uses reflection to access the `Executors.newVirtualThreadPerTaskExecutor`.
     */
    @JvmStatic fun executorService(prefix: String): ExecutorService? =
        try {
            factory(prefix)?.let { factory ->
                newThreadPerTaskExecutorMethodHandle?.invoke(factory) as ExecutorService
            }
        } catch (e: Throwable) {
            null
        }

    /**
     * Create a virtual thread factory, returns `null` if failed.
     *
     * This function only returns a `ThreadFactory` if the current JVM supports
     * virtual threads, therefore, it may only return a non-`null` value if
     * running on Java 21 or later.
     *
     * Function copied from the Pekko project, copyright Apache Software Foundation,
     * licensed under the Apache Public License 2.0:
     * <https://github.com/apache/pekko/pull/1299/files>
     */
    @JvmStatic
    fun factory(prefix: String): ThreadFactory? =
        try {
            val builderClass = ClassLoader.getSystemClassLoader().loadClass("java.lang.Thread\$Builder")
            val ofVirtualClass = ClassLoader.getSystemClassLoader().loadClass("java.lang.Thread\$Builder\$OfVirtual")
            val lookup = MethodHandles.lookup()
            val ofVirtualMethod = lookup.findStatic(Thread::class.java, "ofVirtual", MethodType.methodType(ofVirtualClass))
            var builder = ofVirtualMethod.invoke()
            val nameMethod = lookup.findVirtual(
                ofVirtualClass,
                "name",
                MethodType.methodType(ofVirtualClass, String::class.java, Long::class.javaPrimitiveType)
            )
            val factoryMethod = lookup.findVirtual(builderClass, "factory", MethodType.methodType(ThreadFactory::class.java))
            builder = nameMethod.invoke(builder, "$prefix-virtual-thread-", 0L)
            factoryMethod.invoke(builder) as ThreadFactory
        } catch (e: Throwable) {
            null
        }

    private val isVirtualMethodHandle: MethodHandle? =
        try {
            val cl = ClassLoader.getSystemClassLoader()
            val threadClass = cl.loadClass("java.lang.Thread")
            val lookup = MethodHandles.lookup()
            lookup.findVirtual(threadClass, "isVirtual", MethodType.methodType(Boolean::class.java))
        } catch (e: Throwable) {
            null
        }

    /**
     * Returns `true` if the given thread is a virtual thread.
     *
     * This function only returns `true` if the current JVM supports virtual threads.
     */
    @JvmStatic fun isVirtualThread(th: Thread): Boolean =
        try {
            if (isVirtualMethodHandle != null)
                isVirtualMethodHandle.invoke(th) as Boolean
            else
                false
        } catch (e: Throwable) {
            false
        }
}
