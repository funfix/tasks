@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.funfix.tasks.support

public actual interface Executor {
    public actual fun execute(command: Runnable)
}
