@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.funfix.tasks.support

public actual open class InterruptedException actual constructor(message: String?) :
    Exception(message) {
    public actual constructor() : this(null)
}
