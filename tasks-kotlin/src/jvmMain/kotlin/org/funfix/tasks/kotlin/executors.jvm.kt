@file:JvmName("ExecutorsJvmKt")

package org.funfix.tasks.kotlin

import org.funfix.tasks.jvm.TaskExecutors

public actual val TrampolineExecutor: Executor
    get() = TaskExecutors.trampoline()

public actual val GlobalExecutor: Executor
    get() = TaskExecutors.sharedBlockingIO()
