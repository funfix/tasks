package org.funfix.tasks;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.Serializable;
import java.time.Duration;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;
import java.util.function.Function;
import java.util.stream.Stream;


/**
 * Represents a function that will execute a job asynchronously.
 */
@NullMarked
public abstract class Task<T> implements Serializable {
    /**
     * Executes the task asynchronously.
     *
     * @param listener is the callback that will be invoked when the task completes
     *
     * @return a {@link Cancellable} that can be used to cancel a running task
     */
    abstract Cancellable executeAsync(CompletionListener<? super T> listener);

    /**
     * Executes the task concurrently and returns a [Fiber] that can be
     * used to wait for the result or cancel the task.
     */
    final Fiber<T> executeConcurrently() {
        final var fiber = new TaskFiber<T>();
        final var token = executeAsync(fiber.onComplete);
        fiber.registerCancel(token);
        return fiber;
    }

    /**
     * Executes the task and blocks until it completes, or the current
     * thread gets interrupted (in which case the task is also cancelled).
     *
     * @throws ExecutionException if the task fails with an exception
     *
     * @throws InterruptedException if the current thread is interrupted,
     *         which also cancels the running task. Note that on interruption,
     *         the running concurrent task must also be interrupted, as this method
     *         always blocks for its interruption or completion.
     */
    final T executeBlocking() throws ExecutionException, InterruptedException {
        final var h = new BlockingCompletionListener<T>();
        final var cancelToken = executeAsync(h);
        return h.await(cancelToken);
    }

    /**
     * Executes the task and blocks until it completes, or the timeout is reached,
     * or the current thread is interrupted.
     *
     * @throws ExecutionException if the task fails with an exception
     *
     * @throws InterruptedException if the current thread is interrupted.
     *         The running task is also cancelled, and this method does not
     *         return until `onCancel` is signaled.
     *
     * @throws TimeoutException if the task doesn't complete within the
     *         specified timeout. The running task is also cancelled on timeout,
     *         and this method does not returning until `onCancel` is signaled.
     */
    final T executeBlockingTimed(final Duration timeout)
        throws ExecutionException, InterruptedException, TimeoutException {

        final var h = new BlockingCompletionListener<T>();
        final var cancelToken = executeAsync(h);
        return h.await(cancelToken, timeout);
    }

    /**
     * Creates a task from a builder function that, on evaluation, will have
     * same-thread execution.
     * <p>
     * Compared with creating a new task instance directly, this method ensures:
     * <ol>
     *     <li>Safe/idempotent use of the listener</li>
     *     <li>Idempotent cancellation</li>
     *     <li>Trampolined execution to avoid stack-overflows</li>
     * </ol>
     * </p><p>
     * The created task will execute the given function on the current thread, by
     * using a "trampoline" to avoid stack overflows. This may be useful if the
     * computation for initiating the async process is expected to be fast. However,
     * if the computation can block the current thread, it is recommended to use
     * {@link #createAsync(Function)} instead, which will initiate the computation
     * on a separate thread.
     * </p>
     *
     * @param builder is the function that will trigger the async computation,
     *                receiving a callback that will be used to signal the result.
     *
     * @return a new task that will execute the given builder function upon execution
     *
     * @see #createAsync(Function)
     * @see #createAsync(Executor, Function)
     * @see #createAsync(ThreadFactory, Function)
     */
    public static <T> Task<T> create(
        final Function<CompletionListener<? super T>, Cancellable> builder
    ) {
        return new CreateAsyncTask<>(builder, null, null);
    }

    /**
     * Creates a task that, when executed, will execute the given asynchronous
     * computation starting from a separate thread.
     * <p>
     * This is a variant of {@link #create(Function)}, which executes the given
     * builder function on the same thread.
     *
     * @param es is the {@link Executor} to use for starting the async computation
     * @param builder is the function that will trigger the async computation
     * @return a new task that will execute the given builder function
     *
     * @see #create(Function)
     * @see #createAsync(Function)
     */
    public static <T> Task<T> createAsync(
        final Executor es,
        final Function<CompletionListener<? super T>, Cancellable> builder
    ) {
        return new CreateAsyncTask<>(builder, es, null);
    }

