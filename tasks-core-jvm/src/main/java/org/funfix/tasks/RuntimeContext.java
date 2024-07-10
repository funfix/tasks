package org.funfix.tasks;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.funfix.tasks.ThreadPools.sharedIO;
import static org.funfix.tasks.ThreadPools.sharedScheduledExecutor;

@NullMarked
public interface RuntimeContext {
    RuntimeFiber execute(Runnable command);
    Cancellable scheduleOnce(Duration delay, Runnable command);
    Cancellable scheduleAtFixedRate(Duration initialDelay, Duration period, Runnable command);
    Cancellable scheduleWithFixedDelay(Duration initialDelay, Duration delay, Runnable command);
    RuntimeClock clock();

    static RuntimeContext fromThreadFactory(ThreadFactory factory) {
        return RuntimeContextDefault.fromThreadFactory(factory);
    }

    static RuntimeContext fromThreadFactory(
            ThreadFactory factory,
            @Nullable ScheduledExecutorService scheduler,
            @Nullable RuntimeClock clock
    ) {
        return RuntimeContextDefault.fromThreadFactory(factory, scheduler, clock);
    }

    static RuntimeContext fromExecutor(Executor executor) {
        return RuntimeContextDefault.fromExecutor(executor);
    }

    static RuntimeContext fromExecutor(
            Executor executor,
            @Nullable ScheduledExecutorService scheduler,
            @Nullable RuntimeClock clock
    ) {
        return RuntimeContextDefault.fromExecutor(executor, scheduler, clock);
    }

    static RuntimeContext shared() {
        return RuntimeContextDefault.shared();
    }
}

@NullMarked
final class RuntimeContextDefault implements RuntimeContext {
    private final ScheduledExecutorService _scheduler;
    private final RuntimeClock _clock;
    private final RuntimeExecuteFun _executeFun;

    public RuntimeContextDefault(
            ScheduledExecutorService scheduler,
            RuntimeClock clock,
            RuntimeExecuteFun execute
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

    public static RuntimeContext fromThreadFactory(ThreadFactory factory) {
        return fromThreadFactory(factory, null, null);
    }

    public static RuntimeContext fromThreadFactory(
            ThreadFactory factory,
            @Nullable ScheduledExecutorService scheduler,
            @Nullable RuntimeClock clock
    ) {
        return new RuntimeContextDefault(
                scheduler != null ? scheduler : sharedScheduledExecutor(),
                clock != null ? clock : RuntimeClock.System,
                new RuntimeExecuteFunViaThreadFactory(factory)
        );
    }

    public static RuntimeContext fromExecutor(Executor executor) {
        return fromExecutor(executor, null, null);
    }

    public static RuntimeContext fromExecutor(
            Executor executor,
            @Nullable ScheduledExecutorService scheduler,
            @Nullable RuntimeClock clock
    ) {
        return new RuntimeContextDefault(
                scheduler != null ? scheduler : sharedScheduledExecutor(),
                clock != null ? clock : RuntimeClock.System,
                new RuntimeExecuteFunViaExecutor(executor)
        );
    }

    @Nullable
    private static volatile RuntimeContext defaultRuntimeContextOlderJavaRef;
    @Nullable
    private static volatile RuntimeContext defaultRuntimeContextLoomRef;

    public static RuntimeContext shared() {
        if (VirtualThreads.areVirtualThreadsSupported()) {
            var ref = defaultRuntimeContextLoomRef;
            if (ref == null)
                synchronized (RuntimeContext.class) {
                    ref = defaultRuntimeContextLoomRef;
                    if (ref == null)
                        try {
                            ref = fromThreadFactory(VirtualThreads.factory());
                        } catch (VirtualThreads.NotSupportedException ignored) {
                        }
                    if (ref == null) {
                        ref = fromExecutor(sharedIO());
                    }
                    defaultRuntimeContextLoomRef = ref;
                }
            return ref;
        } else {
            var ref = defaultRuntimeContextOlderJavaRef;
            if (ref == null)
                synchronized (RuntimeContext.class) {
                    ref = defaultRuntimeContextOlderJavaRef;
                    if (ref == null) {
                        ref = fromExecutor(sharedIO());
                    }
                    defaultRuntimeContextOlderJavaRef = ref;
                }
            return ref;
        }
    }
}

@NullMarked
final class RuntimeExecuteFunViaThreadFactory implements RuntimeExecuteFun {
    private final ThreadFactory _factory;

    public RuntimeExecuteFunViaThreadFactory(final ThreadFactory factory) {
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
final class RuntimeExecuteFunViaExecutor implements RuntimeExecuteFun {
    private final Executor _executor;

    public RuntimeExecuteFunViaExecutor(Executor executor) {
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
