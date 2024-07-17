package org.funfix.tasks.kt

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import org.funfix.tasks.support.Executor

internal actual fun coroutineDispatcherAsExecutor(executor: Executor): CoroutineDispatcher =
    executor.asCoroutineDispatcher()