    /**
     * Creates a task that, when executed, will execute the given asynchronous
     * computation starting from a separate thread.
     * <p>
     * This is a simplified version of {@link #createAsync(Executor, Function)}
     * that uses {@link ThreadPools#sharedIO()} as the common thread-pool. This
     * will use virtual threads on Java 21+.
     *
     * @param builder is the function that will trigger the async computation
     * @return a new task that will execute the given builder function
     *
     * @see #create(Function)
     * @see #createAsync(Executor, Function)
     */
    public static <T> Task<T> createAsync(
        final Function<CompletionListener<? super T>, Cancellable> builder
    ) {
        // Optimization for virtual threads — uses virtual threads directly, as
        // usage of an executor may require extra synchronization.
        if (VirtualThreads.areVirtualThreadsSupported())
            try {
                return createAsync(VirtualThreads.factory(), builder);
            } catch (final VirtualThreads.NotSupportedException ignored) {
            }
        return createAsync(ThreadPools.sharedIO(), builder);
    }

    /**
     * Creates a task that, when executed, will execute the given asynchronous
     * computation starting from a separate thread.
     * <p>
     * This is a variant of {@link #createAsync(Executor, Function)} that will
     * use the given {@link ThreadFactory} for creating the thread, instead of
     * using an {@link Executor}. The advantage of this method may be performance,
     * in case virtual threads are supported, since there may be less synchronization
     * overhead.
     * </p>
     *
     * @param factory is the {@link ThreadFactory} to use for creating the thread
     * @param builder is the function that will trigger the async computation
     * @return a new task that will execute the given builder function on a separate thread
     */
    public static <T> Task<T> createAsync(
        final ThreadFactory factory,
        final Function<CompletionListener<? super T>, Cancellable> builder
    ) {
        return new CreateAsyncTask<>(builder, null, factory);
    }

    /**
     * Creates a task from a {@link Callable} executing blocking IO.
     * <p>
     * Similar to {@link #fromBlockingIO(Executor, Callable)}, but uses
     * the common thread-pool defined by {@link ThreadPools#sharedIO()},
     * which is using virtual threads on Java 21+.
     * <p>
     * See {@link #fromBlockingIO(Executor, Callable)} for full details.
     *
     * @param callable is the blocking IO operation to execute
     * @return a new task that will perform the blocking IO operation upon execution
     *
     * @see #fromBlockingIO(Executor, Callable)
     */
    public static <T> Task<T> fromBlockingIO(final Callable<T> callable) {
        // Optimization for virtual threads — uses virtual threads directly,
        // as usage of an executor requires extra synchronization.
        if (VirtualThreads.areVirtualThreadsSupported())
            try {
                return fromBlockingIO(VirtualThreads.factory(), callable);
            } catch (final VirtualThreads.NotSupportedException ignored) {
            }
        // Default to using the shared IO thread-pool
        return fromBlockingIO(ThreadPools.sharedIO(), callable);
    }

    /**
     * Creates a task from a {@link Callable} executing blocking IO
     * asynchronously (on another thread).
     * <p>
     * The given blocking function can listen to Java's interruption
     * protocol (e.g., {@link Thread#interrupt()}), with the resulting task
     * being cancellable.
     *
     * @param es is the {@link Executor} to use for executing the blocking IO operation
     * @param callable is the blocking IO operation to execute
     * @return a new task that will perform the blocking IO operation upon execution
     */
    public static <T> Task<T> fromBlockingIO(final Executor es, final Callable<T> callable) {
        return new TaskFromExecutor<>(es, callable);
    }

    /**
     * Creates a task from a {@link Callable} executing blocking IO, using a
     * different thread created by the given {@link ThreadFactory}.
     *
     * @param factory is the {@link ThreadFactory} to use for creating the thread
     * @param callable is the blocking IO operation to execute
     * @return a new task that will perform the blocking IO operation upon execution
     *
     * @see #fromBlockingIO(Executor, Callable)
     * @see #fromBlockingIO(Callable)
     */
    public static <T> Task<T> fromBlockingIO(final ThreadFactory factory, final Callable<T> callable) {
        return new TaskFromThreadFactory<>(factory, callable);
    }

