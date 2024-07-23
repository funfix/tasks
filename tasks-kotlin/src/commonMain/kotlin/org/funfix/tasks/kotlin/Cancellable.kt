@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.funfix.tasks.kotlin

/**
 * Represents a non-blocking piece of logic that triggers the cancellation
 * procedure of an asynchronous computation.
 *
 * MUST NOT block the calling thread. Interruption of the computation
 * isn't guaranteed to have happened after this call returns.
 *
 * MUST BE idempotent, i.e. calling it multiple times should have the same
 * effect as calling it once.
 *
 * MUST BE thread-safe.
 */
public expect fun interface Cancellable {
    public fun cancel()
}
