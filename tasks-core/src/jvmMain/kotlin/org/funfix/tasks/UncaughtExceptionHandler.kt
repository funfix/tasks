package org.funfix.tasks

/**
 * Logs an uncaught exception.
 *
 * On top of the JVM, this function will use the default uncaught exception
 * handler of the current thread. On top of JS, it will use `console.log`.
 */
object UncaughtExceptionHandler {
    @JvmStatic
    fun logException(t: Throwable) {
        val thread = Thread.currentThread()
        val logger: Thread.UncaughtExceptionHandler =
            thread.uncaughtExceptionHandler
                ?: Thread.getDefaultUncaughtExceptionHandler()
                ?: Thread.UncaughtExceptionHandler { _, e -> e.printStackTrace(System.err) }
        logger.uncaughtException(thread, t)
    }
}
