package org.funfix.tasks.jvm;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.io.Serializable;
import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;

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

    ManyCompletionCallback<T> withExtraListener(CompletionCallback<T> extraListener) {
        Objects.requireNonNull(extraListener, "extraListener");
        final var newListeners = Arrays.copyOf(listeners, listeners.length + 1);
        newListeners[newListeners.length - 1] = extraListener;
        return new ManyCompletionCallback<>(newListeners);
    }

    @Override
    public void onOutcome(Outcome<T> outcome) {
        for (final CompletionCallback<T> listener : listeners) {
            try {
                listener.onOutcome(outcome);
            } catch (Throwable e) {
                UncaughtExceptionHandler.logOrRethrow(e);
            }
        }
    }

    @Override
    public void onSuccess(T value) {
        for (final CompletionCallback<T> listener : listeners) {
            try {
                listener.onSuccess(value);
            } catch (Throwable e) {
                UncaughtExceptionHandler.logOrRethrow(e);
            }
        }
    }

    @Override
    public void onFailure(Throwable e) {
        Objects.requireNonNull(e, "e");
        for (final CompletionCallback<T> listener : listeners) {
            try {
                listener.onFailure(e);
            } catch (Throwable ex) {
                UncaughtExceptionHandler.logOrRethrow(ex);
            }
        }
    }

    @Override
    public void onCancellation() {
        for (final CompletionCallback<T> listener : listeners) {
            try {
                listener.onCancellation();
            } catch (Throwable e) {
                UncaughtExceptionHandler.logOrRethrow(e);
            }
        }
    }
}

@ApiStatus.Internal
interface ContinuationCallback<T extends @Nullable Object>
    extends CompletionCallback<T>, Serializable {

    /**
     * Registers an extra callback to be invoked when the task completes.
     * This is useful for chaining callbacks or adding additional listeners.
     */
    void registerExtraCallback(CompletionCallback<T> extraCallback);
}

