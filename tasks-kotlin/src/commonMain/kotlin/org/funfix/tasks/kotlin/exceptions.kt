@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.funfix.tasks.kotlin

/**
 * An exception that is thrown when a task is cancelled.
 */
public expect open class TaskCancellationException(message: String?): Exception {
    public constructor()
}

public expect class FiberNotCompletedException public constructor() :
    Exception
