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
    CompletionCallback { outcome ->
        when (outcome) {
            is org.funfix.tasks.jvm.Outcome.Success -> this@asJava(Outcome.Success(outcome.value))
            is org.funfix.tasks.jvm.Outcome.Failure -> this@asJava(Outcome.Failure(outcome.exception))
            is org.funfix.tasks.jvm.Outcome.Cancellation -> this@asJava(Outcome.Cancellation)
        }
    }
