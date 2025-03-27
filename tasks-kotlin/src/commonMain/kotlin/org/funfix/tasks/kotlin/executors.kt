@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.funfix.tasks.kotlin

/**
 * An [Executor] is an abstraction for a thread-pool or a single-threaded
 * event-loop, used for running tasks.
 *
 * On the JVM, this is an alias for the `java.util.concurrent.Executor`
 * interface. On top of JavaScript, one way to implement this is via
 * `setTimeout`.
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
 * The global executor, used for running tasks that don't specify an
 * explicit executor.
 *
 * On top of the JVM, this is powered by "virtual threads" (project loom), if
 * the runtime supports it (Java 21+). Otherwise, it's an unlimited "cached"
 * thread-pool. On top of JavaScript, blocking I/O operations are not possible
 * in the browser, and discouraged in Node.js. JS runtimes don't have
 * multi-threading with shared-memory concurrency, so this will be just a plain
 * executor.
 */
public expect val SharedIOExecutor: Executor

/**
 * An [Executor] that runs tasks on the current thread.
 *
 * Uses a [trampoline](https://en.wikipedia.org/wiki/Trampoline_(computing))
 * to ensure that recursive calls don't blow the stack.
 *
 * Using this executor is useful for making asynchronous callbacks stack-safe.
 * Note, however, that the tasks get executed on the current thread, immediately,
 * even if the implementation guards against stack overflows.
 */
public expect val TrampolineExecutor: Executor
