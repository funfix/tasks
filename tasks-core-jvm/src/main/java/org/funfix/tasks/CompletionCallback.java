package org.funfix.tasks;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Represents a callback that will be invoked when a task completes.
 * <p>
 * A task can complete either successfully with a value, or with an exception,
 * or it can be cancelled.
 * </p><p>
 * MUST BE idempotent AND thread-safe.
 * </p>
 * @param <T> is the type of the value that the task will complete with
 * @param <E> is the type of the checked exception that the task may fail with
 */
@NullMarked
public interface CompletionCallback<T, E extends Exception> extends Serializable {
    /**
     * Must be called when the task completes successfully.
     *
     * @param value is the successful result of the task, to be signaled
     */
    void onSuccess(T value);

    /**
     * Must be called when the task completes with the parameterized checked
     * exception.
     *
     * @param e is the checked exception that the task failed with
     */
    void onTypedFailure(E e);

    /**
     * Must be called when the task completes with a runtime exception.
     *
     * @param e is the runtime exception that the task failed with
     */
    void onRuntimeFailure(RuntimeException e);

    /**
     * Must be called when the task is cancelled.
     */
    void onCancellation();

    /**
     * Signals a final {@link Outcome} to this listener.
     */
    default void onCompletion(final Outcome<? extends T, ? extends E> outcome) {
        if (outcome instanceof final Outcome.Success<? extends T, ? extends E> succeeded) {
            onSuccess(succeeded.value());
        } else if (outcome instanceof final Outcome.TypedFailure<? extends T, ? extends E> failed) {
            onTypedFailure(failed.exception());
        } else if (outcome instanceof final Outcome.RuntimeFailure<? extends T, ? extends E> failed) {
            onRuntimeFailure(failed.exception());
        } else {
            onCancellation();
        }
    }

    /**
     * @return a {@code CompletionListener} that does nothing.
     */
    static <T, E extends Exception> CompletionCallback<T, E> empty() {
        return new CompletionCallback<>() {
            @Override
            public void onSuccess(T value) {}

            @Override
            public void onTypedFailure(E e) {
                UncaughtExceptionHandler.logException(e);
            }

            @Override
            public void onRuntimeFailure(RuntimeException e) {
                UncaughtExceptionHandler.logException(e);
            }

            @Override
            public void onCancellation() {}
        };
    }

    /**
     * Protects a given {@code CompletionListener}, by:
     * <ol>
     *     <li>Ensuring that the underlying listener is called at most once.</li>
     *     <li>Trampolining the call, such that stack overflows are avoided.</li>
     * </ol>
     *
     * @param listener is the listener to protect
     * @return a protected version of the given listener
     */
    static <T, E extends Exception> CompletionCallback<T, E> protect(
            final CompletionCallback<? super T, ? super E> listener
    ) {
        return new ProtectedCompletionHandler<>(listener);
    }
}

@NullMarked
final class ProtectedCompletionHandler<T, E extends Exception> implements CompletionCallback<T, E>, Runnable {
    private @Nullable AtomicBoolean isWaiting = new AtomicBoolean(true);
    private @Nullable Outcome<? extends T, ? extends E> outcome;
    private @Nullable CompletionCallback<? super T, ? super E> listener;

    ProtectedCompletionHandler(final CompletionCallback<? super T, ? super E> listener) {
        this.listener = listener;
    }

    @Override
    public void run() {
        final var outcome = this.outcome;
        final var listener = this.listener;
        if (outcome != null && listener != null) {
            listener.onCompletion(outcome);
            // For GC purposes; but it doesn't really matter if we nullify these or not
            this.outcome = null;
            this.listener = null;
        }
    }

    @Override
    public void onSuccess(final T value) {
        this.onCompletion(Outcome.success(value));
    }

    @Override
    public void onTypedFailure(E e) {
        this.onCompletion(Outcome.typedFailure(e));
    }

    @Override
    public void onRuntimeFailure(RuntimeException e) {
        this.onCompletion(Outcome.runtimeFailure(e));
    }

    @Override
    public void onCancellation() {
        this.onCompletion(Outcome.cancellation());
    }

    @Override
    public void onCompletion(Outcome<? extends T, ? extends E> outcome) {
        final var ref = this.isWaiting;
        if (ref != null && ref.getAndSet(false)) {
            this.outcome = outcome;
            // Trampolined execution is needed because, with chained tasks,
            // we might end up with a stack overflow
            Trampoline.execute(this);
            // For GC purposes; but it doesn't really matter if we nullify this or not
            this.isWaiting = null;
        } else if (outcome instanceof final Outcome.RuntimeFailure<? extends T, ? extends E> failed) {
            UncaughtExceptionHandler.logException(failed.exception());
        } else if (outcome instanceof final Outcome.TypedFailure<? extends T, ? extends E> failed) {
            UncaughtExceptionHandler.logException(failed.exception());
        }
    }
}
