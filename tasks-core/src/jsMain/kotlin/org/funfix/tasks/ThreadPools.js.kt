@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.funfix.tasks

import org.funfix.tasks.support.Executor
import org.funfix.tasks.support.Runnable


public actual object ThreadPools {
    public actual val TRAMPOLINE: Executor = Trampoline
}

/**
 * INTERNAL API, do not use!
 */
private object Trampoline: Executor {
    private var queue: MutableList<Runnable>? = null

    private fun eventLoop() {
        while (true) {
            val current = queue
            if (current.isNullOrEmpty()) {
                return
            }
            val next = current.removeFirstOrNull()
            try {
                next?.run()
            } catch (e: Exception) {
                UncaughtExceptionHandler.logOrRethrow(e)
            }
        }
    }

    override fun execute(command: Runnable) {
        val current = queue ?: mutableListOf()
        current.add(command)
        queue = current
        try {
            eventLoop()
        } finally {
            queue = null
        }
    }
}
