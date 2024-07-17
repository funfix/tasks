@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.funfix.tasks

import org.funfix.tasks.support.Executor

public expect object ThreadPools {
    public val TRAMPOLINE: Executor
}
