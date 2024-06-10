package org.funfix.tasks

import kotlin.jvm.JvmField

/**
 * This is a token that can be used for interrupting a scheduled or
 * a running task.
 *
 * The contract for `cancel` is:
 *   1. Its execution is idempotent, meaning that calling it multiple times
 *      has the same effect as calling it once.
 *   2. It is safe to call `cancel` from any thread.
 *   3. It must not block, or do anything expensive. Blocking for the task's
 *      interruption should be done by other means, such as by using
 *      the [CompletionHandler] callback.
 */
interface Cancellable {
    fun cancel()

    companion object {
        @JvmField val EMPTY: Cancellable =
            object : Cancellable {
                override fun cancel() {}
            }
    }
}
