package org.funfix.tasks.jvm;

import org.jspecify.annotations.NullMarked;

/**
 * Utilities for handling uncaught exceptions.
 */
@NullMarked
public final class UncaughtExceptionHandler {
    public static void rethrowIfFatal(final Throwable e) {
        if (e instanceof StackOverflowError) {
            // Stack-overflows should be reported as-is, instead of crashing
            // the process
            return;
        }
        if (e instanceof Error) {
            throw (Error) e;
        }
    }

    public static void logOrRethrow(final Throwable e) {
        rethrowIfFatal(e);
        final var thread = Thread.currentThread();
        var logger = thread.getUncaughtExceptionHandler();
        if (logger == null) {
            logger = Thread.getDefaultUncaughtExceptionHandler();
        }
        if (logger == null) {
            logger = (t, e1) -> e1.printStackTrace(System.err);
        }
        logger.uncaughtException(thread, e);
    }
}
