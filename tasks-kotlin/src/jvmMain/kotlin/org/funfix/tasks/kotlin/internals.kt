@file:JvmName("InternalsJvmKt")

package org.funfix.tasks.kotlin

internal typealias KotlinCallback<T> = (Outcome<T>) -> Unit

internal fun <T> CompletionCallback<T>.asKotlin(): KotlinCallback<T> =
    { outcome ->
        when (outcome) {
            is Outcome.Success -> this.onSuccess(outcome.value)
            is Outcome.Failure -> this.onFailure(outcome.exception)
            is Outcome.Cancellation -> this.onCancellation()
        }
    }

internal fun <T> KotlinCallback<T>.asJava(): CompletionCallback<T> =
    object : CompletionCallback<T> {
        override fun onSuccess(value: T): Unit = this@asJava(Outcome.Success(value))
        override fun onFailure(e: Throwable): Unit = this@asJava(Outcome.Failure(e))
        override fun onCancellation(): Unit = this@asJava(Outcome.Cancellation)
    }
