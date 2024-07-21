@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.funfix.tasks.kotlin

public actual fun interface Cancellable {
    public actual fun cancel()

    public companion object {
        public val empty: Cancellable =
            Cancellable {}
    }
}
