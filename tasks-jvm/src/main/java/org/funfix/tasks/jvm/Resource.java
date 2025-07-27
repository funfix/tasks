package org.funfix.tasks.jvm;

import org.jetbrains.annotations.Blocking;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.function.Function;

@NullMarked
public record Resource<T extends @Nullable Object>(
    Task<Allocated<T>> allocatedTask
) {
    @Blocking
    public Allocated<T> allocateBlocking()
        throws ExecutionException, InterruptedException {

        return Objects.requireNonNull(allocatedTask.runBlocking());
    }

    @Blocking
    public Allocated<T> allocateBlocking(final Executor executor)
        throws ExecutionException, InterruptedException {

        return Objects.requireNonNull(allocatedTask.runBlocking(executor));
    }

    public static <T extends @Nullable Object> Resource<T> alreadyInitialized(
        final T resource,
        final Function<ExitCase, Task<Void>> releaseTask
    ) {
        return new Resource<>(Task.pure(new Allocated<>(resource, releaseTask)));
    }

    public static <T extends @Nullable Object> Resource<T> pure(
        final T resource
    ) {
        return Resource.alreadyInitialized(resource, ignored -> Task.pure(null));
    }

    public static <T extends AutoCloseable> Resource<T> fromAutoCloseable(T resource) {
        return alreadyInitialized(resource, ignored ->
            Task.fromAsync((ec, callback) -> {
                try {
                    resource.close();
                    callback.onSuccess(null);
                } catch (final InterruptedException e) {
                    callback.onCancellation();
                } catch (final Exception e) {
                    callback.onFailure(e);
                }
                return () -> {};
            }));
    }

    @NullMarked
    public record Allocated<T extends @Nullable Object>(
        T resource,
        Function<ExitCase, Task<Void>> releaseTask
    ) implements AutoCloseable {
        public Task<Void> releaseTask(final ExitCase exitCase) {
            return releaseTask.apply(exitCase);
        }

        public void releaseBlocking(final ExitCase exitCase)
            throws InterruptedException, ExecutionException {
            releaseBlocking(null, exitCase);
        }

        public void releaseBlocking(
            final @Nullable Executor executor,
            final ExitCase exitCase
        ) throws InterruptedException, ExecutionException {
            TaskUtils.runBlockingUninterruptible(executor, releaseTask(exitCase));
        }

        @Override
        public void close() throws Exception {
            releaseBlocking(ExitCase.succeeded());
        }
    }
}
