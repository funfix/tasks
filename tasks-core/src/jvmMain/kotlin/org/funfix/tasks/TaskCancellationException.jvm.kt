@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.funfix.tasks

public actual class TaskCancellationException actual constructor(
    message: String?
) : Exception(message) {
    public actual constructor() : this(null)
}
