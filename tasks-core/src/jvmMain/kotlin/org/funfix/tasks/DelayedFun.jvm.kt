@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.funfix.tasks

import org.funfix.tasks.support.Blocking
import kotlin.jvm.Throws

public actual fun interface DelayedFun<out T> {
    @Blocking
    @Throws(java.lang.Exception::class)
    public actual operator fun invoke(): T
}
