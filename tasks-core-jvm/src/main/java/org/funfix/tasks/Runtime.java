package org.funfix.tasks;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

@NullMarked
public final class Runtime {
    private final ScheduledExecutorService _scheduler;
    private final RuntimeClock _clock;
    private final RuntimeExecute _executeFun;

    private Runtime(
            ScheduledExecutorService scheduler,
            RuntimeClock clock,
            RuntimeExecute execute
    ) {
        _scheduler = scheduler;
        _clock = clock;
        _executeFun = execute;
    }

    public RuntimeClock clock() {
        return _clock;
    }

    public RuntimeFiber execute(Runnable command) {
        return SimpleRuntimeFiber.create(_executeFun, command);
    }

    public Cancellable scheduleOnce(Duration delay, Runnable command) {
        final var cancellable = new MutableCancellable();
        final Runnable run = () -> {
            // Redirect to main thread-pool
            final var execToken = _executeFun.invoke(command, null);
            cancellable.setOrdered(execToken, 2);
        };
        final var token = _scheduler.schedule(run, delay.toMillis(), TimeUnit.MILLISECONDS);
        cancellable.setOrdered(() -> token.cancel(false), 1);
        return cancellable;
    }

    public Cancellable scheduleAtFixedRate(Duration initialDelay, Duration period, Runnable command) {
        final var commandCancellable = new MutableCancellable();
        final Runnable runCommand = () -> {
            // Redirect to main thread-pool
            final var execToken = _executeFun.invoke(command, null);
            commandCancellable.set(execToken);
        };
        final var token = _scheduler.scheduleAtFixedRate(
                runCommand,
                initialDelay.toMillis(),
                period.toMillis(),
                TimeUnit.MILLISECONDS
        );
        return () -> {
            token.cancel(false);
            commandCancellable.cancel();
        };
    }

    public Cancellable scheduleWithFixedDelay(Duration initialDelay, Duration delay, Runnable command) {
        final var commandCancellable = new MutableCancellable();
        final Runnable runCommand = () -> {
            // Redirect to main thread-pool
            final var execToken = _executeFun.invoke(command, null);
            commandCancellable.set(execToken);
        };
        final var token = _scheduler.scheduleWithFixedDelay(
                runCommand,
                initialDelay.toMillis(),
                delay.toMillis(),
                TimeUnit.MILLISECONDS
        );
        return () -> {
            token.cancel(false);
            commandCancellable.cancel();
        };
    }

    public static Runtime fromThreadFactory(ThreadFactory factory) {
        return fromThreadFactory(factory, null, null);
    }

    public static Runtime fromThreadFactory(
            ThreadFactory factory,
            @Nullable ScheduledExecutorService scheduler,
            @Nullable RuntimeClock clock
    ) {
        return new Runtime(
                scheduler != null ? scheduler : sharedScheduledExecutor(),
                clock != null ? clock : RuntimeClock.System,
                new RuntimeExecuteViaThreadFactory(factory)
        );
    }

    public static Runtime fromExecutor(Executor executor) {
        return fromExecutor(executor, null, null);
    }

    public static Runtime fromExecutor(
            Executor executor,
            @Nullable ScheduledExecutorService scheduler,
            @Nullable RuntimeClock clock
    ) {
        return new Runtime(
                scheduler != null ? scheduler : sharedScheduledExecutor(),
                clock != null ? clock : RuntimeClock.System,
                new RuntimeExecuteViaExecutor(executor)
        );
    }


    @Nullable
    private static volatile ScheduledExecutorService _sharedScheduledExecutorRef;
    private static ScheduledExecutorService sharedScheduledExecutor() {
        var ref = _sharedScheduledExecutorRef;
        if (ref == null) {
            synchronized (Runtime.class) {
                ref = _sharedScheduledExecutorRef;
                if (ref == null) {
                    final var poolSize = Math.max(1, java.lang.Runtime.getRuntime().availableProcessors());
                    ref = _sharedScheduledExecutorRef =
                            Executors.newScheduledThreadPool(
                                    poolSize,
                                    r -> {
                                        final var t = new Thread(r);
                                        t.setName("io-shared-scheduler-" + t.getId());
                                        t.setDaemon(true);
                                        return t;
                                    }
                            );
                }
            }
        }
        return ref;
    }
}

@NullMarked
final class RuntimeExecuteViaThreadFactory implements RuntimeExecute {
    private final ThreadFactory _factory;

    public RuntimeExecuteViaThreadFactory(final ThreadFactory factory) {
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
final class RuntimeExecuteViaExecutor implements RuntimeExecute {
    private final Executor _executor;

    public RuntimeExecuteViaExecutor(Executor executor) {
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
