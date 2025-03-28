@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.funfix.tasks.kotlin

/**
 * Utilities for handling uncaught exceptions.
 */
public actual object UncaughtExceptionHandler {
    public actual fun rethrowIfFatal(e: Throwable) {
        org.funfix.tasks.jvm.UncaughtExceptionHandler.rethrowIfFatal(e)
    }

    public actual fun logOrRethrow(e: Throwable) {
        org.funfix.tasks.jvm.UncaughtExceptionHandler.logOrRethrow(e)
    }
}
