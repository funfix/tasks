@file:kotlin.jvm.JvmName("CoroutinesKt")
@file:kotlin.jvm.JvmMultifileClass

package org.funfix.tasks.kt

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import org.funfix.tasks.Fiber
import org.funfix.tasks.Outcome
import kotlin.coroutines.resume

public suspend fun <T> Fiber<T>.cancelAndJoinSuspended() {
    cancel()
    joinSuspended()
}

public suspend fun <T> Fiber<T>.joinSuspended() {
    suspendCancellableCoroutine { cont ->
        val token = joinAsync {
            cont.resume(Unit)
        }
        cont.invokeOnCancellation {
            token.cancel()
        }
    }
}

public suspend fun <T> Fiber<T>.awaitSuspended(): T {
    joinSuspended()
    return when (val o = outcome!!) {
        is Outcome.Success ->
            o.value
        is Outcome.Failure ->
            throw o.exception
        is Outcome.Cancellation ->
            throw CancellationException("Fiber was cancelled")
    }
}
