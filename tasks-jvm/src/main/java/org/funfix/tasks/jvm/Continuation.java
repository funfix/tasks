package org.funfix.tasks.jvm;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.Executor;

/**
 * Continuation objects are used to complete tasks, or for registering
 * {@link Cancellable} references that can be used to interrupt running tasks.
 * <p>
 * {@code Continuation} objects get injected in {@link AsyncFun} functions.
 * See {@link Task#fromAsync(AsyncFun)}.
 * <p>
 * The {@code Continuation} provides the execution context and the means to
 * signal the task's outcome. It also allows registering cleanup actions
 * that run if the task is cancelled.
 *
 * @param <T> is the type of the value that the task will complete with
 */
public interface Continuation<T extends @Nullable Object>
    extends CompletionCallback<T> {

    /**
     * Returns the {@link Executor} that the task can use to run its
     * asynchronous computation.
     */
    Executor getExecutor();

    /**
     * Registers a finalizer that is invoked if the running task is cancelled
     * before completion.
     * <p>
     * The finalizer is called only for cancellation before completion.
     * If cancellation already happened, the finalizer may be called immediately.
     * The finalizer is not called after success, failure, or terminal completion.
     * <p>
     * Finalizers must be idempotent, fast, non-blocking, and thread-safe.
     * Exceptions from finalizers are handled via {@link UncaughtExceptionHandler}.
     *
     * @param finalizer the cleanup action to run on cancellation
     */
    @Nullable Cancellable invokeOnCancellation(Cancellable finalizer);
}

/**
 * INTERNAL API.
 */
@ApiStatus.Internal
interface InternalContinuation<T extends @Nullable Object>
    extends Continuation<T> {

    TaskExecutor getTaskExecutor();

    InternalContinuation<T> withExecutorOverride(TaskExecutor executor);

    void registerExtraCallback(CompletionCallback<T> extraCallback);
}

/**
 * INTERNAL API.
 */
@ApiStatus.Internal
@FunctionalInterface
interface AsyncContinuationFun<T extends @Nullable Object> {
    void invoke(InternalContinuation<T> continuation);
}

/**
 * INTERNAL API.
 */
@ApiStatus.Internal
final class CancellableContinuation<T extends @Nullable Object>
    implements InternalContinuation<T>, Cancellable {

    private final ContinuationCallback<T> callback;
    private final MutableCancellable cancellableRef;
    private final TaskExecutor executor;

    public CancellableContinuation(
        final TaskExecutor executor,
        final ContinuationCallback<T> callback
    ) {
        this(
            executor,
            callback,
            new MutableCancellable()
        );
    }

    CancellableContinuation(
        final TaskExecutor executor,
        final ContinuationCallback<T> callback,
        final MutableCancellable cancellable
    ) {
        this.executor = executor;
        this.callback = callback;
        this.cancellableRef = cancellable;
    }

    @Override
    public Executor getExecutor() {
        return this.executor;
    }

    @Override
    public TaskExecutor getTaskExecutor() {
        return this.executor;
    }

    @Override
    public void cancel() {
        cancellableRef.cancel();
    }

    @Override
    public @Nullable Cancellable invokeOnCancellation(final Cancellable finalizer) {
        return cancellableRef.register(finalizer);
    }

    @Override
    public void onOutcome(final Outcome<? extends T> outcome) {
        cancellableRef.complete();
        callback.onOutcome(outcome);
    }

    @Override
    public void onSuccess(final T value) {
        cancellableRef.complete();
        callback.onSuccess(value);
    }

    @Override
    public void onFailure(final Throwable e) {
        cancellableRef.complete();
        callback.onFailure(e);
    }

    @Override
    public void onCancellation() {
        cancellableRef.complete();
        callback.onCancellation();
    }

    @Override
    public InternalContinuation<T> withExecutorOverride(final TaskExecutor executor) {
        return new CancellableContinuation<>(
            executor,
            callback,
            cancellableRef
        );
    }

    @Override
    public void registerExtraCallback(final CompletionCallback<T> extraCallback) {
        callback.registerExtraCallback(extraCallback);
    }
}
