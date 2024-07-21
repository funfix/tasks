package org.funfix.tasks.jvm;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Represents a delayed computation (a thunk).
 * <p>
 * These functions are allowed to trigger side effects, with
 * blocking I/O and to throw exceptions.
 */
@NullMarked
@FunctionalInterface
public interface DelayedFun<T extends @Nullable Object>
    extends DelayedCheckedFun<T, Exception> {

    T invoke() throws Exception;
}
