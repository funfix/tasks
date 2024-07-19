@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.funfix.tasks

import org.funfix.tasks.support.Executor

/**
 * Provides utilities for creating [Executor] instances.
 */
public expect object TaskExecutors {
    /**
     * A globally-available [Executor] that can be used for running tasks.
     *
     * This is the default executor used by the [Task] API.
     *
     * - On Java 21, this is an executor working with virtual threads (Project Loom).
     * - On Java versions older than 21, this is an `Executors.newCachedThreadPool()`.
     * - On JavaScript, this is an executor that uses either `setTimeout` or `setInterval`.
     */
    public val global: Executor

    /**
     * An [Executor] that can be used for running tasks in a trampolined fashion
     * (i.e., immediately, on the current thread, but in a stack-safe way).
     */
    public val trampoline: Executor
}
