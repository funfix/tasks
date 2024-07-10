package org.funfix.tasks;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.Duration;

/**
 * Represents a {@link Task} that has started execution and is running
 * concurrently.
 *
 * @param <T> is the type of the value that the task will complete with
 */
@NullMarked
public interface Fiber<T, E extends Exception> extends Cancellable {
    /**
     * @return the {@link Outcome} of the task, if it has completed,
     * or `null` if the task is still running.
     */
    @Nullable Outcome<T, E> outcome();

    /**
     * Blocks the current thread until the result is available.
     *
     * @throws InterruptedException if the current thread was interrupted;
     * however, the concurrent task will still be running. Interrupting a `join`
     * does not cancel the task. For that, use [cancel].
     */
    void joinBlocking() throws InterruptedException;

    /**
     * Blocks the current thread until the result is available or
     * the given {@code timeout} is reached.
     *
     * @param timeout the maximum time to wait
     *
     * @throws InterruptedException if the current thread was interrupted;
     * however, the interruption of the current thread does not interrupt the
     * fiber.
     *
     * @return {@code true} if the task has completed, {@code false} if the
     * timeout was reached
     */
    boolean joinBlockingTimed(@Nullable Duration timeout) throws InterruptedException;

    /**
     * Invokes the given `Runnable` when the task completes.
     */
    Cancellable joinAsync(Runnable onComplete);
}
