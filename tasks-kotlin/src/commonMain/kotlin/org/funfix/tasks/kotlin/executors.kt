@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.funfix.tasks.kotlin

/**
 * An [Executor] is an abstraction for a thread-pool or a single-threaded
 * event-loop, used for running tasks.
 *
 * On the JVM, this is an alias for the `java.util.concurrent.Executor`
 * interface.
 */
public expect fun interface Executor {
    public fun execute(command: Runnable)
}

/**
 * A simple interface for a task that can be executed asynchronously.
 *
 * On the JVM, this is an alias for the `java.lang.Runnable` interface.
 */
public expect fun interface Runnable {
    public fun run()
}

/**
 * the global executor, used for running tasks that don't specify an
 * explicit executor.
 *
 * On top of the JVM, this is powered by "virtual threads" (project loom), if
 * the runtime supports it. Otherwise, it's an unlimited "cached" thread-pool.
 */
public expect val BlockingIOExecutor: Executor

/**
 * An [Executor] that runs tasks on the current thread.
 *
 * Uses a [trampoline](https://en.wikipedia.org/wiki/Trampoline_(computing))
 * to ensure that recursive calls don't blow the stack.
 *
 * Using this executor is useful for making asynchronous callbacks stack-safe.
 */
public expect val TrampolineExecutor: Executor
