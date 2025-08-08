package org.funfix.tasks.jvm;

import org.jetbrains.annotations.NonBlocking;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.ExecutionException;

/**
 * Represents the outcome of a finished task.
 *
 * @param <T> is the type of the value that the task completed with
 */
public sealed interface Outcome<T extends @Nullable Object>
    permits Outcome.Success, Outcome.Failure, Outcome.Cancellation {

    /**
     * Returns the value of the task if it was successful, or throws an exception.
     *
     * @return the successful value (in case the outcome is a {@link Success})
     * @throws ExecutionException        if the task failed with an exception
     * @throws TaskCancellationException if the task was cancelled
     */
    @NonBlocking
    T getOrThrow() throws ExecutionException, TaskCancellationException;

    /**
     * Converts this task outcome to an {@link ExitCase}.
     */
    ExitCase toTaskExitCase();

    /**
     * Signals a successful result of the task.
     */
    record Success<T extends @Nullable Object>(T value)
        implements Outcome<T> {

        @Override
        public T getOrThrow() { return value; }

        @Override
        public ExitCase toTaskExitCase() { return ExitCase.succeeded(); }
    }

    /**
     * Signals that the task failed.
     */
    record Failure<T extends @Nullable Object>(Throwable exception)
        implements Outcome<T> {

        public T getOrThrow() throws ExecutionException {
            throw new ExecutionException(exception);
        }

        @Override
        public ExitCase toTaskExitCase() { return ExitCase.failed(exception); }
    }

    /**
     * Signals that the task was cancelled.
     */
    record Cancellation<T extends @Nullable Object>()
        implements Outcome<T> {

        @Override
        public T getOrThrow() throws TaskCancellationException {
            throw new TaskCancellationException();
        }

        @Override
        public ExitCase toTaskExitCase() { return ExitCase.canceled(); }

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
