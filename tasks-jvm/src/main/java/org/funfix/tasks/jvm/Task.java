package org.funfix.tasks.jvm;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonBlocking;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;

/**
 * Represents a function that can be executed asynchronously.
 */
@NullMarked
public final class Task<T extends @Nullable Object> {
    private final AsyncContinuationFun<T> createFun;

    private Task(final AsyncContinuationFun<T> createFun) {
        this.createFun = createFun;
    }

    /**
     * Ensures that the task starts asynchronously and runs on the given executor,
     * regardless of the `run` method that is used, or the injected executor in
     * any of those methods.
     * <p>
     * One example where this is useful is for blocking I/O operations, for
     * ensuring that the task runs on the thread-pool meant for blocking I/O,
     * regardless of what executor is passed to {@code runAsync}.
     * <pre>{@code
     * Task.fromBlockingIO(() -> {
     *     // Reads a file from disk
     *     return Files.readString(Paths.get("file.txt"));
     * }).ensureRunningOnExecutor(
     *     TaskExecutors.sharedBlockingIO()
     * );
     * }</pre>
     */
    public Task<T> ensureRunningOnExecutor(final Executor executor) {
        return new Task<>((cont) -> {
            final var taskExecutor = TaskExecutor.from(executor);
            final var cont2 = cont.withExecutorOverride(taskExecutor);
            taskExecutor.resumeOnExecutor(() -> createFun.invoke(cont2));
        });
    }

    /**
     * Ensure that the task will start execution on the injected {@link Executor}.
     * <p>
     * The execution model is set by the "run" methods, e.g.,
     * {@link #runAsync(Executor, CompletionCallback)} will create a new thread,
     * but {@link #runBlocking(Executor)} will not, trying to execute the task
     * on the current thread. Thus, it may be useful to instruct the task to
     * always run on a thread managed by the given executor.
     *
     * <pre>{@code
     * Task.fromBlockingIO(() -> {
     *   // Reads a file from disk
     *   return Files.readString(Paths.get("file.txt"));
     * }).ensureRunningOnExecutor().runBlocking(
     *   TaskExecutors.sharedBlockingIO()
     * );
     * }</pre>
     *
     * In the above example we are reading a file from disk. Normally, the
     * execution via {@code runBlocking} would start this task on the current
     *
     */
    public Task<T> ensureRunningOnExecutor() {
        return new Task<>((cont) ->
            cont.getExecutor().resumeOnExecutor(() -> createFun.invoke(cont))
        );
    }

    /**
     * Executes the task asynchronously.
     * <p>
     * This method ensures that the start starts execution on a different thread,
     * managed by the given executor.
     *
     * @param callback will be invoked when the task completes
     * @param executor is the {@link Executor} that may be used to run the task
     * @return a {@link Cancellable} that can be used to cancel a running task
     */
    public Cancellable runAsync(
        final Executor executor,
        final CompletionCallback<? super T> callback
    ) {
        final var taskExecutor = TaskExecutor.from(executor);
        final var cont = new CancellableContinuation<>(
            taskExecutor,
            ProtectedCompletionCallback.protect(taskExecutor, callback)
        );
        taskExecutor.execute(() -> {
            try {
                createFun.invoke(cont);
            } catch (final Throwable e) {
                UncaughtExceptionHandler.rethrowIfFatal(e);
                cont.onFailure(e);
            }
        });
        return cont;
    }

    /**
     * Overload of {@link #runAsync(Executor, CompletionCallback)} that
     * uses {@link TaskExecutors#sharedBlockingIO()} as the executor.
     * <p>
     * Starts asynchronous execution, on a different thread.
     *
     * @param callback will be invoked when the task completes
     * @return a {@link Cancellable} that can be used to cancel a running task
     */
    public Cancellable runAsync(final CompletionCallback<? super T> callback) {
        return runAsync(TaskExecutors.sharedBlockingIO(), callback);
    }

