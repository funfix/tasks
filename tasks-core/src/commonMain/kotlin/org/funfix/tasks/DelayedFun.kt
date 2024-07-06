package org.funfix.tasks

/**
 * Represents a delayed computation (a thunk).
 *
 * These functions are allowed to trigger side effects, with
 * blocking I/O and to throw exceptions.
 *
 * @see [AsyncFun]
 */
fun interface DelayedFun<out T> {
    @Throws(Exception::class)
    operator fun invoke(): T
}
