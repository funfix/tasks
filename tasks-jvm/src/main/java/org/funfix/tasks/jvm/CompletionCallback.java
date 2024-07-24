package org.funfix.tasks.jvm;

import org.jspecify.annotations.NullMarked;
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
@NullMarked
public interface CompletionCallback<T extends @Nullable Object>
    extends Serializable {

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
    void onCancellation();

    /**
     * @return a {@code CompletionListener} that does nothing.
     */
    static <T extends @Nullable Object> CompletionCallback<T> empty() {
        return new CompletionCallback<>() {
            @Override
            public void onSuccess(final T value) {
            }

            @Override
            public void onCancellation() {
            }

            @Override
            public void onFailure(final Throwable e) {
                UncaughtExceptionHandler.logOrRethrow(e);
            }
        };
    }
}

@NullMarked
final class ProtectedCompletionCallback<T extends @Nullable Object>
    implements CompletionCallback<T>, Runnable {

    private final AtomicBoolean isWaiting = new AtomicBoolean(true);
    private final CompletionCallback<T> listener;
    private final TaskExecutor executor;

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
        if (this.failureCause != null) {
            listener.onFailure(this.failureCause);
        } else if (this.isCancelled) {
            listener.onCancellation();
        } else {
            listener.onSuccess(this.successValue);
        }
        // For GC purposes; but it doesn't really matter if we nullify these or not
        this.successValue = null;
        this.failureCause = null;
        this.isCancelled = false;
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
        UncaughtExceptionHandler.logOrRethrow(e);
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
