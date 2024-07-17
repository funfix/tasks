@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.funfix.tasks

@NonBlocking
public actual object UncaughtExceptionHandler {
    public actual fun rethrowIfFatal(e: Throwable) {
        // Can we do something here?
    }

    public actual fun logOrRethrow(e: Throwable) {
        console.error(e)
    }
}
