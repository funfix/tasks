@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.funfix.tasks.support

/**
 * Exception thrown when attempting to retrieve the result of a task
 * that aborted by throwing an exception. This exception can be
 * inspected via [cause].
 *
 * On the JVM, this is alias for `java.util.concurrent.ExecutionException`.
 */
public expect open class ExecutionException(message: String?, cause: Throwable): Exception {
    public constructor(cause: Throwable)
}