    /**
     * Creates a task from a {@link Future} builder.
     * <p>
     * This is similar to {@link #fromBlockingFuture(Executor, Callable)}, but uses
     * the common thread-pool defined by {@link ThreadPools#sharedIO()}, which is
     * using virtual threads on Java 21+.
     *
     * @param builder is the {@link Callable} that will create the {@link Future} upon
     *                this task's execution.
     * @return a new task that will complete with the result of the created {@code Future}
     *        upon execution
     */
    public static <T> Task<T> fromBlockingFuture(final Callable<Future<? extends T>> builder) {
        return fromBlockingFuture(ThreadPools.sharedIO(), builder);
    }

    /**
     * Creates a task from a {@link Future} builder.
     * <p>
     * This is compatible with Java's interruption protocol and
     * {@link Future#cancel(boolean)}, with the resulting task being cancellable.
     * <p>
     * The given {@link Executor} is used for executing the blocking IO operation
     * (i.e., {@link Future#get()}). Note that blocking threads in this way is
     * perfectly acceptable on top of virtual threads (if supported by the JVM).
     * <p>
     * <strong>NOTE:</strong> Use {@link #fromCompletionStage(Callable)} for directly
     * converting {@link CompletableFuture} builders, because it is not possible to cancel
     * such values, and the logic needs to reflect it. Better yet, use
     * {@link #fromCancellableCompletionStage(Callable)} for working with {@link CompletionStage}
     * values that can be cancelled.
     *
     * @param builder is the {@link Callable} that will create the {@link Future} upon
     *                this task's execution.
     * @return a new task that will complete with the result of the created {@code Future}
     *        upon execution
     *
     * @see #fromCompletionStage(Callable)
     * @see #fromCancellableCompletionStage(Callable)
     */
    public static <T> Task<T> fromBlockingFuture(final Executor es, final Callable<Future<? extends T>> builder) {
        return fromBlockingIO(es, () -> {
            final var f = builder.call();
            try {
                return f.get();
            } catch (final InterruptedException e) {
                f.cancel(true);
                // We need to wait for this future to complete, as we need to
                // back-pressure on its interruption.
                while (!f.isDone()) {
                    // Ignore further interruption signals
                    //noinspection ResultOfMethodCallIgnored
                    Thread.interrupted();
                    try { f.get(); }
                    catch (final Exception ignored) {}
                }
                throw e;
            }
        });
    }

    /**
     * Creates tasks from a builder of {@link CompletionStage}.
     * <p>
     * <strong>NOTE:</strong> {@code CompletionStage} isn't cancellable, and the
     * resulting task should reflect this (i.e., on cancellation, the listener should not
     * receive an `onCancel` signal until the `CompletionStage` actually completes).
     * <p>
     * Prefer using {@link #fromCancellableCompletionStage(Callable)} for working with
     * {@link CompletionStage} values that can be cancelled.
     *
     * @see #fromCancellableCompletionStage(Callable)
     *
     * @param builder is the {@link Callable} that will create the {@link CompletionStage}
     *                value. It's a builder because {@link Task} values are cold values
     *                (lazy, not executed yet).
     * @return a new task that upon execution will complete with the result of
     * the created {@code CancellableCompletionStage}
     */
    public static <T> Task<T> fromCompletionStage(final Callable<CompletionStage<? extends T>> builder) {
        return fromCancellableCompletionStage(
            () -> new CancellableCompletionStage<>(builder.call(), Cancellable.EMPTY)
        );
    }

    /**
     * Creates tasks from a builder of {@link CancellableCompletionStage}.
     * <p>
     * This is the recommended way to work with {@link CompletionStage} builders,
     * because cancelling such values (e.g., {@link CompletableFuture}) doesn't work
     * for cancelling the connecting computation. As such, the user should provide
     * an explicit {@link Cancellable} token that can be used.
     *
     * @param builder is the {@link Callable} that will create the {@link CancellableCompletionStage}
     *                value. It's a builder because {@link Task} values are cold values
     *                (lazy, not executed yet).
     *
     * @return a new task that upon execution will complete with the result of
     * the created {@code CancellableCompletionStage}
     */
    public static <T> Task<T> fromCancellableCompletionStage(final Callable<CancellableCompletionStage<? extends T>> builder) {
        return new TaskFromCompletionStage<>(builder);
    }

