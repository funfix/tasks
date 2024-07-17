package org.funfix.tasks

import org.jetbrains.annotations.Blocking

/**
 * Represents a delayed computation (a thunk).
 *
 * These functions are allowed to trigger side effects, with
 * blocking I/O and to throw exceptions.
 *
 * @see [AsyncFun]
 */
public fun interface DelayedFun<out T> {
    @Blocking
    @Throws(Exception::class)
    public operator fun invoke(): T
}
