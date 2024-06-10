package org.funfix.tasks

interface CompletionHandler<in T> {
    fun onSuccess(value: T)
    fun onException(e: Throwable)
    fun onCancellation()
}
