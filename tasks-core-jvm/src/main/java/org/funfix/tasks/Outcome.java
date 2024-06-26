package org.funfix.tasks;

import org.jspecify.annotations.NullMarked;

import java.util.concurrent.ExecutionException;

/**
 * Represents the outcome of a finished task.
 *
 * @param <T> is the type of the value that the task completed with
 */
@NullMarked
public sealed interface Outcome<T>
    permits Outcome.Success, Outcome.Failure, Outcome.Cancelled {

    /**
     * Returns the value of the task if it was successful, or throws an exception.
     *
     * @return the successful value (in case the outcome is a {@link Success})
     *
     * @throws ExecutionException if the task failed with an exception
     * @throws CancellationException if the task was cancelled
     */
    default T getOrThrow() throws ExecutionException, CancellationException {
        if (this instanceof final Success<T> s) return s.value();
        if (this instanceof final Failure<T> f) throw new ExecutionException(f.exception());
        throw new CancellationException();
    }

    /**
     * Signals a successful result of the task.
     *
     * @param value is the value that the task completed with
     */
    record Success<T>(T value) implements Outcome<T> {}

    /**
     * Signals that the task failed.
     *
     * @param exception is the exception that the task failed with
     */
    record Failure<T>(Throwable exception) implements Outcome<T> {}

    /**
     * Signals that the task was cancelled.
     */
    record Cancelled<T>() implements Outcome<T> {}

    /**
     * @return a {@link Success} outcome.
     */
    static <T> Outcome<T> success(final T value) {
        return new Success<>(value);
    }

    /**
     * @return a {@link Failure} outcome.
     */
    static <T> Outcome<T> failure(final Throwable error) {
        return new Failure<>(error);
    }

    /**
     * @return a {@link Cancelled} outcome.
     */
    static <T> Outcome<T> cancelled() {
        return new Cancelled<>();
    }
}