    /**
     * Creates a task that will complete, successfully, with the given value.
     *
     * @param value is the value to complete the task with
     * @return a new task that will complete with the given value
     */
    @NullMarked
    public static <T> Task<T> successful(final T value) {
        return new Task<>() {
            @Override
            Cancellable executeAsync(final CompletionListener<? super T> listener) {
                Trampoline.execute(() -> listener.onSuccess(value));
                return Cancellable.EMPTY;
            }
        };
    }

    /**
     * Creates a task that will complete with the given exception.
     *
     * @param e is the exception to complete the task with
     * @return a new task that will complete with the given exception
     */
    @NullMarked
    public static <T> Task<T> failed(final Throwable e) {
        return new Task<>() {
            @Override
            Cancellable executeAsync(final CompletionListener<? super T> listener) {
                Trampoline.execute(() -> listener.onFailure(e));
                return Cancellable.EMPTY;
            }
        };
    }

    /**
     * Creates a task that will complete with a cancellation signal.
     *
     * @return a new task that will complete with a cancellation signal
     */
    @NullMarked
    public static <T> Task<T> cancelled() {
        return new Task<>() {
            @Override
            Cancellable executeAsync(final CompletionListener<? super T> listener) {
                Trampoline.execute(listener::onCancel);
                return Cancellable.EMPTY;
            }
        };
    }
}

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

@NullMarked
final class CreateAsyncTask<T> extends Task<T> {
    private final Function<CompletionListener<? super T>, Cancellable> builder;
    private final Executor es;

    public CreateAsyncTask(
        final Function<CompletionListener<? super T>, Cancellable> builder,
        @Nullable final Executor es,
        @Nullable final ThreadFactory factory
    ) {
        this.builder = builder;
        if (es != null) {
            this.es = es;
        } else if (factory != null) {
            this.es = command -> factory.newThread(command).start();
        } else {
            this.es = Trampoline.instance;
        }
    }

    private static final class CreateCancellable implements Cancellable {
        private final AtomicReference<@Nullable Cancellable> ref =
            new AtomicReference<>(Cancellable.EMPTY);

        public void set(final Cancellable token) {
            if (!ref.compareAndSet(Cancellable.EMPTY, token)) {
                Trampoline.execute(token::cancel);
            }
        }

        @Override
        public void cancel() {
            final var current = ref.getAndSet(null);
            if (current != null) {
                current.cancel();
            }
        }
    }

    @Override
    Cancellable executeAsync(final CompletionListener<? super T> listener) {
        final var cancel = new CreateCancellable();
        final Runnable run = () -> {
            try {
                final var token = builder.apply(listener);
                cancel.set(token);
            } catch (final Throwable e) {
                UncaughtExceptionHandler.logException(e);
            }
        };
        this.es.execute(() -> cancel.set(builder.apply(listener)));
        return cancel;
    }
}

@NullMarked
final class BlockingCompletionListener<T> extends AbstractQueuedSynchronizer implements CompletionListener<T> {
    private final AtomicBoolean isDone = new AtomicBoolean(false);
    @Nullable
    private T result = null;
    @Nullable
    private Throwable error = null;
    @Nullable
    private InterruptedException interrupted = null;

    @Override
    public void onSuccess(final T value) {
        if (!isDone.getAndSet(true)) {
            result = value;
            releaseShared(1);
        }
    }

    @Override
    public void onFailure(final Throwable e) {
        if (!isDone.getAndSet(true)) {
            error = e;
            releaseShared(1);
        }
    }

    @Override
    public void onCancel() {
        if (!isDone.getAndSet(true)) {
            interrupted = new InterruptedException("Task was cancelled");
            releaseShared(1);
        }
    }

    @Override
    protected int tryAcquireShared(final int arg) {
        return getState() != 0 ? 1 : -1;
    }

    @Override
    protected boolean tryReleaseShared(final int arg) {
        setState(1);
        return true;
    }

    @FunctionalInterface
    interface AwaitFunction {
        void apply(boolean isNotCancelled) throws InterruptedException, TimeoutException;
    }

