@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.funfix.tasks

import org.funfix.tasks.support.Executor
import org.funfix.tasks.support.Runnable
import org.w3c.dom.WindowOrWorkerGlobalScope
import kotlin.js.Promise

public actual object TaskExecutors {
    public actual val trampoline: Executor = Trampoline
    public actual val global: Executor = JSExecutor
}

private external val self: dynamic
private external val global: dynamic

private val globalOrSelfDynamic =
    (self ?: global)!!
private val globalOrSelf =
    globalOrSelfDynamic.unsafeCast<WindowOrWorkerGlobalScope>()

internal object JSExecutor: Executor {
    class NotSupported(execType: ExecType): Exception(
        "Executor type $execType is not supported on this runtime"
    )

    private var execType =
        if (globalOrSelfDynamic.setInterval != null) ExecType.ViaSetInterval
        else if (globalOrSelfDynamic.setTimeout != null) ExecType.ViaSetTimeout
        else ExecType.Trampolined

    inline fun <T> withExecType(execType: ExecType, block: () -> T): T {
        when (execType) {
            ExecType.ViaSetInterval ->
                if (globalOrSelfDynamic.setInterval == null) throw NotSupported(execType)
            ExecType.ViaSetTimeout ->
                if (globalOrSelfDynamic.setTimeout == null) throw NotSupported(execType)
            ExecType.Trampolined ->
                Unit
        }
        val oldRef = this.execType
        this.execType = execType
        try {
            return block()
        } finally {
            this.execType = oldRef
        }
    }

    fun yield(): Promise<Unit> {
        return Promise { resolve, _ ->
            execute {
                resolve(Unit)
            }
        }
    }

    override fun execute(command: Runnable) {
        val handler: () -> Unit = {
            try {
                command.run()
            } catch (e: Exception) {
                UncaughtExceptionHandler.logOrRethrow(e)
            }
        }
        when (execType) {
            ExecType.ViaSetInterval ->
                globalOrSelf.setInterval(handler)
            ExecType.ViaSetTimeout ->
                globalOrSelf.setTimeout(handler, -1)
            ExecType.Trampolined ->
                Trampoline.execute(command)
        }
    }

    sealed interface ExecType {
        data object ViaSetInterval: ExecType
        data object ViaSetTimeout: ExecType
        data object Trampolined: ExecType
    }
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
