package org.funfix.tasks.jvm;

import org.jspecify.annotations.NullMarked;

/**
 * Represents a delayed computation (a thunk).
 * <p>
 * These functions are allowed to trigger side effects, with
 * blocking I/O and to throw exceptions.
 */
@NullMarked
@FunctionalInterface
public interface DelayedFun<T> {
    T invoke() throws Exception;
}
