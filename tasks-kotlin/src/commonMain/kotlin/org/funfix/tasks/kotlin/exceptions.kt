@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.funfix.tasks.kotlin

/**
 * An exception that is thrown when waiting for the result of a task
 * that has been cancelled.
 *
 * Note, this is unlike the JVM's `InterruptedException` or Kotlin's
 * `CancellationException`, which are thrown when the current thread or fiber is
 * interrupted. This exception is thrown when waiting for the result of a task
 * that has been cancelled concurrently, but this doesn't mean that the current
 * thread or fiber was interrupted.
 */
public expect open class TaskCancellationException(message: String?): Exception {
    public constructor()
}

/**
 * Exception thrown when trying to get the result of a fiber that
 * hasn't completed yet.
 */
public expect class FiberNotCompletedException public constructor() :
    Exception
