package org.funfix.tasks.jvm;

import org.jspecify.annotations.Nullable;

import java.util.function.Function;

/**
 * Blocking/synchronous finalizer of {@link Resource}.
 *
 * @see Resource#fromBlockingIO(DelayedFun)
 */
@FunctionalInterface
public interface CloseableFun extends AutoCloseable {
    void close(ExitCase exitCase) throws Exception;

    /**
     * Converts this blocking finalizer into an asynchronous one
     * that can be used for initializing {@link Resource.Acquired}.
     */
    default Function<ExitCase, Task<@Nullable Void>> toAsync() {
        // NullAway does not propagate the @Nullable Void type witness into the lambda body.
        @SuppressWarnings("NullAway")
        final Function<ExitCase, Task<@Nullable Void>> r =
            exitCase -> TaskUtils.<@Nullable Void>taskUninterruptibleBlockingIO(() -> {
                close(exitCase);
                return null;
            });
        return r;
    }

    @Override
    default void close() throws Exception {
        close(ExitCase.succeeded());
    }

    static CloseableFun fromAutoCloseable(final AutoCloseable resource) {
        return ignored -> resource.close();
    }

    /**
     * Reusable reference for a no-op {@code CloseableFun}.
     */
    CloseableFun NOOP = ignored -> {};
}
