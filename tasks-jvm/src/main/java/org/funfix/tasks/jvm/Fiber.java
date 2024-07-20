package org.funfix.tasks.jvm;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.jetbrains.annotations.Blocking;
import org.jetbrains.annotations.NonBlocking;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@NullMarked
public interface Fiber<T extends @Nullable Object> extends Cancellable {
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
     */
    @NonBlocking
    Cancellable joinAsync(Runnable onComplete);

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
    default void joinBlockingTimed(final long timeoutMillis)
        throws InterruptedException, TimeoutException {

        final var latch = new AwaitSignal();
        final var token = joinAsync(latch::signal);
        try {
            latch.await(timeoutMillis);
        } catch (final InterruptedException | TimeoutException e) {
            token.cancel();
            throw e;
        }
    }

    /**
     * Overload of {@link #joinBlockingTimed(long)} that takes a standard Java
     * {@link Duration} as the timeout.
     */
    @Blocking
    default void joinBlockingTimed(final Duration timeout)
        throws InterruptedException, TimeoutException {
        joinBlockingTimed(timeout.toMillis());
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
     * Returns a {@link CancellableFuture} that can be used to join the fiber
     * asynchronously, or to cancel it.
     */
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
     * Thrown in case {@link #getResultOrThrow()} is called before the fiber
     * completes (i.e., before one of the "join" methods return).
     */
    final class NotCompletedException extends Exception {
        public NotCompletedException() {
            super("Fiber is not completed");
        }
    }
}

@NullMarked
final class ExecutedFiber<T extends @Nullable Object> implements Fiber<T> {
    private final AtomicReference<State<T>> stateRef = new AtomicReference<>(State.start());

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
                Trampoline.execute(onComplete);
                return Cancellable.EMPTY;
            }
        }
    }

    @Override
    public void cancel() {
        while (true) {
            final var current = stateRef.get();
            if (current instanceof State.Active) {
                final var active = (State.Active<T>) current;
                if (stateRef.compareAndSet(current, new State.Cancelled<>(active.listeners))) {
                    if (active.token != null) {
                        active.token.cancel();
                    }
                    return;
                }
            } else {
                return;
            }
        }
    }

    final CompletionCallback<T> onComplete = new CompletionCallback<>() {
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

        private void onOutcome(Outcome<T> outcome) {
            while (true) {
                State<T> current = stateRef.get();
                if (current instanceof State.Active) {
                    if (stateRef.compareAndSet(current, new State.Completed<>(outcome))) {
                        current.triggerListeners();
                        return;
                    }
                } else if (current instanceof State.Cancelled) {
                    State.Completed<T> update = new State.Completed<>(Outcome.cancellation());
                    if (stateRef.compareAndSet(current, update)) {
                        current.triggerListeners();
                        return;
                    }
                } else if (current instanceof State.Completed) {
                    if (outcome instanceof Outcome.Failure) {
                        final var failure = (Outcome.Failure<T>) outcome;
                        UncaughtExceptionHandler.logOrRethrow(failure.exception());
                    }
                    return;
                } else {
                    throw new IllegalStateException("Invalid state: " + current);
                }
            }
        }
    };

    public void registerCancel(final Cancellable token) {
        while (true) {
            final var current = stateRef.get();
            if (current instanceof State.Active) {
                final var active = (State.Active<T>) current;
                if (active.token != null) {
                    throw new IllegalStateException("Already registered a cancel token");
                }
                final var update = new State.Active<T>(active.listeners, token);
                if (stateRef.compareAndSet(current, update)) {
                    return;
                }
            } else if (current instanceof State.Cancelled) {
                token.cancel();
                return;
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

    static abstract class State<T extends @Nullable Object> {
        @Data
        @EqualsAndHashCode(callSuper = false)
        static final class Active<T extends @Nullable Object>
            extends State<T> {
            private final List<Runnable> listeners;
            private final @Nullable Cancellable token;
        }

        @Data
        @EqualsAndHashCode(callSuper = false)
        static final class Cancelled<T extends @Nullable Object>
            extends State<T> {
            private final List<Runnable> listeners;
        }

        @Data
        @EqualsAndHashCode(callSuper = false)
        static final class Completed<T extends @Nullable Object>
            extends State<T> {
            private final Outcome<T> outcome;
        }

        final void triggerListeners() {
            if (this instanceof Active) {
                final var ref = (Active<T>) this;
                for (final var listener : ref.listeners) {
                    Trampoline.execute(listener);
                }
            } else if (this instanceof Cancelled) {
                final var ref = (Cancelled<T>) this;
                for (final var listener : ref.listeners) {
                    Trampoline.execute(listener);
                }
            }
        }

        final State<T> addListener(final Runnable listener) {
            if (this instanceof Active) {
                final var ref = (Active<T>) this;
                final var newList = Stream
                    .concat(ref.listeners.stream(), Stream.of(listener))
                    .collect(Collectors.toList());
                return new Active<>(newList, ref.token);
            } else if (this instanceof Cancelled) {
                final var ref = (Cancelled<T>) this;
                final var newList = Stream
                    .concat(ref.listeners.stream(), Stream.of(listener))
                    .collect(Collectors.toList());
                return new Cancelled<>(newList);
            } else {
                return this;
            }
        }

        final State<T> removeListener(final Runnable listener) {
            if (this instanceof Active) {
                final var ref = (Active<T>) this;
                final var newList = ref.listeners
                    .stream()
                    .filter(l -> l != listener)
                    .collect(Collectors.toList());
                return new Active<>(newList, ref.token);
            } else if (this instanceof Cancelled) {
                final var ref = (Cancelled<T>) this;
                final var newList = ref.listeners
                    .stream()
                    .filter(l -> l != listener)
                    .collect(Collectors.toList());
                return new Cancelled<>(newList);
            } else {
                return this;
            }
        }

        static <T extends @Nullable Object> State<T> start() {
            return new Active<>(List.of(), null);
        }
    }

    public static <T extends @Nullable Object> Fiber<T> start(
        final Executor executor,
        final AsyncFun<T> asyncFun
    ) {
        final var fiber = new ExecutedFiber<T>();
        try {
            final var token = asyncFun.invoke(executor, fiber.onComplete);
            fiber.registerCancel(token);
        } catch (final Throwable e) {
            UncaughtExceptionHandler.rethrowIfFatal(e);
            fiber.onComplete.onFailure(e);
        }
        return fiber;
    }
}

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
