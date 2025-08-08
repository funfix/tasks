package org.funfix.tasks.jvm;

import org.jspecify.annotations.Nullable;

/**
 * Represents an exception that is thrown when a task is cancelled.
 *
 * <p>{@code java.lang.InterruptedException} has the meaning that the current thread was
 * interrupted. This is a JVM-specific exception. If the current thread
 * is waiting on a concurrent job, if an {@code InterruptedException} is thrown,
 * this doesn't mean that the concurrent job was cancelled; such a behavior
 * depending on the type of blocking operation being performed.</p>
 *
 * <p>We need to distinguish between {@code java.lang.InterruptedException} and cancellation,
 * because there are cases where a concurrent job is cancelled without
 * the current thread being interrupted, and also, the current thread
 * might be interrupted without the concurrent job being cancelled.</p>
 */
public class TaskCancellationException extends Exception {
    public TaskCancellationException() {
        super();
    }

    public TaskCancellationException(@Nullable String message) {
        super(message);
    }
}
