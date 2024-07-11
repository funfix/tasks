package org.funfix.tasks;

import org.jspecify.annotations.NullMarked;

@NullMarked
public final class UncaughtExceptionHandler {
    public static void logException(final Throwable e) {
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
