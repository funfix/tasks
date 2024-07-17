package org.funfix.tasks.kt

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable
import org.funfix.tasks.support.Executor
import kotlin.coroutines.CoroutineContext

internal actual fun coroutineDispatcherAsExecutor(executor: Executor): CoroutineDispatcher =
    object : CoroutineDispatcher() {
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            executor.execute { block.run() }
        }
    }