    /**
     * Executes the task concurrently and returns a [Fiber] that can be
     * used to wait for the result or cancel the task.
     * <p>
     * Similar to {@link #runAsync(Executor, CompletionCallback)}, this method
     * starts the execution on a different thread.
     *
     * @param executor is the {@link Fiber} that may be used to run the task
     * @return a {@link Fiber} that can be used to wait for the outcome,
     * or to cancel the running fiber.
     */
    @NonBlocking
    public Fiber<T> runFiber(final Executor executor) {
        return ExecutedFiber.start(executor, createFun);
    }

    /**
     * Overload of {@link #runFiber(Executor)} that
     * uses {@link TaskExecutors#sharedBlockingIO()} as the executor.
     * <p>
     * Similar to {@link #runAsync(CompletionCallback)}, this method
     * starts the execution on a different thread.
     *
     * @return a {@link Fiber} that can be used to wait for the outcome,
     * or to cancel the running fiber.
     */
    @NonBlocking
    public Fiber<T> runFiber() {
        return runFiber(TaskExecutors.sharedBlockingIO());
    }

    /**
     * Executes the task and blocks until it completes, or the current
     * thread gets interrupted (in which case the task is also cancelled).
     * <p>
     * Given that the intention is to block the current thread for the result,
     * the task starts execution on the current thread.
     *
     * @param executor is the {@link Executor} that may be used to run the task
     * @return the successful result of the task
     *
     * @throws ExecutionException if the task fails with an exception
     *
     * @throws InterruptedException if the current thread is interrupted, which
     * also cancels the running task. Note that on interruption, the running
     * concurrent task must also be interrupted, as this method always blocks
     * for its interruption or completion.
     */
    public T runBlocking(final Executor executor)
        throws ExecutionException, InterruptedException {

        final var blockingCallback = new BlockingCompletionCallback<T>();
        final var taskExecutor = TaskExecutor.from(executor);
        final var cont = new CancellableContinuation<>(taskExecutor, blockingCallback);
        createFun.invoke(cont);
        return blockingCallback.await(cont);
    }

    /**
     * Overload of {@link #runBlocking(Executor)} that uses
     * {@link TaskExecutors#sharedBlockingIO()} as the executor.
     * <p>
     * Given that the intention is to block the current thread for the result,
     * the task starts execution on the current thread.
     *
     * @return the successful result of the task
     *
     * @throws ExecutionException if the task fails with an exception
     *
     * @throws InterruptedException if the current thread is interrupted, which
     * also cancels the running task. Note that on interruption, the running
     * concurrent task must also be interrupted, as this method always blocks
     * for its interruption or completion.
     */
    public T runBlocking() throws ExecutionException, InterruptedException {
        return runBlocking(TaskExecutors.sharedBlockingIO());
    }

    /**
     * Executes the task and blocks until it completes, or the timeout is reached,
     * or the current thread is interrupted.
     * <p>
     * <strong>EXECUTION MODEL:</strong> Execution starts on a different thread,
     * by necessity, otherwise the execution could block the current thread
     * indefinitely, without the possibility of interrupting the task after
     * the timeout occurs.
     *
     * @param timeout  is the maximum time to wait for the task to complete
     *                 before throwing a {@link TimeoutException}
     * @param executor is the {@link Executor} that may be used to run
     *                 the task
     * @return the successful result of the task
     * @throws ExecutionException   if the task fails with an exception
     * @throws InterruptedException if the current thread is interrupted.
     *                              The running task is also cancelled, and this method does not
     *                              return until `onCancel` is signaled.
     * @throws TimeoutException     if the task doesn't complete within the
     *                              specified timeout. The running task is also cancelled on timeout,
     *                              and this method does not returning until `onCancel` is signaled.
     */
    public T runBlockingTimed(
        final Executor executor,
        final Duration timeout
    ) throws ExecutionException, InterruptedException, TimeoutException {
        final var blockingCallback = new BlockingCompletionCallback<T>();
        final var taskExecutor = TaskExecutor.from(executor);
        final var cont = new CancellableContinuation<>(taskExecutor, blockingCallback);
        taskExecutor.execute(() -> createFun.invoke(cont));
        return blockingCallback.await(cont, timeout);
    }

