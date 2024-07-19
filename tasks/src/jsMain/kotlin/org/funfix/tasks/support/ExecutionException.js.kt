@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.funfix.tasks.support

/**
 * Exception thrown when attempting to retrieve the result of a task
 * that aborted by throwing an exception. This exception can be
 * inspected via [cause].
 *
 * On the JVM, this is alias for `java.util.concurrent.ExecutionException`.
 */
public actual open class ExecutionException actual constructor(
    message: String?,
    cause: Throwable
) : Exception(message, cause) {
    public actual constructor(cause: Throwable) : this(null, cause)
}
