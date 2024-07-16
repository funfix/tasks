package org.funfix.tasks

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

suspend fun Fiber.cancelAndJoinSuspended(): Unit {
    cancel()
    joinSuspended()
}

suspend fun Fiber.joinSuspended(): Unit =
    suspendCancellableCoroutine { cont ->
        val token = joinAsync {
            cont.resume(Unit)
        }
        cont.invokeOnCancellation {
            token.cancel()
        }
    }

suspend fun <T> TaskFiber<T>.awaitOutcomeSuspended(): Outcome<T> {
    joinSuspended()
    return outcome()!!
}