    /**
     * Overload of {@link #runBlockingTimed(Executor, Duration)} that
     * uses {@link TaskExecutors#sharedBlockingIO()} as the executor.
     * <p>
     * <strong>EXECUTION MODEL:</strong> Execution starts on a different thread,
     * by necessity, otherwise the execution could block the current thread
     * indefinitely, without the possibility of interrupting the task after
     * the timeout occurs.
     *
     * @param timeout is the maximum time to wait for the task to complete
     * @return the successful result of the task
     * @throws ExecutionException   if the task fails with an exception
     * @throws InterruptedException if the current thread is interrupted.
     *                              The running task is also cancelled, and this method does not
     *                              return until `onCancel` is signaled.
     * @throws TimeoutException     if the task doesn't complete within the
     *                              specified timeout. The running task is also cancelled on timeout,
     *                              and this method does not returning until `onCancel` is signaled.
     */
    public T runBlockingTimed(final Duration timeout)
        throws ExecutionException, InterruptedException, TimeoutException {
        return runBlockingTimed(TaskExecutors.sharedBlockingIO(), timeout);
    }

    /**
     * Creates a task from an asynchronous computation, initiated on the current
     * thread.
     * <p>
     * This method ensures:
     * <ol>
     *     <li>Idempotent cancellation</li>
     *     <li>Trampolined execution to avoid stack-overflows</li>
     * </ol>
     * <p>
     *
     * @param start is the function that will trigger the async computation,
     *              injecting a callback that will be used to signal the result,
     *              and an executor that can be used for creating additional threads.
     * @return a new task that will execute the given builder function upon execution
     */
    public static <T> Task<T> fromAsync(final AsyncFun<? extends T> start) {
        return new Task<>((cont) ->
            Trampoline.execute(() -> {
                try {
                    cont.registerForwardCancellable().set(
                        start.invoke(cont.getExecutor(), cont)
                    );
                } catch (final Throwable e) {
                    UncaughtExceptionHandler.rethrowIfFatal(e);
                    cont.onFailure(e);
                }
            }));
    }

    /**
     * Creates a task from a {@link DelayedFun} executing blocking IO.
     * <p>
     * This uses Java's interruption protocol (i.e., {@link Thread#interrupt()})
     * for cancelling the task.
     */
    public static <T> Task<T> fromBlockingIO(final DelayedFun<? extends T> run) {
        return new Task<>((cont) -> {
            Thread th = Thread.currentThread();
            cont.registerCancellable(th::interrupt);
            try {
                T result;
                try {
                    //noinspection DataFlowIssue
                    result = run.invoke();
                } finally {
                    cont.registerCancellable(Cancellable.getEmpty());
                }
                if (th.isInterrupted()) {
                    throw new InterruptedException();
                }
                cont.onSuccess(result);
            } catch (final InterruptedException | TaskCancellationException e) {
                cont.onCancellation();
            } catch (Throwable e) {
                UncaughtExceptionHandler.rethrowIfFatal(e);
                cont.onFailure(e);
            } finally {
                //noinspection ResultOfMethodCallIgnored
                Thread.interrupted();
            }
        });
    }

