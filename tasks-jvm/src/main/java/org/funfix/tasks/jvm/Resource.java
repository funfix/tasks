package org.funfix.tasks.jvm;

import org.jetbrains.annotations.Blocking;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.function.Function;

/**
 * A {@code Resource} represents a resource that can be acquired asynchronously.
 * <p>
 * This is similar to Java's {@link AutoCloseable}, but it's more portable
 * and allows for asynchronous contexts.
 * <p>
 * {@code Resource} is the equivalent of {@link Task} for resource acquisition.
 * <p>
 * Sample usage:
 * <pre>{@code
 *     // -------------------------------------------------------
 *     // SAMPLE METHODS
 *     // -------------------------------------------------------
 *
 *     // Creates temporary files — note that wrapping this in `Resource` is a
 *     // win as `File` isn't `AutoCloseable`).
 *     Resource<File> createTemporaryFile(String prefix, String suffix) {
 *         return Resource.fromBlockingIO(() -> {
 *             File tempFile = File.createTempFile(prefix, suffix);
 *             tempFile.deleteOnExit(); // Ensure it gets deleted on exit
 *             return new Resource.Closeable<>(
 *                 tempFile,
 *                 ignored -> tempFile.delete()
 *             );
 *         });
 *     }
 *
 *     // Creates `Reader` as a `Resource`
 *     Resource<BufferedReader> openReader(File file) {
 *         return Resource.fromAutoCloseable(() ->
 *             new BufferedReader(
 *                 new InputStreamReader(
 *                     new FileInputStream(file),
 *                     StandardCharsets.UTF_8
 *                 )
 *             ));
 *     }
 *
 *     // Creates `Writer` as a `Resource`
 *     Resource<BufferedWriter> openWriter(File file) {
 *         return Resource.fromAutoCloseable(() ->
 *             new BufferedWriter(
 *                 new OutputStreamWriter(
 *                     new FileOutputStream(file),
 *                     StandardCharsets.UTF_8
 *                 )
 *             ));
 *     }
 *
 *     // ...
 *     // -------------------------------------------------------
 *     // USAGE EXAMPLE (via try-with-resources)
 *     // -------------------------------------------------------
 *     try (
 *         final var file = createTemporaryFile("test", ".txt").acquireBlocking()
 *     ) {
 *         try (final var writer = openWriter(file.get()).acquireBlocking()) {
 *             writer.get().write("----\n");
 *             writer.get().write("line 1\n");
 *             writer.get().write("line 2\n");
 *             writer.get().write("----\n");
 *         }
 *
 *         try (final var reader = openReader(file.get()).acquireBlocking()) {
 *             final var builder = new StringBuilder();
 *             String line;
 *             while ((line = reader.get().readLine()) != null) {
 *                 builder.append(line).append("\n");
 *             }
 *             final String content = builder.toString();
 *             assertEquals(
 *                 "----\nline 1\nline 2\n----\n",
 *                 content,
 *                 "File content should match the written lines"
 *             );
 *         }
 *     }
 * }</pre>
 */
@NullMarked
public final class Resource<T extends @Nullable Object> {
    private final Task<Acquired<T>> acquireTask;

    private Resource(final Task<Acquired<T>> acquireTask) {
        this.acquireTask = Objects.requireNonNull(acquireTask, "acquireTask");
    }

    /**
     * Acquires the resource asynchronously via {@link Task}, returning
     * it wrapped in {@link Acquired}, which contains the logic for safe release.
     */
    public Task<Acquired<T>> acquireTask() {
        return acquireTask;
    }

    /**
     * Acquires the resource via blocking I/O.
     * <p>
     * This method blocks the current thread until the resource is acquired.
     * <p>
     * @return an {@link Acquired} object that contains the acquired resource
     *         and the logic to release it.
     * @throws ExecutionException if the resource acquisition failed with an exception
     * @throws InterruptedException if the current thread was interrupted while waiting
     *         for the resource to be acquired
     */
    @Blocking
    public Acquired<T> acquireBlocking()
        throws ExecutionException, InterruptedException {
        return acquireBlocking(null);
    }

