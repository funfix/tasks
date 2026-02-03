package org.funfix.tasks.jvm;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;

@NullMarked
public final class FiberScope {
    private final DefaultSupervisorImpl underlying;

    private FiberScope(@Nullable Executor executor) {
        final var javaVersion = Runtime.version().feature();
        if (javaVersion >= 21 && javaVersion < 24) {
            this.underlying = new Java21SupervisorImpl(executor);
        } else {
            this.underlying = new DefaultSupervisorImpl(executor);
        }
    }

    public <T> Fiber<T> start(Task<T> task) {
        return underlying.fork(task);
    }

    public <T> Fiber<T> start(final DelayedFun<? extends T> run) {
        return underlying.fork(Task.fromBlockingIO(run));
    }

    public void joinAllAsync(Runnable callback) {
        underlying.joinAsync(callback);
    }

    public void joinAllBlocking() throws InterruptedException {
        underlying.joinBlocking();
    }

    public void joinAllBlockingTimed(final Duration timeout) throws InterruptedException,
        TimeoutException {
        underlying.joinBlockingTimed(timeout);
    }
}

final class Java21SupervisorImpl extends DefaultSupervisorImpl {
    private final ReentrantLock lock;

    Java21SupervisorImpl(@Nullable Executor executor) {
        super(executor);
        this.lock = new ReentrantLock();
    }

    @Override
    protected void joinFiber(Fiber<?> fiber) {
        this.lock.lock();
        try {
            unsafeJoinFiber(fiber);
        } finally {
            this.lock.unlock();
        }
    }

    @Override
    <A> Fiber<A> fork(Task<A> task) {
        this.lock.lock();
        try {
            return unsafeFork(task);
        } finally {
            this.lock.unlock();
        }
    }

    @Override
    void joinAsync(Runnable callback) {
        this.lock.lock();
        try {
            unsafeJoinAsync(callback);
        } finally {
            this.lock.unlock();
        }
    }

    @Override
    protected boolean joinLatch(AwaitSignal latch) {
        this.lock.lock();
        try {
            return unsafeJoinLatch(latch);
        } finally {
            this.lock.unlock();
        }
    }
}

class DefaultSupervisorImpl {
    @Nullable
    private final Executor executor;
    private final ArrayList<Fiber<?>> activeFibers;
    private final ArrayList<Runnable> joinCallbacks;
    private boolean isActive;
    private final Object lock;

    DefaultSupervisorImpl(@Nullable Executor executor) {
        this.executor = executor;
        this.activeFibers = new ArrayList<>();
        this.isActive = true;
        this.joinCallbacks = new ArrayList<>();
        this.lock = new Object();
    }

    final void joinBlocking() throws InterruptedException {
        final var latch = new AwaitSignal();
        if (joinLatch(latch)) {
            latch.await();
        }
    }

    final void joinBlockingTimed(final Duration timeout) throws InterruptedException,
        TimeoutException {
        final var latch = new AwaitSignal();
        if (joinLatch(latch)) {
            latch.await(timeout.toMillis());
        }
    }

    protected boolean joinLatch(final AwaitSignal latch) {
        synchronized (lock) {
            return unsafeJoinLatch(latch);
        }
    }

    protected final boolean unsafeJoinLatch(final AwaitSignal latch) {
        if (isActive) {
            joinAsync(latch::signal);
            return true;
        }
        return false;
    }

    void joinAsync(Runnable callback) {
        synchronized (lock) {
            unsafeJoinAsync(callback);
        }
    }

    protected final void unsafeJoinAsync(Runnable callback) {
        if (isActive || !activeFibers.isEmpty()) {
            isActive = false;
            joinCallbacks.add(callback);
        } else {
            // execute it now, asynchronously
            executorOrDefault().execute(callback);
        }
    }

    protected void joinFiber(Fiber<?> fiber) {
        synchronized (lock) {
            unsafeJoinFiber(fiber);
        }
    }

    protected final void unsafeJoinFiber(Fiber<?> fiber) {
        // Order of tests is important here, since removing the fiber
        // is mandatory, plus, we have to notify in case this was the last
        // active fiber AND the supervisor is not active anymore
        final var shouldNotify =
            activeFibers.remove(fiber) &&
            !isActive &&
            activeFibers.isEmpty();

        if (shouldNotify) {
            // making a copy to avoid concurrent modifications
            final var callbacks = new ArrayList<>(joinCallbacks);
            joinCallbacks.clear();
            // execute them asynchronously
            executorOrDefault().execute(() -> {
                for (final var r : callbacks) {
                    try {
                        r.run();
                    } catch (final Exception e) {
                        Thread.currentThread()
                            .getUncaughtExceptionHandler()
                            .uncaughtException(Thread.currentThread(), e);
                    }
                }
            });
        }
    }

    <A> Fiber<A> fork(Task<A> task) {
        synchronized (lock) {
            return unsafeFork(task);
        }
    }

    protected final <A> Fiber<A> unsafeFork(Task<A> task) {
        if (!isActive) {
            return Task.<A>cancelled().runFiber(executor);
        }
        final var fiber = task.runFiber(executor);
        activeFibers.add(fiber);
        fiber.joinAsync(() -> joinFiber(fiber));
        return fiber;
    }

    private Executor executorOrDefault() {
        return executor != null ? executor : TaskExecutors.sharedBlockingIO();
    }
}