    private T awaitInline(final Cancellable cancelToken, final AwaitFunction await)
        throws InterruptedException, ExecutionException, TimeoutException {

        var isNotCancelled = true;
        TimeoutException timedOut = null;
        while (true) {
            try {
                await.apply(isNotCancelled);
                break;
            } catch (final TimeoutException e) {
                if (isNotCancelled) {
                    isNotCancelled = false;
                    timedOut = e;
                    cancelToken.cancel();
                }
            }
            // Clearing the interrupted flag may not be necessary,
            // but doesn't hurt, and we should have a cleared flag before
            // re-throwing the exception
            //
            // noinspection ResultOfMethodCallIgnored
            Thread.interrupted();
        }
        if (timedOut != null) throw timedOut;
        if (interrupted != null) throw interrupted;
        if (error != null) throw new ExecutionException(error);
        // noinspection DataFlowIssue
        return result;
    }

    public T await(final Cancellable cancelToken) throws InterruptedException, ExecutionException {
        try {
            return awaitInline(cancelToken, firstAwait -> acquireSharedInterruptibly(1));
        } catch (final TimeoutException e) {
            throw new IllegalStateException("Unexpected timeout", e);
        }
    }

    public T await(final Cancellable cancelToken, final Duration timeout)
        throws ExecutionException, InterruptedException, TimeoutException {

        return awaitInline(cancelToken, isNotCancelled -> {
            if (isNotCancelled) {
                if (!tryAcquireSharedNanos(1, timeout.toNanos())) {
                    throw new TimeoutException("Task timed-out after " + timeout);
                }
            } else {
                // Waiting without a timeout, since at this point it's waiting
                // on the cancelled task to finish
                acquireSharedInterruptibly(1);
            }
        });
    }
}

@NullMarked
final class TaskFiber<T> implements Fiber<T> {
    @NullMarked
    private sealed interface State<T> permits State.Active, State.Cancelled, State.Completed {
        record Active<T>(List<Runnable> listeners, @Nullable Cancellable token)
            implements State<T> {}
        record Cancelled<T>(List<Runnable> listeners)
            implements State<T> {}
        record Completed<T>(Outcome<T> outcome)
            implements State<T> {}

        default State<T> addListener(final Runnable listener) {
            if (this instanceof final Active<T> active) {
                final var newList = Stream
                    .concat(active.listeners.stream(), Stream.of(listener))
                    .toList();
                return new Active<>(newList, active.token);
            } else if (this instanceof final Cancelled<T> cancelled) {
                final var newList = Stream
                    .concat(cancelled.listeners.stream(), Stream.of(listener))
                    .toList();
                return new Cancelled<>(newList);
            } else {
                return this;
            }
        }

        default State<T> removeListener(final Runnable listener) {
            if (this instanceof final Active<T> active) {
                final var newList = active.listeners.stream()
                    .filter(it -> it != listener)
                    .toList();
                return new Active<>(newList, active.token);
            } else if (this instanceof final Cancelled<T> cancelled) {
                final var newList = cancelled.listeners.stream()
                    .filter(it -> it != listener)
                    .toList();
                return new Cancelled<>(newList);
            } else {
                return this;
            }
        }

        static <T> State<T> start() {
            return new Active<>(List.of(), null);
        }
    }

    private final AtomicReference<State<T>> ref =
        new AtomicReference<>(State.start());

    public final CompletionListener<T> onComplete = new CompletionListener<>() {
        @Override
        public void onSuccess(final T value) {
            signalComplete(Outcome.success(value));
        }

        @Override
        public void onFailure(final Throwable e) {
            signalComplete(Outcome.failure(e));
        }

        @Override
        public void onCancel() {
            signalComplete(Outcome.cancelled());
        }
    };

    void registerCancel(final Cancellable token) {
        while (true) {
            final var current = ref.get();
            if (current instanceof final State.Active<T> active) {
                final var update = new State.Active<T>(active.listeners, token);
                if (active.token != null) {
                    throw new IllegalStateException("Token already registered");
                } else if (ref.compareAndSet(current, update)) {
                    return;
                }
            } else if (current instanceof State.Cancelled<T>) {
                token.cancel();
                return;
            } else if (current instanceof State.Completed<T>) {
                return;
            } else {
                throw new IllegalStateException("Unexpected state: " + current);
            }
        }
    }

