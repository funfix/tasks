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
@FunctionalInterface
public interface CompletionCallback<T> extends Serializable {
    /**
     * Signals a final {@link Outcome} on the completion of a task.
     */
    void onCompletion(final Outcome<T> outcome);

    /**
     * Must be called when the task completes successfully.
     *
     * @param value is the successful result of the task, to be signaled
     */
    default void onSuccess(T value) {
        onCompletion(Outcome.succeeded(value));
    }

    /**
     * Must be called when the task completes with an exception.
     *
     * @param e is the exception that the task failed with
     */
    default void onFailure(Throwable e) {
        onCompletion(Outcome.failed(e));
    }

    /**
     * Must be called when the task is cancelled.
     */
    default void onCancel() {
        onCompletion(Outcome.cancelled());
    }

    /**
     * @return a {@code CompletionListener} that does nothing.
     */
    static <T> CompletionCallback<T> empty() {
        return new CompletionCallback<>() {
            @Override
            public void onSuccess(final T value) {}

            @Override
            public void onCancel() {}

            @Override
            public void onFailure(final Throwable e) {
                UncaughtExceptionHandler.logOrRethrowException(e);
            }

            @Override
            public void onCompletion(Outcome<T> outcome) {
                if (outcome instanceof Outcome.Failed<?> failed) {
                    onFailure(failed.exception());
                }
            }
        };
    }
}

@NullMarked
final class ProtectedCompletionCallback<T> implements CompletionCallback<T>, Runnable {
    private final AtomicBoolean isWaiting = new AtomicBoolean(true);
    private final CompletionCallback<T> listener;

    private @Nullable Outcome<T> outcome;
    private @Nullable T successValue;
    private @Nullable Throwable failureCause;
    private boolean isCancelled = false;

    private ProtectedCompletionCallback(final CompletionCallback<T> listener) {
        this.listener = listener;
    }

    @SuppressWarnings("DataFlowIssue")
    @Override
    public void run() {
        if (this.outcome != null) {
            listener.onCompletion(this.outcome);
        } else if (this.failureCause != null) {
            listener.onFailure(this.failureCause);
        } else if (this.isCancelled) {
            listener.onCancel();
        } else {
            listener.onSuccess(this.successValue);
        }
        // For GC purposes; but it doesn't really matter if we nullify these or not
        this.outcome = null;
        this.successValue = null;
        this.failureCause = null;
        this.isCancelled = false;
    }

    @Override
    public void onSuccess(final T value) {
        if (isWaiting.getAndSet(false)) {
            this.successValue = value;
            // Trampolined execution is needed because, with chained tasks,
            // we might end up with a stack overflow
            Trampoline.execute(this);
        }
    }

    @Override
    public void onFailure(final Throwable e) {
        UncaughtExceptionHandler.logOrRethrowException(e);
        if (isWaiting.getAndSet(false)) {
            this.failureCause = e;
            // Trampolined execution is needed because, with chained tasks,
            // we might end up with a stack overflow
            Trampoline.execute(this);
        } else {
            UncaughtExceptionHandler.logOrRethrowException(e);
        }
    }

    @Override
    public void onCancel() {
        if (isWaiting.getAndSet(false)) {
            this.isCancelled = true;
            // Trampolined execution is needed because, with chained tasks,
            // we might end up with a stack overflow
            Trampoline.execute(this);
        }
    }

    @Override
    public void onCompletion(Outcome<T> outcome) {
        if (isWaiting.getAndSet(false)) {
            this.outcome = outcome;
            // Trampolined execution is needed because, with chained tasks,
            // we might end up with a stack overflow
            Trampoline.execute(this);
        } else if (outcome instanceof Outcome.Failed<?> failed) {
            UncaughtExceptionHandler.logOrRethrowException(failed.exception());
        }
    }

    public static <T> CompletionCallback<T> protect(final CompletionCallback<T> listener) {
        return new ProtectedCompletionCallback<>(listener);
    }
}
