package org.funfix.tasks

/**
 * Logs an uncaught exception.
 *
 * On top of the JVM, this function will use the default uncaught exception
 * handler of the current thread. On top of JS, it will use `console.log`.
 */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect object UncaughtExceptionHandler {
    @JvmStatic fun logException(t: Throwable)
}
