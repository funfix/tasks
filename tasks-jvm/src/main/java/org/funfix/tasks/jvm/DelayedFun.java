package org.funfix.tasks.jvm;

import org.jspecify.annotations.Nullable;

/**
 * Represents a delayed computation (a thunk).
 * <p>
 * These functions are allowed to trigger side effects, with
 * blocking I/O and to throw exceptions.
 */
@FunctionalInterface
public interface DelayedFun<T extends @Nullable Object> {
    T invoke() throws Exception;
}
