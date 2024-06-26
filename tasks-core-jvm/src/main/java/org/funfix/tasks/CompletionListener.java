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
 */
@NullMarked
public interface CompletionListener<T> extends Serializable {
    /**
     * Must be called when the task completes successfully.
     *
     * @param value is the successful result of the task, to be signaled
     */
    void onSuccess(T value);

    /**
     * Must be called when the task completes with an exception.
     *
     * @param e is the exception that the task failed with
     */
    void onFailure(Throwable e);

    /**
     * Must be called when the task is cancelled.
     */
    void onCancel();

    /**
     * Signals a final {@link Outcome} to this listener.
     */
    default void onCompletion(final Outcome<T> outcome) {
        if (outcome instanceof final Outcome.Success<T> success) {
            onSuccess(success.value());
        } else if (outcome instanceof final Outcome.Failure<T> failure) {
            onFailure(failure.exception());
        } else {
            onCancel();
        }
    }

    /**
     * @return a {@code CompletionListener} that does nothing.
     */
    static <T> CompletionListener<T> empty() {
        return new CompletionListener<T>() {
            @Override
            public void onSuccess(final T value) { }
            @Override
            public void onFailure(final Throwable e) {}
            @Override
            public void onCancel() {}
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
    static <T> CompletionListener<T> protect(final CompletionListener<T> listener) {
        return new ProtectedCompletionHandler<>(listener);
    }
}

@NullMarked
final class ProtectedCompletionHandler<T> implements CompletionListener<T>, Runnable {
    private @Nullable AtomicBoolean isWaiting = new AtomicBoolean(true);
    private @Nullable Outcome<T> outcome;
    private @Nullable CompletionListener<T> listener;

    ProtectedCompletionHandler(final CompletionListener<T> listener) {
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
        this.onCompletion(new Outcome.Success<>(value));
    }

    @Override
    public void onFailure(final Throwable e) {
        this.onCompletion(new Outcome.Failure<>(e));
    }

    @Override
    public void onCancel() {
        this.onCompletion(Outcome.cancelled());
    }

    @Override
    public void onCompletion(final Outcome<T> outcome) {
        final var ref = this.isWaiting;
        if (ref != null && ref.getAndSet(false)) {
            this.outcome = outcome;
            // Trampolined execution is needed because, with chained tasks,
            // we might end up with a stack overflow
            Trampoline.execute(this);
            // For GC purposes; but it doesn't really matter if we nullify this or not
            this.isWaiting = null;
        }
    }
}
