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
 * <p>
 * MUST BE idempotent AND thread-safe.
 *
 * @param <T> is the type of the value that the task will complete with
 */
@NullMarked
public interface CompletionCallback<T> extends Serializable {
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
        if (outcome instanceof final Outcome.Succeeded<T> succeeded) {
            onSuccess(succeeded.value());
        } else if (outcome instanceof final Outcome.Failed<T> failed) {
            onFailure(failed.exception());
        } else {
            onCancel();
        }
    }

    /**
     * @return a {@code CompletionListener} that does nothing.
     */
    static <T> CompletionCallback<T> empty() {
        return new CompletionCallback<>() {
            @Override
            public void onSuccess(final T value) {
            }

            @Override
            public void onCancel() {
            }

            @Override
            public void onFailure(final Throwable e) {
                UncaughtExceptionHandler.logOrRethrowException(e);
            }
        };
    }
}

@NullMarked
final class ProtectedCompletionCallback<T> implements CompletionCallback<T>, Runnable {
    private @Nullable AtomicBoolean isWaiting = new AtomicBoolean(true);
    private @Nullable Outcome<T> outcome;
    private @Nullable CompletionCallback<T> listener;

    private ProtectedCompletionCallback(final CompletionCallback<T> listener) {
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
        this.onCompletion(new Outcome.Succeeded<>(value));
    }

    @Override
    public void onFailure(final Throwable e) {
        this.onCompletion(new Outcome.Failed<>(e));
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
        } else if (outcome instanceof final Outcome.Failed<T> failed) {
            UncaughtExceptionHandler.logOrRethrowException(failed.exception());
        }
    }

    public static <T> CompletionCallback<T> protect(final CompletionCallback<T> listener) {
        return new ProtectedCompletionCallback<>(listener);
    }
}
