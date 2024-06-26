package org.funfix.tasks;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Represents a {@link Task} that has started execution and is running concurrently.
 *
 * @param <T> is the type of the value that the task will complete with
 */
@NullMarked
public interface Fiber<T> extends Cancellable {
    /**
     * @return the {@link Outcome} of the task, if it has completed,
     * or `null` if the task is still running.
     */
    @Nullable Outcome<T> outcome();

    /**
     * Blocks the current thread until the result is available.
     *
     * @throws InterruptedException if the current thread was interrupted; however,
     * the concurrent task will still be running. Interrupting a `join` does not
     * cancel the task. For that, use [cancel].
     */
    void joinBlocking() throws InterruptedException;

    /**
     * Invokes the given `Runnable` when the task completes.
     */
    Cancellable joinAsync(Runnable onComplete);
}
