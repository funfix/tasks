package org.funfix.tasks;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;
import java.util.stream.Stream;

@NullMarked
public interface Fiber extends Cancellable {
    Cancellable joinAsync(Runnable onComplete);

    default void joinBlockingTimed(final Duration timeout) throws InterruptedException, TimeoutException {
        final var latch = new AwaitSignal();
        final var token = joinAsync(latch::signal);
        try {
            latch.await(timeout);
        } catch (final InterruptedException | TimeoutException e) {
            token.cancel();
            throw e;
        }
    }

    default void joinBlocking() throws InterruptedException {
        final var latch = new AwaitSignal();
        final var token = joinAsync(latch::signal);
        try {
            latch.await();
        } finally {
            token.cancel();
        }
    }

    default CancellableFuture<@Nullable Void> joinAsync() {
        final var  future = new CompletableFuture<@Nullable Void>();
        final var token = joinAsync(() -> future.complete(null));
        final Cancellable cRef = () -> {
            try { token.cancel(); }
            finally { future.cancel(false); }
        };
        return new CancellableFuture<>(future, cRef);
    }
}

@NullMarked
final class SimpleFiber implements Fiber {
    private final AtomicReference<State> stateRef = new AtomicReference<>(State.START);

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
            if (current instanceof final State.Active active) {
                if (stateRef.compareAndSet(current, new State.Cancelled(active.listeners))) {
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

    public void signalComplete() {
        while (true) {
            final var current = stateRef.get();
            if (current instanceof State.Active || current instanceof State.Cancelled) {
                if (stateRef.compareAndSet(current, new State.Completed())) {
                    current.triggerListeners();
                    return;
                }
            } else {
                return;
            }
        }
    }

    public void registerCancel(final Cancellable token) {
        while (true) {
            final var current = stateRef.get();
            if (current instanceof final State.Active active) {
                if (active.token != null) {
                    throw new IllegalStateException("Already registered a cancel token");
                }
                final var update = new State.Active(active.listeners, token);
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

    sealed interface State {
        record Active(List<Runnable> listeners, @Nullable Cancellable token) implements State {}
        record Cancelled(List<Runnable> listeners) implements State {}
        record Completed() implements State {}

        default void triggerListeners() {
            if (this instanceof final Active ref)
                for (final var listener : ref.listeners) {
                    Trampoline.execute(listener);
                }
            else if (this instanceof final Cancelled ref)
                for (final var listener : ref.listeners) {
                    Trampoline.execute(listener);
                }
        }

        default State addListener(final Runnable listener) {
            if (this instanceof final Active ref) {
                final var newList = Stream.concat(ref.listeners.stream(), Stream.of(listener)).toList();
                return new Active(newList, ref.token);
            } else if (this instanceof final Cancelled ref) {
                final var newList = Stream.concat(ref.listeners.stream(), Stream.of(listener)).toList();
                return new Cancelled(newList);
            } else {
                return this;
            }
        }

        default State removeListener(final Runnable listener) {
            if (this instanceof final Active ref) {
                final var newList = ref.listeners.stream().filter(l -> l != listener).toList();
                return new Active(newList, ref.token);
            } else if (this instanceof final Cancelled ref) {
                final var newList = ref.listeners.stream().filter(l -> l != listener).toList();
                return new Cancelled(newList);
            } else {
                return this;
            }
        }

        State START = new Active(List.of(), null);
    }

    public static Fiber create(
        final RunnableExecuteFun execute,
        final Runnable command
    ) {
        final var fiber = new SimpleFiber();
        final var token = execute.invoke(command, fiber::signalComplete);
        fiber.registerCancel(token);
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

    public void await(final Duration timeout) throws InterruptedException, TimeoutException {
        if (!tryAcquireSharedNanos(1, timeout.toNanos())) {
            throw new TimeoutException("Timed out after " + timeout);
        }
    }
}
