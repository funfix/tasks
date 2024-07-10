package org.funfix.tasks;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;

@NullMarked
public final class Task<T, E extends Exception> {
    private final AsyncFun<? extends T, ? extends E> fun;

    private Task(final AsyncFun<? extends T, ? extends E> fun) {
        this.fun = fun;
    }

    /**
     * Executes the task asynchronously.
     *
     * @param callback will be invoked with the result when the task completes
     * @return a {@link Cancellable} that can be used to cancel a running task
     */
    public Cancellable executeAsync(final CompletionCallback<? super T, ? super E> callback) {
        final var protectedCb = CompletionCallback.protect(callback);
        try {
            return fun.invoke(callback);
        } catch (final RuntimeException e) {
            protectedCb.onRuntimeFailure(e);
            return Cancellable.EMPTY;
        }
    }

    /**
     * Executes the task and blocks until it completes, or the current
     * thread gets interrupted (in which case the task is also cancelled).
     *
     * @throws E is the type of the checked exception that the task may fail with
     *
     * @throws InterruptedException if the current thread is interrupted,
     * which also cancels the running task. Note that on interruption,
     * the running concurrent task must also be interrupted, as this method
     * always blocks for its interruption or completion.
     */
    public T executeBlocking() throws E, InterruptedException {
        final var callback = new BlockingCompletionCallback<T, E>();
        Cancellable token;
        try {
            token = this.fun.invoke(callback);
        } catch (final RuntimeException e) {
            callback.onRuntimeFailure(e);
            token = Cancellable.EMPTY;
        }
        return callback.await(token);
    }

    /**
     * Executes the task and blocks until it completes, or the timeout is reached,
     * or the current thread is interrupted.
     *
     * @throws E is the type of the checked exception that the task may fail with
     *
     * @throws InterruptedException if the current thread is interrupted.
     * The running task is also cancelled, and this method does not
     * return until `onCancel` is signaled.
     *
     * @throws TimeoutException if the task doesn't complete within the
     * specified timeout. The running task is also cancelled on timeout,
     * and this method does not returning until `onCancel` is signaled.
     */
    public T executeBlockingTimed(final Duration timeout)
            throws E, InterruptedException, TimeoutException {

        final var callback = new BlockingCompletionCallback<T, E>();
        Cancellable token;
        try {
            token = this.fun.invoke(callback);
        } catch (final RuntimeException e) {
            callback.onRuntimeFailure(e);
            token = Cancellable.EMPTY;
        }
        return callback.awaitTimed(token, timeout);
    }
}

@NullMarked
final class BlockingCompletionCallback<T, E extends Exception>
        extends AbstractQueuedSynchronizer implements CompletionCallback<T, E> {

    private final AtomicBoolean isDone = new AtomicBoolean(false);
    private volatile @Nullable T result = null;
    private volatile @Nullable RuntimeException runtimeError = null;
    private volatile @Nullable E typedError = null;
    private volatile @Nullable InterruptedException interrupted = null;

    @Override
    public void onSuccess(T value) {
        if (!isDone.getAndSet(true)) {
            result = value;
            releaseShared(1);
        }
    }

    @Override
    public void onTypedFailure(E e) {
        if (!isDone.getAndSet(true)) {
            typedError = e;
            releaseShared(1);
        } else {
            UncaughtExceptionHandler.logException(e);
        }
    }

    @Override
    public void onRuntimeFailure(RuntimeException e) {
        if (!isDone.getAndSet(true)) {
            runtimeError = e;
            releaseShared(1);
        } else {
            UncaughtExceptionHandler.logException(e);
        }
    }

    @Override
    public void onCancellation() {
        if (!isDone.getAndSet(true)) {
            interrupted = new InterruptedException("Task was cancelled");
            releaseShared(1);
        }
    }

    @Override
    protected int tryAcquireShared(int arg) {
        return (getState() != 0) ? 1 : -1;
    }

    @Override
    protected boolean tryReleaseShared(int arg) {
        setState(1);
        return true;
    }

    @SuppressWarnings("DataFlowIssue")
    public T awaitTimed(Cancellable cancelToken, @Nullable Duration timeout)
            throws E, InterruptedException, TimeoutException {

        boolean isNotCancelled = true;
        TimeoutException timedOut = null;
        while (true) {
            try {
                if (timeout == null) {
                    acquireSharedInterruptibly(1);
                } else {
                    if (!tryAcquireSharedNanos(1, timeout.toNanos()))
                        throw new TimeoutException("Task timed-out after " + timeout);
                }
                break;
            } catch (TimeoutException | InterruptedException e) {
                if (isNotCancelled) {
                    isNotCancelled = false;
                    if (e instanceof final TimeoutException te) {
                        timedOut = te;
                    }
                    cancelToken.cancel();
                }
            }
            // Clearing the interrupted flag may not be necessary,
            // but doesn't hurt, and we should have a cleared flag before
            // re-throwing the exception
            // noinspection ResultOfMethodCallIgnored
            Thread.interrupted();
        }
        if (timedOut != null) throw timedOut;
        if (interrupted != null) throw Objects.requireNonNull(interrupted);
        if (typedError != null) throw Objects.requireNonNull(typedError);
        if (runtimeError != null) throw Objects.requireNonNull(runtimeError);
        return result;
    }

    public T await(Cancellable cancelToken) throws E, InterruptedException {
        try {
            return awaitTimed(cancelToken, null);
        } catch (TimeoutException e) {
            throw new RuntimeException(e);
        }
    }

    @FunctionalInterface
    private interface AwaitFunction {
        void await(boolean isNotCancelled) throws InterruptedException, TimeoutException;
    }
}
