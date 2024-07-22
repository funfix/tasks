package org.funfix.tasks.kotlin

internal typealias Callback<T> = (Outcome<T>) -> Unit

internal fun <T> Callback<T>.protect(): Callback<T> {
    var isWaiting = true
    return { o ->
        if (o is Outcome.Failure) {
            UncaughtExceptionHandler.logOrRethrow(o.exception)
        }
        if (isWaiting) {
            isWaiting = false
            TaskExecutors.trampoline.execute {
                this@protect.invoke(o)
            }
        }
    }
}
