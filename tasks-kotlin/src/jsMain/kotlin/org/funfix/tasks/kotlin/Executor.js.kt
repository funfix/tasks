@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.funfix.tasks.kotlin

public actual fun interface Runnable {
    public actual fun run()
}

public actual fun interface Executor {
    public actual fun execute(command: Runnable)
}
