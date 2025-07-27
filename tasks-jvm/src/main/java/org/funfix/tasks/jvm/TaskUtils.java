package org.funfix.tasks.jvm;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

@NullMarked
@ApiStatus.Internal
final class TaskUtils {
    static <T extends @Nullable Object> T runBlockingUninterruptible(
        @Nullable final Executor executor,
        final DelayedFun<? extends T> func
    ) throws ExecutionException, InterruptedException {
        // Not using Task.fromBlockingIO because we don't need the
        // cancellation logic via thread interruption here.
        final var task = Task.<T>fromAsync((ec, callback) -> {
            try {
                callback.onSuccess(func.invoke());
            } catch (final InterruptedException e) {
                callback.onCancellation();
            } catch (final Exception e) {
                callback.onFailure(e);
            }
            return () -> {};
        });
        return runBlockingUninterruptible(null, task);
    }

    static <T extends @Nullable Object> T runBlockingUninterruptible(
        @Nullable final Executor executor,
        final Task<T> task
    ) throws InterruptedException, ExecutionException {
        final var fiber = executor != null
            ? task.runFiber(executor)
            : task.runFiber();

        InterruptedException wasInterrupted = null;
        T result;
        while (true) {
            try {
                result = fiber.awaitBlocking();
                break;
            } catch (final InterruptedException e) {
                if (wasInterrupted == null) wasInterrupted = e;
            } catch (TaskCancellationException e) {
                throw new RuntimeException(e);
            }
        }
        if (wasInterrupted != null) throw wasInterrupted;
        return result;
    }
}
