@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.funfix.tasks

import org.funfix.tasks.support.Blocking

/**
 * Represents a delayed computation (a thunk).
 *
 * These functions are allowed to trigger side effects, with
 * blocking I/O and to throw exceptions.
 */
public expect fun interface DelayedFun<out T> {
    @Blocking
    public operator fun invoke(): T
}
