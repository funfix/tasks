package org.funfix.tasks.jvm;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import java.util.concurrent.ExecutionException;

/**
 * Represents the outcome of a finished task.
 * <p>
 * <strong>INTERNAL API:</strong> Internal apis are subject to change or removal
 * without any notice. When code depends on internal APIs, it is subject to
 * breakage between minor version updates.
 *
 * @param <T> is the type of the value that the task completed with
 */
@ApiStatus.Internal
@NullMarked
public sealed interface Outcome<T extends @Nullable Object>
    permits Outcome.Success, Outcome.Failure, Outcome.Cancellation {

    /**
     * Returns the value of the task if it was successful, or throws an exception.
     *
     * @return the successful value (in case the outcome is a {@link Success})
     * @throws ExecutionException        if the task failed with an exception
     * @throws TaskCancellationException if the task was cancelled
     */
    T getOrThrow() throws ExecutionException, TaskCancellationException;

    /**
     * Signals a successful result of the task.
     */
    record Success<T extends @Nullable Object>(T value)
        implements Outcome<T> {

        @Override
        public T getOrThrow() { return value; }
    }

    /**
     * Signals that the task failed.
     */
    record Failure<T extends @Nullable Object>(Throwable exception)
        implements Outcome<T> {

        public T getOrThrow() throws ExecutionException {
            throw new ExecutionException(exception);
        }
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
