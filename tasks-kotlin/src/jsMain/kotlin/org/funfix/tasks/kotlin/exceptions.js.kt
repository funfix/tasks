@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.funfix.tasks.kotlin

public actual open class TaskCancellationException public actual constructor(
    message: String?
): Exception(message) {
    public actual constructor() : this(null)
}

public actual class FiberNotCompletedException
    public actual constructor(): Exception("Fiber not completed yet")

