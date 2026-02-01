package org.funfix.tasks.jvm;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Blocking;
import org.jetbrains.annotations.NonBlocking;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.*;

/**
 * Represents a function that can be executed asynchronously.
 */
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
    public Task<T> ensureRunningOnExecutor(final @Nullable Executor executor) {
        return new Task<>((cont) -> {
            final Continuation<T> cont2 = executor != null
                ? cont.withExecutorOverride(TaskExecutor.from(executor))
                : cont;
            cont2.getExecutor().resumeOnExecutor(() -> createFun.invoke(cont2));
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
        return ensureRunningOnExecutor(null);
    }

    /**
     * Guarantees that the task will complete with the given callback.
     * <p>
     * This method is useful for releasing resources or performing
     * cleanup operations when the task completes.
     * <p>
     * This callback will be invoked in addition to whatever the client
     * provides as a callback to {@link #runAsync(CompletionCallback)}
     * or similar methods.
     * <p>
     * <strong>WARNING:</strong> The invocation of this method is concurrent
     * with the task's completion, meaning that ordering isn't guaranteed
     * (i.e., a callback installed with this method may be called before or
     * after the callback provided to {@link #runAsync(CompletionCallback)}).
     */
    public Task<T> withOnComplete(final CompletionCallback<? extends T> callback) {
        return new Task<>((cont) -> {
            @SuppressWarnings("unchecked")
            final var extraCallback = (CompletionCallback<T>) Objects.requireNonNull(callback);
            cont.registerExtraCallback(extraCallback);
            cont.getExecutor().resumeOnExecutor(() -> createFun.invoke(cont));
        });
    }

    /**
     * Registers a {@link Cancellable} that can be used to cancel the running task.
     */
    public Task<T> withCancellation(
        final Cancellable cancellable
    ) {
        return new Task<>((cont) -> {
            cont.registerCancellable(cancellable);
            cont.getExecutor().resumeOnExecutor(() -> createFun.invoke(cont));
        });
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
        final @Nullable Executor executor,
        final CompletionCallback<? super T> callback
    ) {
        final var taskExecutor = TaskExecutor.from(
            executor != null ? executor : TaskExecutors.sharedBlockingIO()
        );
        @SuppressWarnings("unchecked")
        final var cont = new CancellableContinuation<>(
            taskExecutor,
            new AsyncContinuationCallback<>(
                (CompletionCallback<T>) callback,
                taskExecutor
            )
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
        return runAsync(null, callback);
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
    public Fiber<T> runFiber(final @Nullable Executor executor) {
        return ExecutedFiber.start(
            executor != null ? executor : TaskExecutors.sharedBlockingIO(),
            createFun
        );
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
        return runFiber(null);
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
    @Blocking
    public T runBlocking(final @Nullable Executor executor)
        throws ExecutionException, InterruptedException {

        final var blockingCallback = new BlockingCompletionCallback<T>();
        final var taskExecutor = TaskExecutor.from(
            executor != null ? executor : TaskExecutors.sharedBlockingIO()
        );
        final var cont = new CancellableContinuation<>(taskExecutor, blockingCallback);
        createFun.invoke(cont);
        TaskLocalContext.signalTheStartOfBlockingCall();
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
    @Blocking
    public T runBlocking() throws ExecutionException, InterruptedException {
        return runBlocking(null);
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
        final @Nullable Executor executor,
        final Duration timeout
    ) throws ExecutionException, InterruptedException, TimeoutException {
        final var blockingCallback = new BlockingCompletionCallback<T>();
        final var taskExecutor = TaskExecutor.from(
            executor != null ? executor : TaskExecutors.sharedBlockingIO()
        );
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
        return runBlockingTimed(null, timeout);
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
    public static <T extends @Nullable Object> Task<T> fromAsync(final AsyncFun<? extends T> start) {
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
    public static <T extends @Nullable Object> Task<T> fromBlockingIO(final DelayedFun<? extends T> run) {
        return new Task<>((cont) -> {
            Thread th = Thread.currentThread();
            final var registration = cont.registerCancellable(th::interrupt);
            if (registration == null) {
                cont.onCancellation();
                return;
            }
            try {
                T result;
                try {
                    TaskLocalContext.signalTheStartOfBlockingCall();
                    result = run.invoke();
                } finally {
                    registration.cancel();
                }
                if (th.isInterrupted()) {
                    throw new InterruptedException();
                }
                //noinspection DataFlowIssue
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
    public static <T extends @Nullable Object> Task<T> fromBlockingFuture(final DelayedFun<Future<? extends T>> builder) {
        return fromBlockingIO(() -> {
            final var f = Objects.requireNonNull(builder.invoke());
            try {
                TaskLocalContext.signalTheStartOfBlockingCall();
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
    public static <T extends @Nullable Object> Task<T> fromCompletionStage(
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
    public static <T extends @Nullable Object> Task<T> fromCancellableFuture(
        final DelayedFun<CancellableFuture<? extends T>> builder
    ) {
        return new Task<>(new TaskFromCancellableFuture<>(builder));
    }

    /**
     * Creates a task that completes with the given static/pure value.
     */
    public static <T extends @Nullable Object> Task<T> pure(final T value) {
        //noinspection DataFlowIssue
        return new Task<>((cont) -> cont.onSuccess(value));
    }

    /** Reusable "void" task that does nothing, completing immediately. */
    @SuppressWarnings({"NullAway", "DataFlowIssue"})
    public static final Task<Void> NOOP = Task.pure(null);
}

/**
 * INTERNAL API.
 * <p>
 * <strong>INTERNAL API:</strong> Internal apis are subject to change or removal
 * without any notice. When code depends on internal APIs, it is subject to
 * breakage between minor version updates.
 */
@ApiStatus.Internal
final class TaskFromCancellableFuture<T extends @Nullable Object>
    implements AsyncContinuationFun<T> {

    private final DelayedFun<CancellableFuture<? extends T>> builder;

    public TaskFromCancellableFuture(DelayedFun<CancellableFuture<? extends T>> builder) {
        this.builder = builder;
    }

    @Override
    public void invoke(Continuation<T> continuation) {
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
                    future.cancel(true);
                } finally {
                    cancellable.cancel();
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
            if (error instanceof InterruptedException || error instanceof TaskCancellationException || error instanceof CancellationException) {
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