    @Override
    public void cancel() {
        while (true) {
            final var current = ref.get();
            if (current instanceof final State.Active<T> active) {
                final var update = new State.Cancelled<T>(active.listeners);
                if (ref.compareAndSet(current, update)) {
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

    @Override
    public @Nullable Outcome<T> outcome() {
        final var current = ref.get();
        if (current instanceof final State.Completed<T> completed) {
            return completed.outcome;
        } else {
            return null;
        }
    }

    @Override
    public void joinBlocking() throws InterruptedException {
        final var latch = new AwaitSignal();
        final Runnable runnable = latch::signal;
        final var token = joinAsync(runnable);
        try {
            latch.await();
        } catch (final InterruptedException e) {
            token.cancel();
            throw e;
        }
    }

    @Override
    public Cancellable joinAsync(final Runnable onComplete) {
        while (true) {
            final var current = ref.get();
            if (current instanceof State.Active<T> || current instanceof State.Cancelled<T>) {
                final var update = current.addListener(onComplete);
                if (ref.compareAndSet(current, update)) {
                    return cancelJoinAsync(onComplete);
                }
            } else if (current instanceof State.Completed<T>) {
                Trampoline.execute(onComplete);
                return Cancellable.EMPTY;
            } else {
                throw new IllegalStateException("Unexpected state: " + current);
            }
        }
    }

    private Cancellable cancelJoinAsync(final Runnable onComplete) {
        return () -> {
            while (true) {
                final var current = ref.get();
                final var update = current.removeListener(onComplete);
                if (ref.compareAndSet(current, update)) {
                    return;
                }
            }
        };
    }

    private void signalComplete(final Outcome<T> outcome) {
        final var update = new State.Completed<>(outcome);
        while (true) {
            final var current = ref.get();
            if (current instanceof final State.Active<T> active) {
                if (ref.compareAndSet(current, update)) {
                    for (final var listener : active.listeners) {
                        Trampoline.execute(listener);
                    }
                }
            } else if (current instanceof final State.Cancelled<T> cancelled) {
                if (ref.compareAndSet(current, update)) {
                    for (final var listener : cancelled.listeners) {
                        Trampoline.execute(listener);
                    }
                }
            } else if (current instanceof State.Completed<T>) {
                return;
            } else {
                throw new IllegalStateException("Unexpected state: " + current);
            }
        }
    }
}

@NullMarked
final class Trampoline {
    private Trampoline() {}

    private static final ThreadLocal<@Nullable LinkedList<Runnable>> queue =
        new ThreadLocal<>();

    private static void eventLoop() {
        while (true) {
            final var current = queue.get();
            if (current == null) {
                return;
            }
            final var next = current.pollFirst();
            if (next == null) {
                return;
            }
            try {
                next.run();
            } catch (final Exception e) {
                UncaughtExceptionHandler.logException(e);
            }
        }
    }

    public static final Executor instance =
        command -> {
            var current = queue.get();
            if (current == null) {
                current = new LinkedList<>();
                current.add(command);
                queue.set(current);
                try {
                    eventLoop();
                } finally {
                    queue.remove();
                }
            } else {
                current.add(command);
            }
        };

    public static void execute(final Runnable command) {
        instance.execute(command);
    }
}

@NullMarked
final class TaskFromThreadFactory<T> extends Task<T> {
    private final ThreadFactory factory;
    private final Callable<? extends T> callable;

    public TaskFromThreadFactory(final ThreadFactory factory, final Callable<? extends T> callable) {
        this.factory = factory;
        this.callable = callable;
    }

    @Override
    Cancellable executeAsync(final CompletionListener<? super T> listener) {
        final var thread = this.factory.newThread(new TaskRunnable(listener));
        thread.start();
        return thread::interrupt;
    }

    private final class TaskRunnable implements Runnable {
        private final CompletionListener<? super T> listener;

        public TaskRunnable(final CompletionListener<? super T> listener) {
            this.listener = listener;
        }

        @Override
        public void run() {
            var isUserError = true;
            try {
                final var result = callable.call();
                isUserError = false;
                listener.onSuccess(result);
            } catch (final InterruptedException | CancellationException e) {
                listener.onCancel();
            } catch (final Exception e) {
                if (isUserError) listener.onFailure(e);
                else UncaughtExceptionHandler.logException(e);
            }
        }
    }
}

@NullMarked
final class TaskFromExecutor<T> extends Task<T> {
    private final Executor es;
    private final Callable<? extends T> callable;

    private sealed interface State extends Serializable
        permits State.NotStarted, State.Completed, State.Running, State.Interrupting {

        final class NotStarted implements State {
            static final NotStarted INSTANCE = new NotStarted();
            private NotStarted() {}
        }

        final class Completed implements State {
            static final Completed INSTANCE = new Completed();
            private Completed() {}
        }

        record Running(Thread thread) implements State {}

        record Interrupting(AwaitSignal wasInterrupted) implements State {}
    }

    public TaskFromExecutor(final Executor es, final Callable<? extends T> callable) {
        this.es = es;
        this.callable = callable;
    }

    private void triggerCancel(final AtomicReference<State> state) {
        while (true) {
            final var current = state.get();
            if (current instanceof State.NotStarted) {
                if (state.compareAndSet(current, State.Completed.INSTANCE)) {
                    return;
                }
            } else if (current instanceof final State.Running running) {
                final var wasInterrupted = new AwaitSignal();
                if (state.compareAndSet(current, new State.Interrupting(wasInterrupted))) {
                    running.thread.interrupt();
                    wasInterrupted.signal();
                    return;
                }
            } else if (current instanceof State.Completed || current instanceof State.Interrupting) {
                return;
            } else {
                throw new IllegalStateException("Unexpected state: " + current);
            }
        }
    }

    @Override
    public Cancellable executeAsync(final CompletionListener<? super T> onComplete) {
        final var state = new AtomicReference<State>(State.NotStarted.INSTANCE);
        es.execute(() -> {
            if (!state.compareAndSet(State.NotStarted.INSTANCE, new State.Running(Thread.currentThread()))) {
                onComplete.onCancel();
                return;
            }

            @SuppressWarnings("DataFlowIssue")
            T result = null;
            Throwable error = null;
            var interrupted = false;
            try {
                result = callable.call();
            } catch (final InterruptedException | CancellationException e) {
                interrupted = true;
            } catch (final Exception e) {
                error = e;
            }

            while (true) {
                final var current = state.get();
                if (current instanceof final State.Running running) {
                    if (state.compareAndSet(current, State.Completed.INSTANCE)) {
                        if (interrupted)
                            onComplete.onCancel();
                        else if (error != null)
                            onComplete.onFailure(error);
                        else
                            onComplete.onSuccess(result);
                        return;
                    }
                } else if (current instanceof final State.Interrupting interrupting) {
                    while (true)
                        try {
                            interrupting.wasInterrupted.await();
                            break;
                        } catch (final InterruptedException ignored) {
                            //noinspection ResultOfMethodCallIgnored
                            Thread.interrupted();
                        }
                    state.lazySet(State.Completed.INSTANCE);
                    onComplete.onCancel();
                    return;
                } else if (current instanceof State.Completed) {
                    throw new IllegalStateException("Task:Completed");
                } else if (current instanceof State.NotStarted) {
                    throw new IllegalStateException("Task:NotStarted");
                }
            }
        });
        return () -> triggerCancel(state);
    }
}

@NullMarked
final class TaskFromCompletionStage<T> extends Task<T> {
    private final Callable<? extends CancellableCompletionStage<? extends T>> builder;

    public TaskFromCompletionStage(final Callable<? extends CancellableCompletionStage<? extends T>> builder) {
        this.builder = builder;
    }

    @Override
    public Cancellable executeAsync(final CompletionListener<? super T> listener) {
        var userError = true;
        try {
            final var future = builder.call();
            userError = false;

            future.completionStage().whenComplete((value, error) -> {
                if (error instanceof InterruptedException || error instanceof CancellationException) {
                    Trampoline.execute(listener::onCancel);
                } else if (error instanceof final ExecutionException e && e.getCause() != null) {
                    Trampoline.execute(() -> listener.onFailure(e.getCause()));
                } else if (error instanceof final CompletionException e && e.getCause() != null) {
                    Trampoline.execute(() -> listener.onFailure(error.getCause()));
                } else if (error != null) {
                    Trampoline.execute(() -> listener.onFailure(error));
                } else {
                    Trampoline.execute(() -> listener.onSuccess(value));
                }
            });
            return future.cancellable();
        } catch (final Exception e) {
            if (userError) {
                listener.onFailure(e);
                return Cancellable.EMPTY;
            }
            // noinspection ConstantValue
            if (e instanceof final RuntimeException re) throw re;
            throw new RuntimeException(e);
        }
    }
}
