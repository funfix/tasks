package org.funfix.tasks;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

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
    @Nullable private Outcome<T> outcomeRef;

    public TaskFiberDefault() {
        this.fiber = new SimpleFiber();
    }

    @Override
    public @Nullable Outcome<T> outcome() {
        return outcomeRef;
    }

    @Override
    public Cancellable joinAsync(Runnable onComplete) {
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
        outcomeRef = outcome;
        fiber.signalComplete();
    }

    final CompletionCallback<T> onComplete = new CompletionCallback<>() {
        @Override
        public void onSuccess(T value) {
            signalComplete(Outcome.succeeded(value));
        }

        @Override
        public void onFailure(Throwable e) {
            signalComplete(Outcome.failed(e));
        }

        @Override
        public void onCancel() {
            signalComplete(Outcome.cancelled());
        }

        @Override
        public void onCompletion(Outcome<T> outcome) {
            signalComplete(outcome);
        }
    };
}