@ApiStatus.Internal
final class AsyncContinuationCallback<T extends @Nullable Object>
    implements ContinuationCallback<T>, Runnable {

    private final AtomicBoolean isWaiting = new AtomicBoolean(true);
    private final AtomicReference<CompletionCallback<T>> listenerRef;
    private final TaskExecutor executor;

    private @Nullable Outcome<T> outcome;
    private @Nullable T successValue;
    private @Nullable Throwable failureCause;
    private boolean isCancelled = false;

    public AsyncContinuationCallback(
        final CompletionCallback<T> listener,
        final TaskExecutor executor
    ) {
        this.executor = executor;
        this.listenerRef = new AtomicReference<>(
            Objects.requireNonNull(listener, "listener")
        );
    }

    @Override
    @SuppressWarnings("NullAway")
    public void run() {
        if (this.outcome != null) {
            listenerRef.get().onOutcome(this.outcome);
        } else if (this.failureCause != null) {
            listenerRef.get().onFailure(this.failureCause);
        } else if (this.isCancelled) {
            listenerRef.get().onCancellation();
        } else if (this.successValue != null) {
            listenerRef.get().onSuccess(this.successValue);
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

    public static <T> ContinuationCallback<T> protect(
        final TaskExecutor executor,
        final CompletionCallback<T> listener
    ) {
        Objects.requireNonNull(listener, "listener");
        return new AsyncContinuationCallback<>(
            listener,
            executor
        );
    }

    @SuppressWarnings("NullAway")
    public void registerExtraCallback(CompletionCallback<T> extraCallback) {
        while (true) {
            final var current = listenerRef.get();
            if (current instanceof ManyCompletionCallback<T> many) {
                final var update = many.withExtraListener(extraCallback);
                if (listenerRef.compareAndSet(current, update)) {
                    return;
                }
            } else if (listenerRef.compareAndSet(current, new ManyCompletionCallback<>(current, extraCallback))) {
                return;
            }
        }
    }
}

/**
 * INTERNAL API.
 * <p>
 * <strong>INTERNAL API:</strong> Internal apis are subject to change or removal
 * without any notice. When code depends on internal APIs, it is subject to
 * breakage between minor version updates.
 */
@ApiStatus.Internal
final class BlockingCompletionCallback<T extends @Nullable Object>
    extends AbstractQueuedSynchronizer implements ContinuationCallback<T> {

    private final AtomicBoolean isDone =
        new AtomicBoolean(false);
    private final AtomicReference<@Nullable CompletionCallback<T>> extraCallbackRef =
        new AtomicReference<>(null);

    @Nullable
    private T result = null;
    @Nullable
    private Throwable error = null;
    @Nullable
    private InterruptedException interrupted = null;

    @SuppressWarnings("NullAway")
    private void notifyOutcome() {
        final var extraCallback = extraCallbackRef.getAndSet(null);
        if (extraCallback != null)
            try {
                if (error != null)
                    extraCallback.onFailure(error);
                else if (interrupted != null)
                    extraCallback.onCancellation();
                else
                    extraCallback.onSuccess(result);
            } catch (Throwable e) {
                UncaughtExceptionHandler.logOrRethrow(e);
            }
        releaseShared(1);
    }

    @Override
    public void onSuccess(final T value) {
        if (!isDone.getAndSet(true)) {
            result = value;
            notifyOutcome();
        }
    }

    @Override
    public void onFailure(final Throwable e) {
        UncaughtExceptionHandler.rethrowIfFatal(e);
        if (!isDone.getAndSet(true)) {
            error = e;
            notifyOutcome();
        } else {
            UncaughtExceptionHandler.logOrRethrow(e);
        }
    }

    @Override
    public void onCancellation() {
        if (!isDone.getAndSet(true)) {
            interrupted = new InterruptedException("Task was cancelled");
            notifyOutcome();
        }
    }

    @Override
    public void onOutcome(Outcome<T> outcome) {
        if (outcome instanceof Outcome.Success<T> success) {
            onSuccess(success.value());
        } else if (outcome instanceof Outcome.Failure<T> failure) {
            onFailure(failure.exception());
        } else {
            onCancellation();
        }
    }

    @Override
    protected int tryAcquireShared(final int arg) {
        return getState() != 0 ? 1 : -1;
    }

    @Override
    protected boolean tryReleaseShared(final int arg) {
        setState(1);
        return true;
    }

    @FunctionalInterface
    interface AwaitFunction {
        void apply(boolean isCancelled) throws InterruptedException, TimeoutException;
    }

    @SuppressWarnings("NullAway")
    private T awaitInline(final Cancellable cancelToken, final AwaitFunction await)
        throws InterruptedException, ExecutionException, TimeoutException {

        TaskLocalContext.signalTheStartOfBlockingCall();
        var isCancelled = false;
        TimeoutException timedOut = null;
        while (true) {
            try {
                await.apply(isCancelled);
                break;
            } catch (final TimeoutException | InterruptedException e) {
                if (!isCancelled) {
                    isCancelled = true;
                    if (e instanceof TimeoutException te)
                        timedOut = te;
                    cancelToken.cancel();
                }
            }
            // Clearing the interrupted flag may not be necessary,
            // but doesn't hurt, and we should have a cleared flag before
            // re-throwing the exception
            //
            // noinspection ResultOfMethodCallIgnored
            Thread.interrupted();
        }
        if (timedOut != null) throw timedOut;
        if (interrupted != null) throw interrupted;
        if (error != null) throw new ExecutionException(error);
        return result;
    }

    public T await(final Cancellable cancelToken) throws InterruptedException, ExecutionException {
        try {
            return awaitInline(cancelToken, isCancelled -> acquireSharedInterruptibly(1));
        } catch (final TimeoutException e) {
            throw new IllegalStateException("Unexpected timeout", e);
        }
    }

    public T await(final Cancellable cancelToken, final Duration timeout)
        throws ExecutionException, InterruptedException, TimeoutException {

        return awaitInline(cancelToken, isCancelled -> {
            if (!isCancelled) {
                if (!tryAcquireSharedNanos(1, timeout.toNanos())) {
                    throw new TimeoutException("Task timed-out after " + timeout);
                }
            } else {
                // Waiting without a timeout, since at this point it's waiting
                // on the cancelled task to finish
                acquireSharedInterruptibly(1);
            }
        });
    }

    @Override
    public void registerExtraCallback(CompletionCallback<T> extraCallback) {
        while (true) {
            final var current = extraCallbackRef.get();
            if (current == null) {
                if (extraCallbackRef.compareAndSet(null, extraCallback)) {
                    return;
                }
            } else if (current instanceof ManyCompletionCallback<T> many) {
                final var update = many.withExtraListener(extraCallback);
                if (extraCallbackRef.compareAndSet(current, update)) {
                    return;
                }
            } else if (extraCallbackRef.compareAndSet(current, new ManyCompletionCallback<>(current, extraCallback))) {
                return;
            }
        }
    }
}
