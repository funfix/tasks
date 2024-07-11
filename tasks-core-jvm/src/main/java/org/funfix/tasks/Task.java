package org.funfix.tasks;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;

/**
 * Represents a function that can be executed asynchronously.
 */
@NullMarked
public final class Task<T> {
    private final AsyncFun<T> asyncFun;

    private Task(final AsyncFun<T> asyncFun) {
        this.asyncFun = asyncFun;
    }

    /**
     * Executes the task asynchronously.
     *
     * @param callback will be invoked when the task completes
     * @param executor is the {@link FiberExecutor} that may be used to run the task
     *
     * @return a {@link Cancellable} that can be used to cancel a running task
     */
    public Cancellable executeAsync(
            final CompletionCallback<? super T> callback,
            final FiberExecutor executor
    ) {
        final var cb = CompletionCallback.protect(callback);
        try {
            return asyncFun.invoke(cb, executor);
        } catch (final Throwable e) {
            cb.onFailure(e);
            return Cancellable.EMPTY;
        }
    }

    /**
     * Overload of {@link #executeAsync(CompletionCallback, FiberExecutor)} that
     * uses {@link FiberExecutor#shared()} as the executor.
     *
     * @param callback will be invoked when the task completes
     *
     * @return a {@link Cancellable} that can be used to cancel a running task
     */
    public Cancellable executeAsync(final CompletionCallback<? super T> callback) {
        return executeAsync(callback, FiberExecutor.shared());
    }

    /**
     * Executes the task concurrently and returns a [Fiber] that can be
     * used to wait for the result or cancel the task.
     *
     * @param executor is the {@link FiberExecutor} that may be used to run the task
     *
     * @return a {@link TaskFiber} that can be used to wait for the outcome,
     * or to cancel the running fiber.
     */
    public TaskFiber<T> executeConcurrently(final @Nullable FiberExecutor executor) {
        final var fiber = new TaskFiberDefault<T>();
        try {
            final var token = asyncFun.invoke(
                    fiber.onComplete,
                    executor == null ? FiberExecutor.shared() : executor
            );
            fiber.registerCancel(token);
        } catch (Exception e) {
            fiber.onComplete.onFailure(e);
        }
        return fiber;
    }

    /**
     * Overload of {@link #executeConcurrently(FiberExecutor)} that
     * uses {@link FiberExecutor#shared()} as the executor.
     *
     * @return a {@link TaskFiber} that can be used to wait for the outcome,
     * or to cancel the running fiber.
     */
    public TaskFiber<T> executeConcurrently() {
        return executeConcurrently(null);
    }

    /**
     * Executes the task and blocks until it completes, or the current
     * thread gets interrupted (in which case the task is also cancelled).
     *
     * @param executor is the {@link FiberExecutor} that may be used to run the task
     *
     * @throws ExecutionException if the task fails with an exception
     *
     * @throws InterruptedException if the current thread is interrupted,
     *         which also cancels the running task. Note that on interruption,
     *         the running concurrent task must also be interrupted, as this method
     *         always blocks for its interruption or completion.
     *
     * @return the successful result of the task
     */
    public T executeBlocking(@Nullable FiberExecutor executor)
            throws ExecutionException, InterruptedException {

        final var blockingCallback = new BlockingCompletionCallback<T>();
        final var cancelToken = asyncFun.invoke(
                blockingCallback,
                executor == null ? FiberExecutor.shared() : executor
        );
        return blockingCallback.await(cancelToken);
    }

    /**
     * Overload of {@link #executeBlocking(FiberExecutor)} that uses
     * {@link FiberExecutor#shared()} as the executor.
     */
    public T executeBlocking() throws ExecutionException, InterruptedException {
        return executeBlocking(null);
    }

    /**
     * Executes the task and blocks until it completes, or the timeout is reached,
     * or the current thread is interrupted.
     *
     * @param timeout is the maximum time to wait for the task to complete
     *                before throwing a {@link TimeoutException}
     *
     * @param executor is the {@link FiberExecutor} that may be used to run
     *                 the task
     *
     * @throws ExecutionException if the task fails with an exception
     *
     * @throws InterruptedException if the current thread is interrupted.
     *         The running task is also cancelled, and this method does not
     *         return until `onCancel` is signaled.
     *
     * @throws TimeoutException if the task doesn't complete within the
     *         specified timeout. The running task is also cancelled on timeout,
     *         and this method does not returning until `onCancel` is signaled.
     *
     * @return the successful result of the task
     */
    public T executeBlockingTimed(
            final Duration timeout,
            @Nullable final FiberExecutor executor
    ) throws ExecutionException, InterruptedException, TimeoutException {
        final var blockingCallback = new BlockingCompletionCallback<T>();
        final var cancelToken = asyncFun.invoke(
                blockingCallback,
                executor == null ? FiberExecutor.shared() : executor
        );
        return blockingCallback.await(cancelToken, timeout);
    }

    /**
     * Overload of {@link #executeBlockingTimed(Duration, FiberExecutor)} that
     * uses {@link FiberExecutor#shared()} as the executor.
     *
     * @param timeout is the maximum time to wait for the task to complete
     *
     * @throws ExecutionException if the task fails with an exception
     *
     * @throws InterruptedException if the current thread is interrupted.
     *         The running task is also cancelled, and this method does not
     *         return until `onCancel` is signaled.
     *
     * @throws TimeoutException if the task doesn't complete within the
     *         specified timeout. The running task is also cancelled on timeout,
     *         and this method does not returning until `onCancel` is signaled.
     *
     * @return the successful result of the task
     */
    public T executeBlockingTimed(final Duration timeout)
            throws ExecutionException, InterruptedException, TimeoutException {
        return executeBlockingTimed(timeout, null);
    }

