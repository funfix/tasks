package org.funfix.tasks.jvm;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Blocking;
import org.jetbrains.annotations.NonBlocking;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;

/**
 * A {@code Fiber} is a task that was started concurrently, and that can
 * be joined or cancelled.
 * <p>
 * Fibers are the equivalent of threads, except they are higher-level and
 * library-managed, instead of being intrinsic to the JVM.
 *
 * @param <T> is the result of the fiber, if successful.
 */
@NullMarked
public interface Fiber<T extends @Nullable Object> extends Cancellable {
    /**
     * Returns the result of the completed fiber.
     * <p>
     * This method does not block for the result. In case the fiber is not
     * completed, it throws {@link NotCompletedException}. Therefore, by contract,
     * it should be called only after the fiber was "joined".
     *
     * @return the result of the concurrent task, if successful.
     * @throws ExecutionException        if the task failed with an exception.
     * @throws TaskCancellationException if the task was cancelled concurrently,
     *                                   thus being completed via cancellation.
     * @throws NotCompletedException     if the fiber is not completed yet.
     */
    @NonBlocking
    T getResultOrThrow() throws ExecutionException, TaskCancellationException, NotCompletedException;

    /**
     * Waits until the fiber completes, and then runs the given callback to
     * signal its completion.
     * <p>
     * Completion includes cancellation. Triggering {@link #cancel()} before
     * {@code joinAsync} will cause the fiber to get cancelled, and then the
     * "join" back-pressures on cancellation.
     *
     * @param onComplete is the callback to run when the fiber completes
     *                   (successfully, or with failure, or cancellation)
     *
     * @return a {@link Cancellable} that can be used to unregister the callback,
     * in case the caller is no longer interested in the result. Note this
     * does not cancel the fiber itself.
     */
    @NonBlocking
    Cancellable joinAsync(Runnable onComplete);

    /**
     * Waits until the fiber completes, and then runs the given callback
     * to signal its completion.
     * <p>
     * This method can be executed as many times as necessary, with the
     * result of the {@code Fiber} being memoized. It can also be executed
     * after the fiber has completed, in which case the callback will be
     * executed immediately.
     *
     * @param callback will be called with the result when the fiber completes.
     *
     * @return a {@link Cancellable} that can be used to unregister the callback,
     * in case the caller is no longer interested in the result. Note this
     * does not cancel the fiber itself.
     */
    @NonBlocking
    default Cancellable awaitAsync(CompletionCallback<? super T> callback) {
        return joinAsync(() -> {
            try {
                final var result = getResultOrThrow();
                callback.onSuccess(result);
            } catch (final ExecutionException e) {
                callback.onFailure(e.getCause());
            } catch (final TaskCancellationException e) {
                callback.onCancellation();
            } catch (final Throwable e) {
                UncaughtExceptionHandler.rethrowIfFatal(e);
                callback.onFailure(e);
            }
        });
    }

    /**
     * Blocks the current thread until the fiber completes, or until
     * the timeout is reached.
     * <p>
     * This method does not return the outcome of the fiber. To check
     * the outcome, use [outcome].
     *
     * @throws InterruptedException if the current thread is interrupted, which
     *                              will just stop waiting for the fiber, but will not cancel the running
     *                              task.
     * @throws TimeoutException     if the timeout is reached before the fiber
     *                              completes.
     */
    @Blocking
    default void joinBlockingTimed(final Duration timeout)
        throws InterruptedException, TimeoutException {

        final var latch = new AwaitSignal();
        final var token = joinAsync(latch::signal);
        try {
            latch.await(timeout.toMillis());
        } catch (final InterruptedException | TimeoutException e) {
            token.cancel();
            throw e;
        }
    }

