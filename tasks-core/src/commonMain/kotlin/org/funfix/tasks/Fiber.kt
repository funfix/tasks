@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.funfix.tasks

import org.funfix.tasks.support.Executor
import org.funfix.tasks.support.NonBlocking
import org.funfix.tasks.support.Runnable

public expect interface Fiber : Cancellable {
    @NonBlocking
    public fun joinAsync(onComplete: Runnable): Cancellable
}

public expect interface TaskFiber<out T> : Fiber {
    @NonBlocking
    public fun outcome(): Outcome<T>?
}

public expect interface FiberExecutor: Executor {
    public companion object {
        public val global: FiberExecutor
    }
}
