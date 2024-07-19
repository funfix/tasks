package org.funfix.tasks.jvm;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.atomic.AtomicReference;

@NullMarked
public interface TaskFiber<T> extends Fiber {
    /**
     * @return the {@link Outcome} of the task, if it has completed,
     * or `null` if the task is still running.
     */
    @Nullable Outcome<T> outcome();
}

@NullMarked
final class TaskFiberDefault<T> implements TaskFiber<T> {
    private final SimpleFiber fiber;
    private final AtomicReference<@Nullable Outcome<T>> outcomeRef =
        new AtomicReference<>(null);

    public TaskFiberDefault() {
        this.fiber = new SimpleFiber();
    }

    @Override
    public @Nullable Outcome<T> outcome() {
        return outcomeRef.get();
    }

    @Override
    public Cancellable joinAsync(final Runnable onComplete) {
        return fiber.joinAsync(onComplete);
    }

    @Override
    public void cancel() {
        fiber.cancel();
    }

    void registerCancel(final Cancellable token) {
        fiber.registerCancel(token);
    }

    private void signalComplete(final Outcome<T> outcome) {
        if (outcomeRef.compareAndSet(null, outcome)) {
            fiber.signalComplete();
        } else if (outcome instanceof final Outcome.Failed<T> failed) {
            UncaughtExceptionHandler.logOrRethrowException(failed.exception());
        }
    }

    final CompletionCallback<T> onComplete = new CompletionCallback<>() {
        @Override
        public void onSuccess(final T value) {
            signalComplete(Outcome.succeeded(value));
        }

        @Override
        public void onFailure(final Throwable e) {
            UncaughtExceptionHandler.rethrowIfFatal(e);
            signalComplete(Outcome.failed(e));
        }

        @Override
        public void onCancellation() {
            signalComplete(Outcome.cancelled());
        }

        @Override
        public void onOutcome(final Outcome<T> outcome) {
            if (outcome instanceof final Outcome.Failed<?> failed) {
                UncaughtExceptionHandler.rethrowIfFatal(failed.exception());
            }
            signalComplete(outcome);
        }
    };
}
