package org.funfix.tasks.jvm;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.ExecutionException;

/**
 * Represents the outcome of a finished task.
 *
 * @param <T> is the type of the value that the task completed with
 */
@NullMarked
abstract class Outcome<T extends @Nullable Object> {
    private Outcome() {
        super();
    }

    /**
     * Returns the value of the task if it was successful, or throws an exception.
     *
     * @return the successful value (in case the outcome is a {@link Success})
     * @throws ExecutionException        if the task failed with an exception
     * @throws TaskCancellationException if the task was cancelled
     */
    abstract T getOrThrow() throws ExecutionException, TaskCancellationException;

    /**
     * Signals a successful result of the task.
     */
    @ToString
    @EqualsAndHashCode(callSuper = false)
    @RequiredArgsConstructor
    static final class Success<T extends @Nullable Object>
        extends Outcome<T> {

        private final T value;

        public T value() {
            return value;
        }

        @Override
        public T getOrThrow() {
            return value;
        }
    }

    /**
     * Signals that the task failed.
     */
    @ToString
    @EqualsAndHashCode(callSuper = false)
    @RequiredArgsConstructor
    static final class Failure<T extends @Nullable Object>
        extends Outcome<T> {

        private final Throwable exception;
        public Throwable exception() {
            return exception;
        }

        @Override
        public T getOrThrow() throws ExecutionException {
            throw new ExecutionException(exception);
        }
    }

    /**
     * Signals that the task was cancelled.
     */
    @Data
    @EqualsAndHashCode(callSuper = false)
    static final class Cancellation<T extends @Nullable Object>
        extends Outcome<T> {

        @Override
        public T getOrThrow() throws TaskCancellationException {
            throw new TaskCancellationException();
        }

        private static final Cancellation<?> INSTANCE =
            new Cancellation<>();
    }

    static <T extends @Nullable Object> Outcome<T> success(final T value) {
        return new Success<>(value);
    }

    static <T extends @Nullable Object> Outcome<T> failure(final Throwable error) {
        return new Failure<>(error);
    }

    @SuppressWarnings("unchecked")
    static <T extends @Nullable Object> Outcome<T> cancellation() {
        return (Outcome<T>) Cancellation.INSTANCE;
    }
}
