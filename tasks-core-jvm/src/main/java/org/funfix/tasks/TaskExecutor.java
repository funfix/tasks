package org.funfix.tasks;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicReference;
import static org.funfix.tasks.ThreadPools.sharedIO;

@NullMarked
public interface TaskExecutor {
    RunnableFiber execute(Runnable command);

    static TaskExecutor fromThreadFactory(ThreadFactory factory) {
        return TaskExecutorDefault.fromThreadFactory(factory);
    }

    static TaskExecutor fromExecutor(Executor executor) {
        return TaskExecutorDefault.fromExecutor(executor);
    }

    static TaskExecutor shared() {
        return TaskExecutorDefault.shared();
    }
}

@NullMarked
@FunctionalInterface
interface RunnableExecuteFun {
    Cancellable invoke(Runnable command, @Nullable Runnable onComplete);
}

@NullMarked
final class TaskExecutorDefault implements TaskExecutor {
    private final RunnableExecuteFun _executeFun;

    public TaskExecutorDefault(RunnableExecuteFun execute) {
        _executeFun = execute;
    }

    public RunnableFiber execute(Runnable command) {
        return SimpleRunnableFiber.create(_executeFun, command);
    }

    public static TaskExecutor fromThreadFactory(ThreadFactory factory) {
        return new TaskExecutorDefault(new RunnableExecuteFunViaThreadFactory(factory));
    }

    public static TaskExecutor fromExecutor(Executor executor) {
        return new TaskExecutorDefault(new RunnableExecuteFunViaExecutor(executor));
    }

    @Nullable
    private static volatile TaskExecutor defaultTaskExecutorOlderJavaRef;
    @Nullable
    private static volatile TaskExecutor defaultTaskExecutorLoomRef;

    public static TaskExecutor shared() {
        if (VirtualThreads.areVirtualThreadsSupported()) {
            var ref = defaultTaskExecutorLoomRef;
            if (ref == null)
                synchronized (TaskExecutor.class) {
                    ref = defaultTaskExecutorLoomRef;
                    if (ref == null)
                        try {
                            ref = fromThreadFactory(VirtualThreads.factory());
                        } catch (VirtualThreads.NotSupportedException ignored) {
                        }
                    if (ref == null) {
                        ref = fromExecutor(sharedIO());
                    }
                    defaultTaskExecutorLoomRef = ref;
                }
            return ref;
        } else {
            var ref = defaultTaskExecutorOlderJavaRef;
            if (ref == null)
                synchronized (TaskExecutor.class) {
                    ref = defaultTaskExecutorOlderJavaRef;
                    if (ref == null) {
                        ref = fromExecutor(sharedIO());
                    }
                    defaultTaskExecutorOlderJavaRef = ref;
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
    public Cancellable invoke(Runnable command, @Nullable Runnable onComplete) {
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

    public RunnableExecuteFunViaExecutor(Executor executor) {
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
            } catch (final Exception e) {
                UncaughtExceptionHandler.logException(e);
            }

            while (true) {
                final var current = state.get();
                if (current instanceof State.Running) {
                    if (state.compareAndSet(current, State.Completed.INSTANCE)) {
                        if (onComplete != null) onComplete.run();
                        return;
                    }
                } else if (current instanceof State.Interrupting interrupting) {
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

    private void triggerCancel(AtomicReference<State> state) {
        while (true) {
            final var current = state.get();
            if (current instanceof State.NotStarted) {
                if (state.compareAndSet(current, State.Completed.INSTANCE))
                    return;
            } else if (current instanceof State.Running running) {
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
            public static NotStarted INSTANCE = new NotStarted();
        }
        record Completed() implements State {
            public static NotStarted INSTANCE = new NotStarted();
        }
        record Running(Thread thread) implements State {}
        record Interrupting(AwaitSignal wasInterrupted) implements State {}
    }
}
