@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.funfix.tasks

import org.funfix.tasks.support.Blocking

public actual fun interface DelayedFun<out T> {
    @Blocking
    public actual operator fun invoke(): T
}
