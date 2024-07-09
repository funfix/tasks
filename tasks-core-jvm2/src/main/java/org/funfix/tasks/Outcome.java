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
    permits Outcome.Succeeded, Outcome.Failed, Outcome.Cancelled {

    /**
     * Returns the value of the task if it was successful, or throws an exception.
     *
     * @return the successful value (in case the outcome is a {@link Succeeded})
     *
     * @throws ExecutionException if the task failed with an exception
     * @throws CancellationException if the task was cancelled
     */
    T getOrThrow() throws ExecutionException, CancellationException;

    /**
     * Signals a successful result of the task.
     *
     * @param value is the value that the task completed with
     */
    record Succeeded<T>(T value) implements Outcome<T> {
        @Override
        public T getOrThrow() {
            return value;
        }
    }

    /**
     * Signals that the task failed.
     *
     * @param exception is the exception that the task failed with
     */
    record Failed<T>(Throwable exception) implements Outcome<T> {
        @Override
        public T getOrThrow() throws ExecutionException {
            throw new ExecutionException(exception);
        }
    }

    /**
     * Signals that the task was cancelled.
     */
    record Cancelled<T>() implements Outcome<T> {
        @Override
        public T getOrThrow() throws CancellationException {
            throw new CancellationException();
        }
    }

    /**
     * @return a {@link Succeeded} outcome.
     */
    static <T> Outcome<T> succeeded(final T value) {
        return new Succeeded<>(value);
    }

    /**
     * @return a {@link Failed} outcome.
     */
    static <T> Outcome<T> failed(final Throwable error) {
        return new Failed<>(error);
    }

    /**
     * @return a {@link Cancelled} outcome.
     */
    static <T> Outcome<T> cancelled() {
        return new Cancelled<>();
    }
}
