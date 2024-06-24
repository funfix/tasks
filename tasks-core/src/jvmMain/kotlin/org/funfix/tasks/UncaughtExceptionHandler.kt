package org.funfix.tasks

object UncaughtExceptionHandler {
    @JvmStatic fun logException(t: Throwable) {
        val thread = Thread.currentThread()
        val logger: Thread.UncaughtExceptionHandler =
            thread.uncaughtExceptionHandler
                ?: Thread.getDefaultUncaughtExceptionHandler()
                ?: Thread.UncaughtExceptionHandler { _, e -> e.printStackTrace(System.err) }
        logger.uncaughtException(thread, t)
    }
}
