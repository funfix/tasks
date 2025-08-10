package org.funfix.tasks.jvm;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.Executor;

/**
 * INTERNAL API.
 * <p>
 * Continuation objects are used to complete tasks, or for registering
 * {@link Cancellable} references that can be used to interrupt running tasks.
 * <p>
 * {@code Continuation} objects get injected in {@link AsyncFun} functions.
 * See {@link Task#fromAsync(AsyncFun)}.
 *
 * @param <T> is the type of the value that the task will complete with
 */
@ApiStatus.Internal
interface Continuation<T extends @Nullable Object>
    extends CompletionCallback<T> {

    /**
     * Returns the {@link Executor} that the task can use to run its
     * asynchronous computation.
     */
    TaskExecutor getExecutor();

    /**
     * Registers a {@link Cancellable} reference that can be used to interrupt
     * a running task.
     *
     * @param cancellable is the reference to the cancellable object that this
     *                    continuation will register.
     */
    void registerCancellable(Cancellable cancellable);

    CancellableForwardRef registerForwardCancellable();

    Continuation<T> withExecutorOverride(TaskExecutor executor);
}

/**
 * INTERNAL API.
 */
@ApiStatus.Internal
@FunctionalInterface
interface AsyncContinuationFun<T extends @Nullable Object> {
    void invoke(Continuation<T> continuation);
}

/**
 * INTERNAL API.
 */
@ApiStatus.Internal
final class CancellableContinuation<T extends @Nullable Object>
    implements Continuation<T>, Cancellable {

    private final CompletionCallback<T> callback;
    private final MutableCancellable cancellable;
    private final TaskExecutor executor;

    public CancellableContinuation(
        final TaskExecutor executor,
        final CompletionCallback<T> callback
    ) {
        this(
            executor,
            callback,
            new MutableCancellable()
        );
    }

    private CancellableContinuation(
        final TaskExecutor executor,
        final CompletionCallback<T> callback,
        final MutableCancellable cancellable
    ) {
        this.executor = executor;
        this.callback = callback;
        this.cancellable = cancellable;
    }

    @Override
    public TaskExecutor getExecutor() {
        return this.executor;
    }

    @Override
    public void cancel() {
        cancellable.cancel();
    }

    @Override
    public CancellableForwardRef registerForwardCancellable() {
        return cancellable.newCancellableRef();
    }

    @Override
    public void registerCancellable(Cancellable cancellable) {
        this.cancellable.register(cancellable);
    }

    @Override
    public void onOutcome(Outcome<T> outcome) {
        callback.onOutcome(outcome);
    }

    @Override
    public void onSuccess(T value) {
        callback.onSuccess(value);
    }

    @Override
    public void onFailure(Throwable e) {
        callback.onFailure(e);
    }

    @Override
    public void onCancellation() {
        callback.onCancellation();
    }

    @Override
    public Continuation<T> withExecutorOverride(TaskExecutor executor) {
        return new CancellableContinuation<>(
            executor,
            callback,
            cancellable
        );
    }
}
