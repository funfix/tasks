@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.funfix.tasks

@NonBlocking
public expect object UncaughtExceptionHandler {
    public fun rethrowIfFatal(e: Throwable)
    public fun logOrRethrow(e: Throwable)
}
