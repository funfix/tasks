package org.funfix.tasks.jvm;

/**
 * Utilities for handling uncaught exceptions.
 */
public final class UncaughtExceptionHandler {
    public static void rethrowIfFatal(final Throwable e) {
        if (e instanceof VirtualMachineError error) {
            throw error;
        }
        if (e instanceof ThreadDeath death) {
            throw death;
        }
        if (e instanceof LinkageError linkage) {
            throw linkage;
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
