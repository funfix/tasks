package org.funfix.tasks.jvm;

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
    default Function<ExitCase, Task<Void>> toAsync() {
        @SuppressWarnings("NullAway")
        final Function<ExitCase, Task<Void>> r =
            exitCase -> TaskUtils.taskUninterruptibleBlockingIO(() -> {
                close(exitCase);
                return null;
            });
        return r;
    }

    @Override
    default void close() throws Exception {
        close(ExitCase.succeeded());
    }

    static CloseableFun fromAutoCloseable(AutoCloseable resource) {
        return ignored -> resource.close();
    }

    /**
     * Reusable reference for a no-op {@code CloseableFun}.
     */
    CloseableFun NOOP = ignored -> {};
}