    /**
     * Acquires the resource via blocking I/O using the specified executor.
     * <p>
     * This method blocks the current thread until the resource is acquired.
     * <p>
     * @param executor is the executor to use for executing the acquire task.
     * @return an {@link Acquired} object that contains the acquired resource
     *         and the logic to release it.
     * @throws ExecutionException if the resource acquisition failed with an exception
     * @throws InterruptedException if the current thread was interrupted while waiting
     *         for the resource to be acquired
     */
    @Blocking
    public Acquired<T> acquireBlocking(@Nullable final Executor executor)
        throws ExecutionException, InterruptedException {

        return Objects.requireNonNull(
            executor != null
                ? acquireTask().runBlocking(executor)
                : acquireTask().runBlocking(),
            "Resource acquisition failed, acquireTask returned null"
        );
    }

    /**
     * Safely use the {@code Resource}, a method to use as an alternative to
     * Java's try-with-resources.
     * <p>
     * Note that an asynchronous (Task-driven) alternative isn't provided,
     * as that would require an asynchronous evaluation model that's outside
     * the scope. E.g., work with Kotlin's coroutines, or Scala's Cats-Effect
     * to achieve that.
     *
     * @param process is the processing function that can do I/O
     *
     * @return
     * @param <R>
     * @throws ExecutionException
     * @throws InterruptedException
     */
    @SuppressWarnings("ConstantValue")
    public <R extends @Nullable Object> R useBlocking(
        final ProcessFun<? super T, ? extends R> process
    ) throws IOException, InterruptedException, ExecutionException {
        Objects.requireNonNull(process, "use");
        var finalizerCalled = false;
        final var acquired = acquireBlocking();
        try {
            return process.call(acquired.get());
        } catch (InterruptedException e) {
            if (!finalizerCalled) {
                finalizerCalled = true;
                acquired.releaseBlocking(ExitCase.canceled());
            }
            throw e;
        } catch (RuntimeException | IOException | ExecutionException e) {
            if (!finalizerCalled) {
                finalizerCalled = true;
                acquired.releaseBlocking(ExitCase.failed(e));
            }
            throw e;
        } finally {
            if (!finalizerCalled) {
                acquired.releaseBlocking(ExitCase.succeeded());
            }
        }
    }

    /**
     * Creates a {@link Resource} from an asynchronous task that acquires the resource.
     * <p>
     * The task should return an {@link Acquired} object that contains the
     * acquired resource and the logic to release it.
     */
    public static <T extends @Nullable Object> Resource<T> fromAsync(
        final Task<Acquired<T>> acquire
    ) {
        Objects.requireNonNull(acquire, "acquire");
        return new Resource<>(acquire);
    }

    /**
     * Creates a {@link Resource} from a builder doing blocking I/O.
     * <p>
     * This method is useful for resources that are acquired via blocking I/O,
     * such as file handles, database connections, etc.
     * <p>
     * @param acquire is a function that returns a {@link Resource.Closeable} object
     *        that contains the acquired resource and the logic to release it.
     */
    public static <T extends @Nullable Object> Resource<T> fromBlockingIO(
        final DelayedFun<? extends Resource.Closeable<T>> acquire
    ) {
        Objects.requireNonNull(acquire, "acquire");
        return Resource.fromAsync(Task.fromBlockingIO(() -> {
            final Closeable<T> closeable = acquire.invoke();
            Objects.requireNonNull(closeable, "Resource allocation returned null");
            return new Acquired<>(
                closeable.get(),
                exitCase -> TaskUtils.taskUninterruptibleBlockingIO(() -> {
                    closeable.close(exitCase);
                    return null;
                }));
        }));
    }

