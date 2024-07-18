@file:JvmName("InternalsKt")
@file:JvmMultifileClass

package org.funfix.tasks.kt

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.asExecutor
import java.util.concurrent.Executor

internal actual fun coroutineDispatcherAsExecutor(dispatcher: CoroutineDispatcher): Executor =
    dispatcher.asExecutor()

internal actual fun executorAsCoroutineDispatcher(executor: Executor): CoroutineDispatcher =
    executor.asCoroutineDispatcher()
