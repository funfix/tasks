package org.funfix.tasks;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;
import java.util.function.Function;
import java.util.stream.Stream;

@NullMarked
public interface RuntimeFiber extends Cancellable {
    Cancellable joinAsync(Runnable onComplete);

    default void joinTimed(Duration timeout) throws InterruptedException, TimeoutException {
        final var latch = new AwaitSignal();
        final var token = joinAsync(latch::signal);
        try {
            latch.await(timeout);
        } finally {
            token.cancel();
        }
    }

    default void join() throws InterruptedException {
        final var latch = new AwaitSignal();
        final var token = joinAsync(latch::signal);
        try {
            latch.await();
        } finally {
            token.cancel();
        }
    }

    default CancellableFuture<@Nullable Void> joinAsync() {
        final var  p = new CompletableFuture<@Nullable Void>();
        final var token = joinAsync(() -> p.complete(null));
        final Cancellable cRef = () -> {
            try { token.cancel(); }
            finally { p.cancel(false); }
        };
        return new CancellableFuture<>(cRef, p);
    }
}

@NullMarked
final class SimpleRuntimeFiber implements RuntimeFiber {
    private final AtomicReference<State> stateRef = new AtomicReference<>(State.START);

    @Override
    public Cancellable joinAsync(Runnable onComplete) {
        while (true) {
            final var current = stateRef.get();
            if (current instanceof State.Active || current instanceof State.Cancelled) {
                final var update = current.addListener(onComplete);
                if (stateRef.compareAndSet(current, update))
                    return removeListener(onComplete);
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
            if (current instanceof State.Active active) {
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

    public void registerCancel(Cancellable token) {
        while (true) {
            final var current = stateRef.get();
            if (current instanceof State.Active active) {
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

    private Cancellable removeListener(Runnable listener) {
        return () -> {
            while (true) {
                final var current = stateRef.get();
                if (current instanceof State.Active || current instanceof State.Cancelled) {
                    final var update = current.removeListener(listener);
                    if (stateRef.compareAndSet(current, update))
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
            if (this instanceof Active ref)
                for (var listener : ref.listeners) {
                    Trampoline.execute(listener);
                }
            else if (this instanceof Cancelled ref)
                for (var listener : ref.listeners) {
                    Trampoline.execute(listener);
                }
        }

        default State addListener(Runnable listener) {
            if (this instanceof Active ref) {
                final var newList = Stream.concat(ref.listeners.stream(), Stream.of(listener)).toList();
                return new Active(newList, ref.token);
            } else if (this instanceof Cancelled ref) {
                final var newList = Stream.concat(ref.listeners.stream(), Stream.of(listener)).toList();
                return new Cancelled(newList);
            } else {
                return this;
            }
        }

        default State removeListener(Runnable listener) {
            if (this instanceof Active ref) {
                final var newList = ref.listeners.stream().filter(l -> l != listener).toList();
                return new Active(newList, ref.token);
            } else if (this instanceof Cancelled ref) {
                final var newList = ref.listeners.stream().filter(l -> l != listener).toList();
                return new Cancelled(newList);
            } else {
                return this;
            }
        }

        State START = new Active(List.of(), Cancellable.EMPTY);
    }

    public static RuntimeFiber create(
            RuntimeExecute execute,
            Runnable command
    ) {
        final var fiber = new SimpleRuntimeFiber();
        final var token = execute.invoke(command, fiber::signalComplete);
        fiber.registerCancel(token);
        return fiber;
    }
}

@NullMarked
final class AwaitSignal extends AbstractQueuedSynchronizer {
    @Override
    protected int tryAcquireShared(int arg) {
        return getState() != 0 ? 1 : -1;
    }

    @Override
    protected boolean tryReleaseShared(int arg) {
        setState(1);
        return true;
    }

    public void signal() {
        releaseShared(1);
    }

    public void await() throws InterruptedException {
        acquireSharedInterruptibly(1);
    }

    public void await(Duration timeout) throws InterruptedException, TimeoutException {
        if (!tryAcquireSharedNanos(1, timeout.toNanos())) {
            throw new TimeoutException("Timed out after " + timeout);
        }
    }
}
