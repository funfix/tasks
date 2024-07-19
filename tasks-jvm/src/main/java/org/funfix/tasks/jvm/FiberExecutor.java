package org.funfix.tasks.jvm;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicReference;
import static org.funfix.tasks.jvm.ThreadPools.sharedIO;

@NullMarked
public interface FiberExecutor extends Executor {
    Cancellable executeCancellable(Runnable command, @Nullable Runnable onComplete);
    Fiber executeFiber(Runnable command);

    default Cancellable executeCancellable(final Runnable command) {
        return executeCancellable(command, null);
    }

    static FiberExecutor fromThreadFactory(final ThreadFactory factory) {
        return FiberExecutorDefault.fromThreadFactory(factory);
    }

    static FiberExecutor fromExecutor(final Executor executor) {
        return FiberExecutorDefault.fromExecutor(executor);
    }

    static FiberExecutor shared() {
        return FiberExecutorDefault.shared();
    }
}

@NullMarked
@FunctionalInterface
interface RunnableExecuteFun {
    Cancellable invoke(Runnable command, @Nullable Runnable onComplete);
}

@NullMarked
final class FiberExecutorDefault implements FiberExecutor {
    private final Executor _executor;
    private final RunnableExecuteFun _executeFun;

    public FiberExecutorDefault(
        final Executor executor,
        final RunnableExecuteFun execute
    ) {
        _executor = executor;
        _executeFun = execute;
    }

    @Override
    public void execute(final Runnable command) {
        _executor.execute(command);
    }

    @Override
    public Cancellable executeCancellable(final Runnable command, @Nullable final Runnable onComplete) {
        return _executeFun.invoke(command, onComplete);
    }

    @Override
    public Fiber executeFiber(final Runnable command) {
        return SimpleFiber.create(_executeFun, command);
    }

    public static FiberExecutor fromThreadFactory(final ThreadFactory factory) {
        final Executor executor = (command) -> {
            final var t = factory.newThread(command);
            t.start();
        };
        return new FiberExecutorDefault(
                executor,
                new RunnableExecuteFunViaThreadFactory(factory)
        );
    }

    public static FiberExecutor fromExecutor(final Executor executor) {
        return new FiberExecutorDefault(
                executor,
                new RunnableExecuteFunViaExecutor(executor)
        );
    }

    @Nullable
    private static volatile FiberExecutor defaultFiberExecutorOlderJavaRef;
    @Nullable
    private static volatile FiberExecutor defaultFiberExecutorLoomRef;

    public static FiberExecutor shared() {
        if (VirtualThreads.areVirtualThreadsSupported()) {
            var ref = defaultFiberExecutorLoomRef;
            if (ref == null)
                synchronized (FiberExecutor.class) {
                    ref = defaultFiberExecutorLoomRef;
                    if (ref == null)
                        try {
                            ref = fromThreadFactory(VirtualThreads.factory());
                        } catch (final VirtualThreads.NotSupportedException ignored) {
                        }
                    if (ref == null) {
                        ref = fromExecutor(sharedIO());
                    }
                    defaultFiberExecutorLoomRef = ref;
                }
            return ref;
        } else {
            var ref = defaultFiberExecutorOlderJavaRef;
            if (ref == null)
                synchronized (FiberExecutor.class) {
                    ref = defaultFiberExecutorOlderJavaRef;
                    if (ref == null) {
                        ref = fromExecutor(sharedIO());
                    }
                    defaultFiberExecutorOlderJavaRef = ref;
                }
            return ref;
        }
    }
}

@NullMarked
final class RunnableExecuteFunViaThreadFactory implements RunnableExecuteFun {
    private final ThreadFactory _factory;

    public RunnableExecuteFunViaThreadFactory(final ThreadFactory factory) {
        _factory = factory;
    }

    @Override
    public Cancellable invoke(final Runnable command, @Nullable final Runnable onComplete) {
        final var t = _factory.newThread(() -> {
            try {
                command.run();
            } finally {
                if (onComplete != null)
                    onComplete.run();
            }
        });
        t.start();
        return t::interrupt;
    }
}

@NullMarked
final class RunnableExecuteFunViaExecutor implements RunnableExecuteFun {
    private final Executor _executor;

    public RunnableExecuteFunViaExecutor(final Executor executor) {
        _executor = executor;
    }

    @Override
    public Cancellable invoke(final Runnable command, final @Nullable Runnable onComplete) {
        final var state = new AtomicReference<State>(State.NotStarted.INSTANCE);
        _executor.execute(() -> {
            final var running = new State.Running(Thread.currentThread());
            if (!state.compareAndSet(State.NotStarted.INSTANCE, running)) {
                if (onComplete != null) onComplete.run();
                return;
            }
            try {
                command.run();
            } catch (final Throwable e) {
                UncaughtExceptionHandler.logOrRethrowException(e);
            }

            while (true) {
                final var current = state.get();
                if (current instanceof State.Running) {
                    if (state.compareAndSet(current, State.Completed.INSTANCE)) {
                        if (onComplete != null) onComplete.run();
                        return;
                    }
                } else if (current instanceof final State.Interrupting interrupting) {
                    try {
                        interrupting.wasInterrupted.await();
                    } catch (final InterruptedException ignored) {
                    } finally {
                        if (onComplete != null) onComplete.run();
                    }
                    return;
                } else {
                    throw new IllegalStateException("Invalid state: " + current);
                }
            }
        });
        return () -> triggerCancel(state);
    }

    private void triggerCancel(final AtomicReference<State> state) {
        while (true) {
            final var current = state.get();
            if (current instanceof State.NotStarted) {
                if (state.compareAndSet(current, State.Completed.INSTANCE))
                    return;
            } else if (current instanceof final State.Running running) {
                // Starts the interruption process
                final var wasInterrupted = new AwaitSignal();
                if (state.compareAndSet(current, new State.Interrupting(wasInterrupted))) {
                    running.thread.interrupt();
                    wasInterrupted.signal();
                    return;
                }
            } else {
                return;
            }
        }
    }

    sealed interface State {
        record NotStarted() implements State {
            public static final NotStarted INSTANCE = new NotStarted();
        }
        record Completed() implements State {
            public static final NotStarted INSTANCE = new NotStarted();
        }
        record Running(Thread thread) implements State {}
        record Interrupting(AwaitSignal wasInterrupted) implements State {}
    }
}

