package org.funfix.tasks.jvm;

import org.jspecify.annotations.NullMarked;

/**
 * For signaling to finalizers how a task exited.
 * <p>
 * Similar to {@link Outcome}, but without the result. Used by {@link Resource}
 * to indicate how a resource was released.
 */
@NullMarked
public sealed interface ExitCase {
    /**
     * Signals that the task completed. Used for successful completion, but
     * also for cases in which the outcome is unknown.
     * <br>
     * This is a catch-all result. For example, this is the value passed
     * to {@link Resource.Allocated} in {@code releaseTask} when the resource
     * is used as an {@link AutoCloseable}, as the {@code AutoCloseable} protocol
     * does not distinguish between successful completion, failure, or cancellation.
     * <br>
     * When receiving a {@code Completed} exit case, the caller shouldn't
     * assume that the task was successful.
     */
    record Completed() implements ExitCase {
        private static final Completed INSTANCE = new Completed();
    }

    /**
     * Signals that the task failed with a known exception.
     * <br>
     * Used in {@link Resource.Allocated} to indicate that the resource
     * is being released due to an exception.
     *
     * @param error is the exception thrown
     */
    record Failed(Throwable error) implements ExitCase {}

    /**
     * Signals that the task was cancelled.
     * <br>
     * Used in {@link Resource.Allocated} to indicate that the resource
     * is being released due to a cancellation (e.g., the thread was
     * interrupted).
     */
    record Canceled() implements ExitCase {
        private static final Canceled INSTANCE = new Canceled();
    }

    static ExitCase succeeded() {
        return Completed.INSTANCE;
    }

    static ExitCase failed(Throwable error) {
        return new Failed(error);
    }

    static ExitCase canceled() {
        return Canceled.INSTANCE;
    }
}