    /**
     * Creates a task from a {@link Future} builder.
     * <p>
     * This is compatible with Java's interruption protocol and
     * {@link Future#cancel(boolean)}, with the resulting task being cancellable.
     * <p>
     * <strong>NOTE:</strong> Use {@link #fromCompletionStage(DelayedFun)} for directly
     * converting {@link CompletableFuture} builders, because it is not possible to cancel
     * such values, and the logic needs to reflect it. Better yet, use
     * {@link #fromCancellableFuture(DelayedFun)} for working with {@link CompletionStage}
     * values that can be cancelled.
     *
     * @param builder is the {@link DelayedFun} that will create the {@link Future} upon
     *                this task's execution.
     * @return a new task that will complete with the result of the created {@code Future}
     * upon execution
     * @see #fromCompletionStage(DelayedFun)
     * @see #fromCancellableFuture(DelayedFun)
     */
    public static <T> Task<T> fromBlockingFuture(final DelayedFun<Future<? extends T>> builder) {
        return fromBlockingIO(() -> {
            final var f = Objects.requireNonNull(builder.invoke());
            try {
                return f.get();
            } catch (final ExecutionException e) {
                if (e.getCause() instanceof RuntimeException)
                    throw (RuntimeException) e.getCause();
                throw e;
            } catch (final InterruptedException e) {
                f.cancel(true);
                // We need to wait for this future to complete, as we need to
                // try to back-pressure on its interruption (doesn't really work).
                while (!f.isDone()) {
                    // Ignore further interruption signals
                    //noinspection ResultOfMethodCallIgnored
                    Thread.interrupted();
                    try {
                        f.get();
                    } catch (final Exception ignored) {
                    }
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
     * Prefer using {@link #fromCancellableFuture(DelayedFun)} for working with
     * {@link CompletionStage} values that can be cancelled.
     *
     * @param builder is the {@link DelayedFun} that will create the {@link CompletionStage}
     *                value. It's a builder because {@link Task} values are cold values
     *                (lazy, not executed yet).
     * @return a new task that upon execution will complete with the result of
     * the created {@code CancellableCompletionStage}
     * @see #fromCancellableFuture(DelayedFun)
     */
    public static <T> Task<T> fromCompletionStage(
        final DelayedFun<? extends CompletionStage<? extends T>> builder
    ) {
        return fromCancellableFuture(
            () -> new CancellableFuture<T>(
                Objects.requireNonNull(builder.invoke()).toCompletableFuture(),
                Cancellable.getEmpty()
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
     * @param builder is the {@link DelayedFun} that will create the {@link CancellableFuture}
     *                value. It's a builder because {@link Task} values are cold values
     *                (lazy, not executed yet).
     * @return a new task that upon execution will complete with the result of
     * the created {@link CancellableFuture}
     */
    public static <T> Task<T> fromCancellableFuture(
        final DelayedFun<CancellableFuture<? extends T>> builder
    ) {
        return new Task<>(new TaskFromCancellableFuture<>(builder));
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
@NullMarked
final class BlockingCompletionCallback<T extends @Nullable Object>
    extends AbstractQueuedSynchronizer implements CompletionCallback<T> {

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
            UncaughtExceptionHandler.logOrRethrow(e);
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
                    if (e instanceof TimeoutException)
                        timedOut = (TimeoutException) e;
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
}

/**
 * INTERNAL API.
 * <p>
 * <strong>INTERNAL API:</strong> Internal apis are subject to change or removal
 * without any notice. When code depends on internal APIs, it is subject to
 * breakage between minor version updates.
 */
@ApiStatus.Internal
@NullMarked
final class TaskFromCancellableFuture<T extends @Nullable Object>
    implements AsyncContinuationFun<T> {

    private final DelayedFun<CancellableFuture<? extends T>> builder;

    public TaskFromCancellableFuture(DelayedFun<CancellableFuture<? extends T>> builder) {
        this.builder = builder;
    }

    @Override
    public void invoke(Continuation<? super T> continuation) {
        try {
            final var cancellableRef =
                continuation.registerForwardCancellable();
            final var cancellableFuture =
                Objects.requireNonNull(builder.invoke());
            final CompletableFuture<? extends T> future =
                getCompletableFuture(cancellableFuture, continuation);
            final Cancellable cancellable =
                cancellableFuture.cancellable();

            cancellableRef.set(() -> {
                try {
                    cancellable.cancel();
                } finally {
                    future.cancel(true);
                }
            });
        } catch (Throwable e) {
            UncaughtExceptionHandler.rethrowIfFatal(e);
            continuation.onFailure(e);
        }
    }

    private static <T> CompletableFuture<? extends T> getCompletableFuture(
        CancellableFuture<? extends T> cancellableFuture,
        CompletionCallback<? super T> callback
    ) {
        CompletableFuture<? extends T> future = cancellableFuture.future();
        future.whenComplete((value, error) -> {
            if (error instanceof InterruptedException || error instanceof TaskCancellationException) {
                callback.onCancellation();
            } else if (error instanceof ExecutionException) {
                callback.onFailure(error.getCause() != null ? error.getCause() : error);
            } else if (error instanceof CompletionException) {
                callback.onFailure(error.getCause() != null ? error.getCause() : error);
            } else {
                if (error != null) callback.onFailure(error);
                else callback.onSuccess(value);
            }
        });
        return future;
    }
}
