@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.funfix.tasks.kotlin

/**
 * Utilities for handling uncaught exceptions.
 */
public expect object UncaughtExceptionHandler {
    /**
     * Used for filtering the fatal exceptions that should
     * crash the process (e.g., `OutOfMemoryError`).
     */
    public fun rethrowIfFatal(e: Throwable)

    /**
     * Logs a caught exception, or rethrows it if it's fatal.
     */
    public fun logOrRethrow(e: Throwable)
}
