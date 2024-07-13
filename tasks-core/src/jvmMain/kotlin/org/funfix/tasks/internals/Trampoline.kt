@file:Suppress("DEPRECATED_JAVA_ANNOTATION")

package org.funfix.tasks.internals

import org.funfix.tasks.UncaughtExceptionHandler
import java.util.LinkedList
import java.util.concurrent.Executor
import kotlin.concurrent.getOrSet

/**
 * INTERNAL API, do not use!
 */
internal object Trampoline {
    private val queue = ThreadLocal<LinkedList<Runnable>>()

    private fun eventLoop() {
        while (true) {
            val current = queue.get()
            if (current == null || current.isEmpty()) {
                return
            }
            val next = current.pollFirst()
            try {
                next?.run()
            } catch (e: Exception) {
                UncaughtExceptionHandler.logOrRethrow(e)
            }
        }
    }

    @JvmField
    val EXECUTOR: Executor = Executor { command ->
        val current = queue.getOrSet { LinkedList() }
        current.add(command)
        if (current.size == 1) {
            try {
                eventLoop()
            } finally {
                queue.remove()
            }
        }
    }

    @JvmStatic
    fun execute(command: Runnable) {
        EXECUTOR.execute(command)
    }
}
