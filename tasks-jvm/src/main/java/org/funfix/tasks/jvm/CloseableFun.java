package org.funfix.tasks.jvm;

@FunctionalInterface
public interface CloseableFun extends AutoCloseable {
    void close(ExitCase exitCase) throws Exception;

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
    CloseableFun VOID = ignored -> {};
}
