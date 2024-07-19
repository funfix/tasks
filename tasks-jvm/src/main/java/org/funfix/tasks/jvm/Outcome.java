package org.funfix.tasks.jvm;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import java.util.concurrent.ExecutionException;

/**
 * Represents the outcome of a finished task.
 *
 * @param <T> is the type of the value that the task completed with
 */
@NullMarked
public sealed interface Outcome<T extends @Nullable Object>
        permits Outcome.Success, Outcome.Failure, Outcome.Cancellation {

    /**
     * Returns the value of the task if it was successful, or throws an exception.
     *
     * @return the successful value (in case the outcome is a {@link Success})
     *
     * @throws ExecutionException if the task failed with an exception
     * @throws TaskCancellationException if the task was cancelled
     */
    T getOrThrow() throws ExecutionException, TaskCancellationException;

    /**
     * Signals a successful result of the task.
     *
     * @param value is the value that the task completed with
     */
    record Success<T extends @Nullable Object>(
            T value
    ) implements Outcome<T> {
        @Override
        public T getOrThrow() {
            return value;
        }

        public static <T> Outcome<T> instance(final T value) {
            return new Success<>(value);
        }
    }

    /**
     * Signals that the task failed.
     *
     * @param exception is the exception that the task failed with
     */
    record Failure<T extends @Nullable Object>(
            Throwable exception
    ) implements Outcome<T> {
        @Override
        public T getOrThrow() throws ExecutionException {
            throw new ExecutionException(exception);
        }

        public static <T> Outcome<T> instance(final Throwable exception) {
            return new Failure<>(exception);
        }
    }

    /**
     * Signals that the task was cancelled.
     */
    record Cancellation<T extends @Nullable Object>() implements Outcome<T> {
        @Override
        public T getOrThrow() throws TaskCancellationException {
            throw new TaskCancellationException();
        }

        @SuppressWarnings("unchecked")
        public static <T> Outcome<T> instance() {
            return (Outcome<T>) INSTANCE;
        }

        private static final Cancellation<?> INSTANCE =
                new Cancellation<>();
    }

    /**
     * @return a {@link Success} outcome.
     */
    static <T extends @Nullable Object> Outcome<T> success(final T value) {
        return new Success<>(value);
    }

    /**
     * @return a {@link Failure} outcome.
     */
    static <T> Outcome<T> failure(final Throwable error) {
        return new Failure<>(error);
    }

    /**
     * @return a {@link Cancellation} outcome.
     */
    static <T> Outcome<T> cancellation() {
        return new Cancellation<>();
    }
}
