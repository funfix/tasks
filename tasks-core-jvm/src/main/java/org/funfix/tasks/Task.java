package org.funfix.tasks;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.concurrent.*;
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
        final var cb = ProtectedCompletionCallback.protect(callback);
        try {
            return asyncFun.invoke(executor, cb);
        } catch (final Throwable e) {
            UncaughtExceptionHandler.rethrowIfFatal(e);
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
                    executor == null ? FiberExecutor.shared() : executor, fiber.onComplete
            );
            fiber.registerCancel(token);
        } catch (final Throwable e) {
            UncaughtExceptionHandler.rethrowIfFatal(e);
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
    public T executeBlocking(@Nullable final FiberExecutor executor)
            throws ExecutionException, InterruptedException {

        final var blockingCallback = new BlockingCompletionCallback<T>();
        final var cancelToken = asyncFun.invoke(
                executor == null ? FiberExecutor.shared() : executor, blockingCallback
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
                executor == null ? FiberExecutor.shared() : executor, blockingCallback
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
     * <p>
     * The created task will execute the given function on the current thread, by
     * using a "trampoline" to avoid stack overflows. This may be useful if the
     * computation for initiating the async process is expected to be fast. However,
     * if the computation can block the current thread, it is recommended to use
     * {@link #createAsync(AsyncFun)} instead, which will initiate the computation
     * on a separate thread.
     *
     * @param fun is the function that will trigger the async computation,
     *            injecting a callback that will be used to signal the result,
     *            and an executor that can be used for creating additional threads.
     *
     * @return a new task that will execute the given builder function upon execution
     *
     * @see #createAsync(AsyncFun)
     */
    public static <T> Task<T> create(final AsyncFun<? extends T> fun) {
        return new Task<>((executor, callback) -> {
            final var cancel = new MutableCancellable();
            Trampoline.execute(() -> {
                try {
                    cancel.set(fun.invoke(executor, callback));
                } catch (final Throwable e) {
                    UncaughtExceptionHandler.rethrowIfFatal(e);
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
     * <p>
     * NOTE: the thread is created via the injected {@link FiberExecutor} in the
     * "execute" methods (e.g., {@link #executeAsync(CompletionCallback, FiberExecutor)}).
     * Even when using on of the overloads, then {@link FiberExecutor#shared()}
     * is assumed.
     *
     * @param fun is the function that will trigger the async computation
     * @return a new task that will execute the given builder function
     *
     * @see #create(AsyncFun)
     * @see FiberExecutor
     */
    public static <T> Task<T> createAsync(final AsyncFun<? extends T> fun) {
        return new Task<>((executor, callback) -> {
            final var cancel = new MutableCancellable();
            // Starting the execution on another thread, to ensure concurrent
            // execution; NOTE: this execution is not cancellable, to simplify
            // the model (i.e., we are not using `executeCancellable` on purpose)
            executor.execute(() -> {
                try {
                    final var token = fun.invoke(executor, callback);
                    cancel.set(token);
                } catch (final Throwable e) {
                    UncaughtExceptionHandler.rethrowIfFatal(e);
                    callback.onFailure(e);
                }
            });
            return cancel;
        });
    }

    /**
     * Creates a task from a {@link Callable} executing blocking IO.
     */
    public static <T> Task<T> fromBlockingIO(final Callable<? extends T> callable) {
        return new Task<>((executor, callback) -> executor.executeCancellable(
            () -> {
                try {
                    final var result = callable.call();
                    callback.onSuccess(result);
                } catch (final InterruptedException | CancellationException e) {
                    callback.onCancel();
                } catch (final Throwable e) {
                    UncaughtExceptionHandler.rethrowIfFatal(e);
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

    /**
     * Creates a task from a {@link Future} builder.
     * <p>
     * This is compatible with Java's interruption protocol and
     * {@link Future#cancel(boolean)}, with the resulting task being cancellable.
     * <p>
     * <strong>NOTE:</strong> Use {@link #fromCompletionStage(Callable)} for directly
     * converting {@link CompletableFuture} builders, because it is not possible to cancel
     * such values, and the logic needs to reflect it. Better yet, use
     * {@link #fromCancellableFuture(Callable)} for working with {@link CompletionStage}
     * values that can be cancelled.
     *
     * @param builder is the {@link Callable} that will create the {@link Future} upon
     *                this task's execution.
     * @return a new task that will complete with the result of the created {@code Future}
     *        upon execution
     *
     * @see #fromCompletionStage(Callable)
     * @see #fromCancellableFuture(Callable)
     */
    public static <T> Task<T> fromBlockingFuture(final Callable<Future<? extends T>> builder) {
        return fromBlockingIO(() -> {
            final var f = builder.call();
            try {
                return f.get();
            } catch (final ExecutionException e) {
                if (e.getCause() instanceof final RuntimeException re) throw re;
                throw e;
            } catch (final InterruptedException e) {
                f.cancel(true);
                // We need to wait for this future to complete, as we need to
                // try to back-pressure on its interruption (doesn't really work).
                while (!f.isDone()) {
                    // Ignore further interruption signals
                    //noinspection ResultOfMethodCallIgnored
                    Thread.interrupted();
                    try { f.get(); }
                    catch (final Exception ignored) {}
                }
                throw e;
            }
        });
    }

    /**
     * Creates tasks from a builder of {@link CompletionStage}.
     * <p>
     * <strong>NOTE:</strong> {@code CompletionStage} isn't cancellable, and the
     * resulting task should reflect this (i.e., on cancellation, the listener should not
     * receive an `onCancel` signal until the `CompletionStage` actually completes).
     * <p>
     * Prefer using {@link #fromCancellableFuture(Callable)} for working with
     * {@link CompletionStage} values that can be cancelled.
     *
     * @see #fromCancellableFuture(Callable)
     *
     * @param builder is the {@link Callable} that will create the {@link CompletionStage}
     *                value. It's a builder because {@link Task} values are cold values
     *                (lazy, not executed yet).
     *
     * @return a new task that upon execution will complete with the result of
     * the created {@code CancellableCompletionStage}
     */
    public static <T> Task<T> fromCompletionStage(final Callable<CompletionStage<? extends T>> builder) {
        return fromCancellableFuture(
            () -> new CancellableFuture<T>(
                builder.call().toCompletableFuture(), 
                Cancellable.EMPTY
            )
        );
    }

    /**
     * Creates tasks from a builder of {@link CancellableFuture}.
     * <p>
     * This is the recommended way to work with {@link CompletionStage} builders,
     * because cancelling such values (e.g., {@link CompletableFuture}) doesn't work
     * for cancelling the connecting computation. As such, the user should provide
     * an explicit {@link Cancellable} token that can be used.
     *
     * @param builder is the {@link Callable} that will create the {@link CancellableFuture}
     *                value. It's a builder because {@link Task} values are cold values
     *                (lazy, not executed yet).
     *
     * @return a new task that upon execution will complete with the result of
     * the created {@code CancellableCompletionStage}
     */
    public static <T> Task<T> fromCancellableFuture(final Callable<CancellableFuture<? extends T>> builder) {
        return new Task<>(new TaskFromCancellableFuture<>(builder));
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
        UncaughtExceptionHandler.rethrowIfFatal(e);
        if (!isDone.getAndSet(true)) {
            error = e;
            releaseShared(1);
        } else {
            UncaughtExceptionHandler.logOrRethrowException(e);
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
        void apply(boolean isCancelled) throws InterruptedException, TimeoutException;
    }

    private T awaitInline(final Cancellable cancelToken, final AwaitFunction await)
            throws InterruptedException, ExecutionException, TimeoutException {

        var isCancelled = false;
        TimeoutException timedOut = null;
        while (true) {
            try {
                await.apply(isCancelled);
                break;
            } catch (final TimeoutException | InterruptedException e) {
                if (!isCancelled) {
                    isCancelled = true;
                    if (e instanceof final TimeoutException te) timedOut = te;
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
}

@NullMarked
final class TaskFromCancellableFuture<T> implements AsyncFun<T> {
    private final Callable<? extends CancellableFuture<? extends T>> builder;

    public TaskFromCancellableFuture(final Callable<? extends CancellableFuture<? extends T>> builder) {
        this.builder = builder;
    }

    @Override
    public Cancellable invoke(
            final FiberExecutor executor, final CompletionCallback<? super T> callback
    ) {
        final var cancel = new MutableCancellable();
        executor.execute(() -> {
            var isUserError = true;
            try {
                final var cancellableFuture = builder.call();
                isUserError = false;
                final var future = getCompletableFuture(cancellableFuture, callback);
                final var cancellable = cancellableFuture.cancellable();
                cancel.set(() -> {
                    try { cancellable.cancel(); }
                    finally { future.cancel(true); }
                });
            } catch (final Throwable e) {
                UncaughtExceptionHandler.rethrowIfFatal(e);
                if (isUserError) callback.onFailure(e);
                else UncaughtExceptionHandler.logOrRethrowException(e);
            }
        });
        return cancel;
    }

    private static <T> CompletableFuture<? extends T> getCompletableFuture(
        final CancellableFuture<? extends T> cancellableFuture,
        final CompletionCallback<? super T> callback
    ) {
        final var future = cancellableFuture.future();
        future.whenComplete((value, error) -> {
            if (error instanceof InterruptedException || error instanceof CancellationException) {
                callback.onCancel();
            } else if (error instanceof final ExecutionException e && e.getCause() != null) {
                callback.onFailure(e.getCause());
            } else if (error instanceof final CompletionException e && e.getCause() != null) {
                callback.onFailure(error.getCause());
            } else if (error != null) {
                callback.onFailure(error);
            } else {
                callback.onSuccess(value);
            }
        });
        return future;
    }
}
