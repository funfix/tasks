package org.funfix.tasks.jvm;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Blocking;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

@ApiStatus.Internal
final class TaskUtils {
    static <T extends @Nullable Object> Task<T> taskUninterruptibleBlockingIO(
        final DelayedFun<? extends T> func
    ) {
        return Task.fromAsync((ec, callback) -> {
            try {
                callback.onSuccess(func.invoke());
            } catch (final InterruptedException e) {
                callback.onCancellation();
            } catch (final Exception e) {
                callback.onFailure(e);
            }
            return () -> {};
        });
    }

    @Blocking
    @SuppressWarnings("UnusedReturnValue")
    static <T extends @Nullable Object> T runBlockingUninterruptible(
        @Nullable final Executor executor,
        final Task<T> task
    ) throws InterruptedException, ExecutionException {
        final var fiber = executor != null
            ? task.runFiber(executor)
            : task.runFiber();

        fiber.joinBlockingUninterruptible();
        try {
            return fiber.getResultOrThrow();
        } catch (TaskCancellationException | Fiber.NotCompletedException e) {
            throw new ExecutionException(e);
        }
    }
}
