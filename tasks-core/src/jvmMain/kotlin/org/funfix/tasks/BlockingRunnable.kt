package org.funfix.tasks

import org.jetbrains.annotations.Blocking

/**
 * A [Runnable] that is annotated with [Blocking].
 *
 * Just a marker interface to indicate when [Runnable] instances are
 * allowed to perform blocking I/O operations.
 */
fun interface BlockingRunnable : Runnable {
    @Blocking
    override fun run()
}
