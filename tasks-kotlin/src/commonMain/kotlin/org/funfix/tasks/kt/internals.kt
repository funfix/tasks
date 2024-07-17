package org.funfix.tasks.kt

import kotlinx.coroutines.CoroutineDispatcher
import org.funfix.tasks.support.Executor

internal expect fun coroutineDispatcherAsExecutor(executor: Executor): CoroutineDispatcher
