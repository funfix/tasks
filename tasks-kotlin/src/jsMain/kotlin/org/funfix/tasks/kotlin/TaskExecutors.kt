package org.funfix.tasks.kotlin

import org.w3c.dom.WindowOrWorkerGlobalScope
import kotlin.js.Promise

public object TaskExecutors {
    public val trampoline: Executor = Trampoline
    public val global: Executor = JSExecutor
}

private external val self: dynamic
private external val global: dynamic

private val globalOrSelfDynamic =
    (self ?: global)!!
private val globalOrSelf =
    globalOrSelfDynamic.unsafeCast<WindowOrWorkerGlobalScope>()

private object JSExecutor: Executor {
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

internal object UncaughtExceptionHandler {
    fun rethrowIfFatal(e: Throwable) {
        // Can we do something here?
    }

    fun logOrRethrow(e: Throwable) {
        console.error(e)
    }
}

