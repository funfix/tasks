package org.funfix.tasks

import java.time.Duration

/**
 * Represents a [Task] that has started execution and is running concurrently.
 *
 * @param T is the type of the value that the task will complete with
 */
interface TaskFiber<out T> : Fiber {
    /**
     * @return the [Outcome] of the task, if it has completed, or `null` if the
     * task is still running.
     */
    fun outcome(): Outcome<T>?
}
