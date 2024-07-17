@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.funfix.tasks.support

public expect open class InterruptedException(message: String?): Exception {
    public constructor()
}
