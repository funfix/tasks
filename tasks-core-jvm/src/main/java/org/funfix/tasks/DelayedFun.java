package org.funfix.tasks;

import org.jspecify.annotations.NullMarked;

/**
 * Represents a delayed computation (a thunk).
 * <p>
 * These functions are allowed to trigger side effects, with
 * blocking I/O and to throw exceptions.
 *
 * @see AsyncFun
 */
@NullMarked
@FunctionalInterface
public interface DelayedFun<T, E extends Exception> {
    T invoke() throws E;
}
