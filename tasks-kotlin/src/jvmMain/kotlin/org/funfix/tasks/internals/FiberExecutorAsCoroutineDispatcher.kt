package org.funfix.tasks.internals

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.asExecutor
import org.funfix.tasks.BlockingRunnable
import org.funfix.tasks.Cancellable
import org.funfix.tasks.Fiber
import org.funfix.tasks.FiberExecutor
import kotlin.coroutines.CoroutineContext

internal class CoroutineDispatcherAsFiberExecutor(
    val dispatcher: CoroutineDispatcher
): FiberExecutor {
    private val executor = FiberExecutor.fromExecutor(dispatcher.asExecutor())

    override fun startFiber(command: BlockingRunnable): Fiber =
        executor.startFiber(command)

    override fun execute(command: Runnable) =
        executor.execute(command)

    override fun executeCancellable(command: BlockingRunnable, onComplete: Runnable?): Cancellable =
        executor.executeCancellable(command, onComplete)
}

internal class FiberExecutorAsCoroutineDispatcher(
    override val executor: FiberExecutor
): ExecutorCoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        executor.execute(block)
    }

    override fun close() {
        // nothing to do
    }
}