    /**
     * Creates a {@link Resource} from a builder that returns an
     * {@link AutoCloseable} resource.
     */
    public static <T extends AutoCloseable> Resource<T> fromAutoCloseable(
        final DelayedFun<? extends T> acquire
    ) {
        Objects.requireNonNull(acquire, "acquire");
        return Resource.fromBlockingIO(() -> {
            final T resource = acquire.invoke();
            Objects.requireNonNull(resource, "Resource allocation returned null");
            return new Closeable<>(resource, CloseableFun.fromAutoCloseable(resource));
        });
    }

    /**
     * Creates a "pure" {@code Resource}.
     * <p>
     * A "pure" resource is one that just wraps a known value, with no-op
     * release logic.
     * <p>
     * @see Task#pure(Object)
     */
    public static <T extends @Nullable Object> Resource<T> pure(T value) {
        return Resource.fromAsync(Task.pure(Acquired.pure(value)));
    }

    /**
     * Tuple that represents an acquired resource and the logic to release it.
     *
     * @param get is the acquired resource
     * @param releaseTask is the (async) function that can release the resource
     */
    @NullMarked
    public record Acquired<T extends @Nullable Object>(
        T get,
        Function<ExitCase, Task<Void>> releaseTask
    ) implements AutoCloseable {
        /**
         * Used for asynchronous resource release.
         * <p>
         * @param exitCase signals the context in which the resource is being released.
         * @return a {@link Task} that releases the resource upon invocation.
         */
        public Task<Void> releaseTask(final ExitCase exitCase) {
            return releaseTask.apply(exitCase);
        }

        /**
         * Releases the resource in a blocking manner, using the default executor.
         * <p>
         * This method can block the current thread until the resource is released.
         * </p>
         * <p>
         * @param exitCase signals the context in which the resource is being released.
         */
        @Blocking
        public void releaseBlocking(final ExitCase exitCase)
            throws InterruptedException, ExecutionException {
            releaseBlocking(null, exitCase);
        }

        /**
         * Releases the resource in a blocking manner, using the specified executor.
         * <p>
         * This method can block the current thread until the resource is released.
         * <p>
         * @param executor is the executor to use for executing the release task.
         * @param exitCase signals the context in which the resource is being released.
         */
        @Blocking
        public void releaseBlocking(
            final @Nullable Executor executor,
            final ExitCase exitCase
        ) throws InterruptedException, ExecutionException {
            TaskUtils.runBlockingUninterruptible(executor, releaseTask(exitCase));
        }

        /**
         * Releases the resource in a blocking manner, using the default executor.
         * <p>
         * This being part of {@link AutoCloseable} means it can be used via a
         * try-with-resources block.
         */
        @Override
        public void close() throws Exception {
            releaseBlocking(ExitCase.succeeded());
        }

        /**
         * Creates a "pure" {@code Acquired} instance with the given value —
         * i.e., it just wraps a value with the release function being a no-op.
         */
        public static <T extends @Nullable Object> Acquired<T> pure(T value) {
            return new Acquired<>(value, NOOP);
        }

        private static Function<ExitCase, Task<Void>> NOOP =
            ignored -> Task.VOID;
    }

    /**
     * This is the equivalent of {@link Acquired}, but it is used for
     * resources that are used via Java's {@link AutoCloseable} in a
     * try-with-resources context.
     * <p>
     * Note that acquiring this resource was probably a blocking operation,
     * and its release may also block the current thread (perfectly acceptable
     * in a Java {@link AutoCloseable} context).
     * <p>
     * @param get is the acquired resource
     * @param releaseFun is the function that eventually gets invoked for
     *        releasing the resource
     */
    @NullMarked
    public record Closeable<T extends @Nullable Object>(
        T get,
        CloseableFun releaseFun
    ) implements CloseableFun {
        @Override
        public void close(ExitCase exitCase) throws Exception {
            releaseFun.close(exitCase);
        }

        /**
         * Creates a "pure" {@code Closeable} instance with the given value —
         * i.e., it just wraps a value with the release function being a no-op.
         */
        public static <T extends @Nullable Object> Closeable<T> pure(T value) {
            return new Closeable<>(value, CloseableFun.VOID);
        }
    }
}
