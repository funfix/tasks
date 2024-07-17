@file:JvmName("CoroutinesKt")
@file:JvmMultifileClass

package org.funfix.tasks

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

public suspend fun Fiber.cancelAndJoinSuspended() {
    cancel()
    joinSuspended()
}

public suspend fun Fiber.joinSuspended() {
    suspendCancellableCoroutine { cont ->
        val token = joinAsync {
            cont.resume(Unit)
        }
        cont.invokeOnCancellation {
            token.cancel()
        }
    }
}

public suspend fun <T> TaskFiber<T>.awaitOutcomeSuspended(): Outcome<T> {
    joinSuspended()
    return outcome()!!
}
