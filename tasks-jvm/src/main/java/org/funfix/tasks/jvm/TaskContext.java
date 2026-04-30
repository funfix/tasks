package org.funfix.tasks.jvm;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.Executor;

/**
 * INTERNAL API.
 * <p>
 * Task context objects used to complete tasks, or for registering
 * {@link Cancellable} references that can be used to interrupt running tasks.
 * <p>
 * {@code TaskContext} objects get injected in {@link AsyncFun} functions.
 * See {@link Task#fromAsync(AsyncFun)}.
 *
 * @param <T> is the type of the value that the task will complete with
 */
@ApiStatus.Internal
interface TaskContext<T extends @Nullable Object>
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
     * @param cancellable is the reference to the cancellable object, registered
     *                    to be invoked when the running task gets canceled.
     *
     * @return a {@link Cancellable} reference that can be used to unregister
     *         the given {@code cancellable} reference, if it was registered
     *         successfully (the task isn't already completed/canceled),
     *         or {@code null} otherwise.
     */
    @Nullable Cancellable registerCancellable(Cancellable cancellable);

    TaskContext<T> withExecutorOverride(TaskExecutor executor);

    void registerExtraCallback(CompletionCallback<T> extraCallback);
}

/**
 * INTERNAL API.
 */
@ApiStatus.Internal
@FunctionalInterface
interface TaskStartFun<T extends @Nullable Object> {
    void invoke(TaskContext<T> context);
}

/**
 * INTERNAL API.
 */
@ApiStatus.Internal
final class CancellableTaskContext<T extends @Nullable Object>
    implements TaskContext<T>, Cancellable {

    private final TaskContextCallback<T> callback;
    private final MutableCancellable cancellableRef;
    private final TaskExecutor executor;

    public CancellableTaskContext(
        final TaskExecutor executor,
        final TaskContextCallback<T> callback
    ) {
        this(
            executor,
            callback,
            new MutableCancellable()
        );
    }

    CancellableTaskContext(
        final TaskExecutor executor,
        final TaskContextCallback<T> callback,
        final MutableCancellable cancellable
    ) {
        this.executor = executor;
        this.callback = callback;
        this.cancellableRef = cancellable;
    }

    @Override
    public TaskExecutor getExecutor() {
        return this.executor;
    }

    @Override
    public void cancel() {
        cancellableRef.cancel();
    }

    @Override
    public @Nullable Cancellable registerCancellable(Cancellable cancellable) {
        return this.cancellableRef.register(cancellable);
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
    public TaskContext<T> withExecutorOverride(TaskExecutor executor) {
        return new CancellableTaskContext<>(
            executor,
            callback,
            cancellableRef
        );
    }

    @Override
    public void registerExtraCallback(CompletionCallback<T> extraCallback) {
        callback.registerExtraCallback(extraCallback);
    }
}