    /**
     * Blocks the current thread until the fiber completes, then returns
     * the result of the fiber.
     *
     * @param timeout is the maximum time to wait for the fiber to complete,
     *                before throwing a {@link TimeoutException}.
     * @return the result of the fiber, if successful.
     * @throws InterruptedException      if the current thread is interrupted, which
     *                                   will just stop waiting for the fiber, but will not
     *                                   cancel the running task.
     * @throws TimeoutException          if the timeout is reached before the fiber completes.
     * @throws TaskCancellationException if the fiber was cancelled concurrently.
     * @throws ExecutionException        if the task failed with an exception.
     */
    @Blocking
    default T awaitBlockingTimed(final Duration timeout)
        throws InterruptedException, TimeoutException, TaskCancellationException, ExecutionException {

        joinBlockingTimed(timeout);
        try {
            return getResultOrThrow();
        } catch (NotCompletedException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Blocks the current thread until the fiber completes.
     * <p>
     * This method does not return the outcome of the fiber. To check
     * the outcome, use [outcome].
     *
     * @throws InterruptedException if the current thread is interrupted, which
     *                              will just stop waiting for the fiber, but will not cancel the running
     *                              task.
     */
    @Blocking
    default void joinBlocking() throws InterruptedException {
        final var latch = new AwaitSignal();
        final var token = joinAsync(latch::signal);
        try {
            latch.await();
        } finally {
            token.cancel();
        }
    }

    /**
     * Blocks the current thread until the fiber completes, then returns the
     * result of the fiber.
     *
     * @throws InterruptedException      if the current thread is interrupted, which
     *                                   will just stop waiting for the fiber, but will not
     *                                   cancel the running task.
     * @throws TaskCancellationException if the fiber was cancelled concurrently.
     * @throws ExecutionException        if the task failed with an exception.
     */
    @Blocking
    default T awaitBlocking() throws InterruptedException, TaskCancellationException, ExecutionException {
        joinBlocking();
        try {
            return getResultOrThrow();
        } catch (NotCompletedException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Cancels the fiber, which will eventually stop the running fiber (if
     * it's still running), completing it via "cancellation".
     * <p>
     * This manifests either in a {@link TaskCancellationException} being
     * thrown by {@link #getResultOrThrow()}, or in the
     * {@link CompletionCallback#onCancellation()} callback being triggered.
     */
    @NonBlocking
    @Override void cancel();

    /**
     * Returns a {@link CancellableFuture} that can be used to join the fiber
     * asynchronously, or to cancel it.
     *
     * @return a {@link CancellableFuture} that can be used to asynchronously
     * process its result, or to give up on listening for the result. Note that
     * cancelling the returned future does not cancel the fiber itself, for
     * that you need to call {@link Fiber#cancel()}.
     */
    @NonBlocking
    default CancellableFuture<@Nullable Void> joinAsync() {
        final var future = new CompletableFuture<@Nullable Void>();
        final var token = joinAsync(() -> future.complete(null));
        final Cancellable cRef = () -> {
            try {
                token.cancel();
            } finally {
                future.cancel(false);
            }
        };
        return new CancellableFuture<>(future, cRef);
    }

    /**
     * Overload of {@link #awaitAsync(CompletionCallback)}.
     *
     * @return a {@link CancellableFuture} that can be used to asynchronously
     * process its result or to give up on listening for the result. Note that
     * cancelling the returned future does not cancel the fiber itself, for
     * that you need to call {@link Fiber#cancel()}.
     */
    @NonBlocking
    default CancellableFuture<T> awaitAsync() {
        final var f = joinAsync();
        return f.transform(it -> it.thenApply(v -> {
            try {
                return getResultOrThrow();
            } catch (final ExecutionException e) {
                throw new CompletionException(e.getCause());
            } catch (final Throwable e) {
                UncaughtExceptionHandler.rethrowIfFatal(e);
                throw new CompletionException(e);
            }
        }));
    }

    /**
     * Thrown in case {@link #getResultOrThrow()} is called before the fiber
     * completes (i.e., before one of the "join" methods return).
     */
    final class NotCompletedException extends Exception {
        public NotCompletedException() {
            super("Fiber is not completed");
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
@NullMarked
final class ExecutedFiber<T extends @Nullable Object> implements Fiber<T> {
    private final TaskExecutor executor;
    private final Continuation<? super T> continuation;
    private final AtomicReference<State<T>> stateRef =
        new AtomicReference<>(State.start());

    private ExecutedFiber(final TaskExecutor executor) {
        this.executor = executor;
        this.continuation = new FiberContinuation<>(executor, stateRef);
    }

    @Override
    public T getResultOrThrow() throws ExecutionException, TaskCancellationException, NotCompletedException {
        final var current = stateRef.get();
        if (current instanceof State.Completed) {
            return ((State.Completed<T>) current).outcome.getOrThrow();
        } else {
            throw new NotCompletedException();
        }
    }

    @Override
    public Cancellable joinAsync(final Runnable onComplete) {
        while (true) {
            final var current = stateRef.get();
            if (current instanceof State.Active || current instanceof State.Cancelled) {
                final var update = current.addListener(onComplete);
                if (stateRef.compareAndSet(current, update)) {
                    return removeListenerCancellable(onComplete);
                }
            } else {
                executor.resumeOnExecutor(onComplete);
                return Cancellable.getEmpty();
            }
        }
    }

    @Override
    public void cancel() {
        while (true) {
            final var current = stateRef.get();
            if (current instanceof State.Active<T> active) {
                if (stateRef.compareAndSet(current, new State.Cancelled<>(active.listeners))) {
                    active.cancellable.cancel();
                    return;
                }
            } else {
                return;
            }
        }
    }

    private Cancellable removeListenerCancellable(final Runnable listener) {
        return () -> {
            while (true) {
                final var current = stateRef.get();
                if (current instanceof State.Active || current instanceof State.Cancelled) {
                    final var update = current.removeListener(listener);
                    if (stateRef.compareAndSet(current, update)) {
                        return;
                    }
                } else {
                    return;
                }
            }
        };
    }

    @NullMarked
    static abstract class State<T extends @Nullable Object> {
        @ToString
        @EqualsAndHashCode(callSuper = false)
        @Getter
        static final class Active<T extends @Nullable Object>
            extends State<T> {
            private final ImmutableQueue<Runnable> listeners;
            private final MutableCancellable cancellable;

            public Active(ImmutableQueue<Runnable> listeners, MutableCancellable cancellable) {
                this.listeners = listeners;
                this.cancellable = cancellable;
            }
        }

        @ToString
        @EqualsAndHashCode(callSuper = false)
        @Getter
        static final class Cancelled<T extends @Nullable Object>
            extends State<T> {
            private final ImmutableQueue<Runnable> listeners;

            public Cancelled(ImmutableQueue<Runnable> listeners) {
                this.listeners = listeners;
            }
        }

        @ToString
        @EqualsAndHashCode(callSuper = false)
        @Getter
        static final class Completed<T extends @Nullable Object>
            extends State<T> {
            private final Outcome<T> outcome;
            public Completed(Outcome<T> outcome) {
                this.outcome = outcome;
            }
        }

        final void triggerListeners(TaskExecutor executor) {
            if (this instanceof Active<T> ref) {
                for (final var listener : ref.listeners) {
                    executor.resumeOnExecutor(listener);
                }
            } else if (this instanceof Cancelled<T> ref) {
                for (final var listener : ref.listeners) {
                    executor.resumeOnExecutor(listener);
                }
            }
        }

        final State<T> addListener(final Runnable listener) {
            if (this instanceof Active<T> ref) {
                final var newQueue = ref.listeners.enqueue(listener);
                return new Active<>(newQueue, ref.cancellable);
            } else if (this instanceof Cancelled<T> ref) {
                final var newQueue = ref.listeners.enqueue(listener);
                return new Cancelled<>(newQueue);
            } else {
                return this;
            }
        }

        final State<T> removeListener(final Runnable listener) {
            if (this instanceof Active<T> ref) {
                final var newQueue = ref.listeners.filter(l -> l != listener);
                return new Active<>(newQueue, ref.cancellable);
            } else if (this instanceof Cancelled<T> ref) {
                final var newQueue = ref.listeners.filter(l -> l != listener);
                return new Cancelled<>(newQueue);
            } else {
                return this;
            }
        }

        static <T extends @Nullable Object> State<T> start() {
            return new Active<>(ImmutableQueue.empty(), new MutableCancellable());
        }
    }

    static <T extends @Nullable Object> Fiber<T> start(
        final Executor executor,
        final AsyncContinuationFun<T> createFun
    ) {
        final var taskExecutor = TaskExecutor.from(executor);
        final var fiber = new ExecutedFiber<T>(taskExecutor);
        taskExecutor.execute(() -> {
            try {
                createFun.invoke(fiber.continuation);
            } catch (final Throwable e) {
                UncaughtExceptionHandler.rethrowIfFatal(e);
                fiber.continuation.onFailure(e);
            }
        });
        return fiber;
    }

    @NullMarked
    static final class FiberContinuation<T extends @Nullable Object> implements Continuation<T> {
        private final TaskExecutor executor;
        private final AtomicReference<ExecutedFiber.State<T>> stateRef;

        FiberContinuation(
            final TaskExecutor executor,
            final AtomicReference<ExecutedFiber.State<T>> stateRef
        ) {
            this.executor = executor;
            this.stateRef = stateRef;
        }

        @Override
        public TaskExecutor getExecutor() {
            return executor;
        }

        @Override
        public CancellableForwardRef registerForwardCancellable() {
            final var current = stateRef.get();
            if (current instanceof State.Completed) {
                return (cancellable) -> {};
            } else if (current instanceof State.Cancelled) {
                return Cancellable::cancel;
            } else if (current instanceof State.Active<T> active) {
                return active.cancellable.newCancellableRef();
            } else {
                throw new IllegalStateException("Invalid state: " + current);
            }
        }

        @Override
        public void registerCancellable(Cancellable cancellable) {
            final var current = stateRef.get();
            if (current instanceof State.Active<T> active) {
                active.cancellable.register(cancellable);
            } else if (current instanceof State.Cancelled) {
                cancellable.cancel();
            }
        }

        @Override
        public void onSuccess(T value) {
            onOutcome(Outcome.success(value));
        }

        @Override
        public void onFailure(Throwable e) {
            onOutcome(Outcome.failure(e));
        }

        @Override
        public void onCancellation() {
            onOutcome(Outcome.cancellation());
        }

        @Override
        public void onOutcome(Outcome<T> outcome) {
            while (true) {
                State<T> current = stateRef.get();
                if (current instanceof State.Active) {
                    if (stateRef.compareAndSet(current, new State.Completed<>(outcome))) {
                        current.triggerListeners(executor);
                        return;
                    }
                } else if (current instanceof State.Cancelled) {
                    State.Completed<T> update = new State.Completed<>(Outcome.cancellation());
                    if (stateRef.compareAndSet(current, update)) {
                        current.triggerListeners(executor);
                        return;
                    }
                } else if (current instanceof State.Completed) {
                    if (outcome instanceof Outcome.Failure<T> failure) {
                        UncaughtExceptionHandler.logOrRethrow(failure.exception());
                    }
                    return;
                } else {
                    throw new IllegalStateException("Invalid state: " + current);
                }
            }
        }

        @Override
        public CancellableContinuation<T> withExecutorOverride(TaskExecutor executor) {
            return new CancellableContinuation<>(executor, this);
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
@NullMarked
final class AwaitSignal extends AbstractQueuedSynchronizer {
    @Override
    protected int tryAcquireShared(final int arg) {
        return getState() != 0 ? 1 : -1;
    }

    @Override
    protected boolean tryReleaseShared(final int arg) {
        setState(1);
        return true;
    }

    public void signal() {
        releaseShared(1);
    }

    public void await() throws InterruptedException {
        acquireSharedInterruptibly(1);
    }

    public void await(final long timeoutMillis) throws InterruptedException, TimeoutException {
        if (!tryAcquireSharedNanos(1, TimeUnit.MILLISECONDS.toNanos(timeoutMillis))) {
            throw new TimeoutException("Timed out after " + timeoutMillis + " millis");
        }
    }
}
