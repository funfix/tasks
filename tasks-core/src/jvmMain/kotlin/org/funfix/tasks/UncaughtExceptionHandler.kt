package org.funfix.tasks

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual object UncaughtExceptionHandler {
    @JvmStatic
    actual fun logException(t: Throwable) {
        val thread = Thread.currentThread()
        val logger: Thread.UncaughtExceptionHandler =
            thread.uncaughtExceptionHandler
                ?: Thread.getDefaultUncaughtExceptionHandler()
                ?: Thread.UncaughtExceptionHandler { _, e -> e.printStackTrace(System.err) }
        logger.uncaughtException(thread, t)
    }
}
