package org.funfix.tasks.jvm;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.io.Serializable;
import java.util.Objects;
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
@FunctionalInterface
public interface CompletionCallback<T extends @Nullable Object>
    extends Serializable {

    void onOutcome(Outcome<T> outcome);

    /**
     * Must be called when the task completes successfully.
     *
     * @param value is the successful result of the task, to be signaled
     */
    default void onSuccess(T value) {
        onOutcome(Outcome.success(value));
    }

    /**
     * Must be called when the task completes with an exception.
     *
     * @param e is the exception that the task failed with
     */
    default void onFailure(Throwable e) {
        onOutcome(Outcome.failure(e));
    }

    /**
     * Must be called when the task is cancelled.
     */
    default void onCancellation() {
        onOutcome(Outcome.cancellation());
    }

    /**
     * Returns a {@code CompletionListener} that does nothing (a no-op).
     */
    static <T extends @Nullable Object> CompletionCallback<T> empty() {
        return outcome -> {
            if (outcome instanceof Outcome.Failure<T> f) {
                UncaughtExceptionHandler.logOrRethrow(f.exception());
            }
        };
    }

    /**
     * Composes multiple {@code CompletionCallback} instances into a single
     * {@code CompletionCallback} that will invoke all the listeners in order.
     */
    @SafeVarargs
    static <T extends @Nullable Object> CompletionCallback<T> compose(
        final CompletionCallback<T>... listeners
    ) {
        Objects.requireNonNull(listeners, "listeners");
        if (listeners.length == 0) {
            return empty();
        } else if (listeners.length == 1) {
            return listeners[0];
        } else {
            return new ManyCompletionCallback<>(listeners);
        }
    }
}

@ApiStatus.Internal
final class ManyCompletionCallback<T extends @Nullable Object>
    implements CompletionCallback<T> {

    private final CompletionCallback<T>[] listeners;

    @SafeVarargs
    ManyCompletionCallback(final CompletionCallback<T>... listeners) {
        Objects.requireNonNull(listeners, "listeners");
        this.listeners = listeners;
    }

    @Override
    public void onOutcome(Outcome<T> outcome) {
        Trampoline.execute(() -> {
            for (final CompletionCallback<T> listener : listeners) {
                try {
                    listener.onOutcome(outcome);
                } catch (Throwable e) {
                    UncaughtExceptionHandler.logOrRethrow(e);
                }
            }
        });
    }

    @Override
    public void onSuccess(T value) {
        Trampoline.execute(() -> {
            for (final CompletionCallback<T> listener : listeners) {
                try {
                    listener.onSuccess(value);
                } catch (Throwable e) {
                    UncaughtExceptionHandler.logOrRethrow(e);
                }
            }
        });
    }

    @Override
    public void onFailure(Throwable e) {
        Objects.requireNonNull(e, "e");
        Trampoline.execute(() -> {
            for (final CompletionCallback<T> listener : listeners) {
                try {
                    listener.onFailure(e);
                } catch (Throwable ex) {
                    UncaughtExceptionHandler.logOrRethrow(ex);
                }
            }
        });
    }

    @Override
    public void onCancellation() {
        Trampoline.execute(() -> {
            for (final CompletionCallback<T> listener : listeners) {
                try {
                    listener.onCancellation();
                } catch (Throwable e) {
                    UncaughtExceptionHandler.logOrRethrow(e);
                }
            }
        });
    }
}

@ApiStatus.Internal
final class ProtectedCompletionCallback<T extends @Nullable Object>
    implements CompletionCallback<T>, Runnable {

    private final AtomicBoolean isWaiting = new AtomicBoolean(true);
    private final CompletionCallback<T> listener;
    private final TaskExecutor executor;

    private @Nullable Outcome<T> outcome;
    private @Nullable T successValue;
    private @Nullable Throwable failureCause;
    private boolean isCancelled = false;

    private ProtectedCompletionCallback(
        final CompletionCallback<T> listener,
        final TaskExecutor executor
    ) {
        this.listener = listener;
        this.executor = executor;
    }

    @Override
    public void run() {
        if (this.outcome != null) {
            listener.onOutcome(this.outcome);
        } else if (this.failureCause != null) {
            listener.onFailure(this.failureCause);
        } else if (this.isCancelled) {
            listener.onCancellation();
        } else if (this.successValue != null) {
            listener.onSuccess(this.successValue);
        } else {
            throw new IllegalStateException("No outcome, success value, failure cause, or cancellation state set");
        }
        // For GC purposes; but it doesn't really matter if we nullify these or not
        this.outcome = null;
        this.successValue = null;
        this.failureCause = null;
        this.isCancelled = false;
    }

    @Override
    public void onOutcome(final Outcome<T> outcome) {
        Objects.requireNonNull(outcome, "outcome");
        if (isWaiting.getAndSet(false)) {
            this.outcome = outcome;
            executor.resumeOnExecutor(this);
        } else if (outcome instanceof Outcome.Failure<T> f) {
            UncaughtExceptionHandler.logOrRethrow(f.exception());
        }
    }

    @Override
    public void onSuccess(final T value) {
        if (isWaiting.getAndSet(false)) {
            this.successValue = value;
            executor.resumeOnExecutor(this);
        }
    }

    @Override
    public void onFailure(final Throwable e) {
        Objects.requireNonNull(e, "e");
        if (isWaiting.getAndSet(false)) {
            this.failureCause = e;
            executor.resumeOnExecutor(this);
        } else {
            UncaughtExceptionHandler.logOrRethrow(e);
        }
    }

    @Override
    public void onCancellation() {
        if (isWaiting.getAndSet(false)) {
            this.isCancelled = true;
            executor.resumeOnExecutor(this);
        }
    }

    public static <T> CompletionCallback<T> protect(
        final TaskExecutor executor,
        final CompletionCallback<T> listener
    ) {
        Objects.requireNonNull(listener, "listener");
        return new ProtectedCompletionCallback<>(
            listener,
            executor
        );
    }
}