    /**
     * Creates a task from a builder function that, on evaluation, will have
     * same-thread execution.
     * <p>
     * This method ensures:
     * <ol>
     *     <li>Idempotent cancellation</li>
     *     <li>Trampolined execution to avoid stack-overflows</li>
     * </ol>
     * </p><p>
     * The created task will execute the given function on the current thread, by
     * using a "trampoline" to avoid stack overflows. This may be useful if the
     * computation for initiating the async process is expected to be fast. However,
     * if the computation can block the current thread, it is recommended to use
     * {@link #createAsync(AsyncFun)} instead, which will initiate the computation
     * on a separate thread.
     * </p>
     *
     * @param fun is the function that will trigger the async computation,
     *            injecting a callback that will be used to signal the result,
     *            and an executor that can be used for creating additional threads.
     *
     * @return a new task that will execute the given builder function upon execution
     *
     * @see #createAsync(AsyncFun)
     */
    public static <T> Task<T> create(final AsyncFun<T> fun) {
        return new Task<>((callback, executor) -> {
            final var cancel = new MutableCancellable();
            Trampoline.execute(() -> {
                try {
                    cancel.set(fun.invoke(callback, executor));
                } catch (final Throwable e) {
                    callback.onFailure(e);
                }
            });
            return cancel;
        });
    }

    /**
     * Creates a task that, when executed, will execute the given asynchronous
     * computation starting from a separate thread.
     * <p>
     * This is a variant of {@link #create(AsyncFun)}, which executes the given
     * builder function on the same thread.
     * </p><p>
     * NOTE: the thread is created via the injected {@link FiberExecutor} in the
     * "execute" methods (e.g., {@link #executeAsync(CompletionCallback, FiberExecutor)}).
     * Even when using on of the overloads, then {@link FiberExecutor#shared()}
     * is assumed.
     * </p>
     *
     * @param fun is the function that will trigger the async computation
     * @return a new task that will execute the given builder function
     *
     * @see #create(AsyncFun)
     * @see FiberExecutor
     */
    public static <T> Task<T> createAsync(final AsyncFun<T> fun) {
        return new Task<>((callback, executor) -> {
            final var cancel = new MutableCancellable();
            // Starting the execution on another thread, to ensure concurrent
            // execution; NOTE: this execution is not cancellable, to simplify
            // the model (i.e., we are not using `executeCancellable` on purpose)
            executor.execute(() -> {
                try {
                    final var token = fun.invoke(callback, executor);
                    cancel.set(token);
                } catch (final Throwable e) {
                    callback.onFailure(e);
                }
            });
            return cancel;
        });
    }

    /**
     * Creates a task from a {@link Callable} executing blocking IO.
     */
    public static <T> Task<T> fromBlockingIO(final Callable<T> callable) {
        return new Task<>((callback, executor) -> executor.executeCancellable(
                () -> {
                    try {
                        final var result = callable.call();
                        callback.onSuccess(result);
                    } catch (final InterruptedException | CancellationException e) {
                        callback.onCancel();
                    } catch (final Exception e) {
                        callback.onFailure(e);
                    }
                },
                () -> {
                    // This is a concurrent call that wouldn't be very safe
                    // with a naive implementation of the CompletionCallback
                    // (thankfully we protect the injected callback)
                    // noinspection Convert2MethodRef
                    callback.onCancel();
                }));
    }
}

@NullMarked
final class BlockingCompletionCallback<T> extends AbstractQueuedSynchronizer implements CompletionCallback<T> {
    private final AtomicBoolean isDone = new AtomicBoolean(false);
    @Nullable
    private T result = null;
    @Nullable
    private Throwable error = null;
    @Nullable
    private InterruptedException interrupted = null;

    @Override
    public void onSuccess(final T value) {
        if (!isDone.getAndSet(true)) {
            result = value;
            releaseShared(1);
        }
    }

    @Override
    public void onFailure(final Throwable e) {
        if (!isDone.getAndSet(true)) {
            error = e;
            releaseShared(1);
        }
    }

    @Override
    public void onCancel() {
        if (!isDone.getAndSet(true)) {
            interrupted = new InterruptedException("Task was cancelled");
            releaseShared(1);
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
        void apply(boolean isNotCancelled) throws InterruptedException, TimeoutException;
    }

    private T awaitInline(final Cancellable cancelToken, final AwaitFunction await)
            throws InterruptedException, ExecutionException, TimeoutException {

        var isNotCancelled = true;
        TimeoutException timedOut = null;
        while (true) {
            try {
                await.apply(isNotCancelled);
                break;
            } catch (final TimeoutException e) {
                if (isNotCancelled) {
                    isNotCancelled = false;
                    timedOut = e;
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
        // noinspection DataFlowIssue
        return result;
    }

    public T await(final Cancellable cancelToken) throws InterruptedException, ExecutionException {
        try {
            return awaitInline(cancelToken, firstAwait -> acquireSharedInterruptibly(1));
        } catch (final TimeoutException e) {
            throw new IllegalStateException("Unexpected timeout", e);
        }
    }

    public T await(final Cancellable cancelToken, final Duration timeout)
            throws ExecutionException, InterruptedException, TimeoutException {

        return awaitInline(cancelToken, isNotCancelled -> {
            if (isNotCancelled) {
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
}

