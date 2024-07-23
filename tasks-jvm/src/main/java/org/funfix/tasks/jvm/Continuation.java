package org.funfix.tasks.jvm;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.NullMarked;
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
@NullMarked
interface Continuation<T extends @Nullable Object>
    extends CompletionCallback<T> {

    /**
     * Returns the {@link Executor} that the task can use to run its
     * asynchronous computation.
     */
    Executor getExecutor();

    /**
     * Registers a {@link Cancellable} reference that can be used to interrupt
     * a running task.
     *
     * @param cancellable is the reference to the cancellable object that this
     *                    continuation will register.
     */
    void registerCancellable(Cancellable cancellable);

    CancellableForwardRef registerForwardCancellable();
}

/**
 * INTERNAL API.
 */
@ApiStatus.Internal
@NullMarked
@FunctionalInterface
interface AsyncContinuationFun<T extends @Nullable Object> {
    void invoke(Continuation<? super T> continuation);
}

/**
 * INTERNAL API.
 */
@ApiStatus.Internal
@NullMarked
final class CancellableContinuation<T extends @Nullable Object>
    implements Continuation<T>, Cancellable {

    private final CompletionCallback<T> callback;
    private final MutableCancellable cancellable;
    private final Executor executor;

    public CancellableContinuation(
        final Executor executor,
        final CompletionCallback<T> callback
    ) {
        this.executor = executor;
        this.callback = callback;
        this.cancellable = new MutableCancellable();
    }

    @Override
    public Executor getExecutor() {
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
}
