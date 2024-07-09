package org.funfix.tasks;

import org.jspecify.annotations.NullMarked;

/**
 * Represents the outcome of a finished task.
 *
 * @param <T> is the type of the value that the task completed with
 * @param <E> is the type of the checked exception that the task failed with
 */
@NullMarked
public sealed interface Outcome<T, E extends Exception> {
    /**
     * Returns the value of the task if it was successful, or throws an exception.
     *
     * @return the successful value (in case the outcome is a {@link Success})
     *
     * @throws E if the task failed with a typed exception ({@link TypedFailure})
     * @throws CancellationException if the task was cancelled ({@link Cancellation})
     * @throws RuntimeException if the task failed with a runtime exception ({@link RuntimeFailure})
     */
    T getOrThrow() throws E, CancellationException;

    /**
     * Signals a successful result of the task.
     *
     * @param value is the value that the task completed with
     */
    record Success<T, E extends Exception>(T value) implements Outcome<T, E> {
        @Override
        public T getOrThrow() {
            return value;
        }
    }

    static <T, E extends Exception> Outcome<T, E> success(T value) {
        return new Success<>(value);
    }

    /**
     * Signals that the task failed with a typed (checked) exception.
     *
     * @param exception is the checked exception that the task failed with
     * @param <E> is the type of the checked exception that the task failed with
     */
    record TypedFailure<T, E extends Exception>(E exception) implements Outcome<T, E> {
        @Override
        public T getOrThrow() throws E {
            throw exception;
        }
    }

    static <T, E extends Exception> Outcome<T, E> typedFailure(E exception) {
        return new TypedFailure<>(exception);
    }

    /**
     * Signals that the task failed with a {@link RuntimeException}.
     *
     * @param exception is the runtime exception that the task failed with
     */
    record RuntimeFailure<T, E extends Exception>(RuntimeException exception) implements Outcome<T, E> {
        @Override
        public T getOrThrow() {
            throw exception;
        }
    }

    static <T, E extends Exception> Outcome<T, E> runtimeFailure(RuntimeException exception) {
        return new RuntimeFailure<>(exception);
    }

    /**
     * Signals that the task was cancelled.
     */
    record Cancellation<T, E extends Exception>() implements Outcome<T, E> {
        @Override
        public T getOrThrow() throws CancellationException {
            throw new CancellationException();
        }

        static final Cancellation<?, ?> INSTANCE = new Cancellation<>();
    }

    @SuppressWarnings("unchecked")
    static <T, E extends Exception> Outcome<T, E> cancellation() {
        return (Outcome<T, E>) Cancellation.INSTANCE;
    }
}
