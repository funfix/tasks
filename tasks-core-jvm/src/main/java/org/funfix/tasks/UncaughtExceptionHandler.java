package org.funfix.tasks;

import org.jspecify.annotations.NullMarked;

@NullMarked
public final class UncaughtExceptionHandler {
    public static void rethrowIfFatal(final Throwable e) {
        if (e instanceof StackOverflowError) {
            // Stack-overflows should be reported as-is, instead of crashing
            // the process
            return;
        }
        if (e instanceof final Error error) {
            throw error;
        }
    }

    public static void logOrRethrowException(final Throwable e) {
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
