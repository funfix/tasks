package org.funfix.tasks.kt

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.funfix.tasks.TaskExecutors
import org.funfix.tasks.support.Executor

internal actual fun coroutineDispatcherAsExecutor(dispatcher: CoroutineDispatcher): Executor =
    // Building this `Executor` is problematic, and there's no real point in using anything
    // other than the global one on top of JS engines.
    TaskExecutors.global

internal actual fun executorAsCoroutineDispatcher(executor: Executor): CoroutineDispatcher =
    // Building this CoroutineDispatcher from an Executor is problematic, and there's no
    // point in even trying on top of JS engines.
    Dispatchers.Unconfined
