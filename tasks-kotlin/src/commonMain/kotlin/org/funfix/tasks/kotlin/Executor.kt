@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.funfix.tasks.kotlin

public expect fun interface Runnable {
    public fun run()
}

public expect fun interface Executor {
    public fun execute(command: Runnable)
}
