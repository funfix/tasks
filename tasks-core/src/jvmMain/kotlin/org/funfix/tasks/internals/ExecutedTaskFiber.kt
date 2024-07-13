package org.funfix.tasks.internals

import org.funfix.tasks.*
import java.util.concurrent.atomic.AtomicReference

/**
 * INTERNAL API, do not use!
 */
internal class ExecutedTaskFiber<T>(
    private val fiber: ExecutedFiber = ExecutedFiber()
) : TaskFiber<T> {
    private val outcomeRef: AtomicReference<Outcome<T>?> =
        AtomicReference(null)

    override fun outcome(): Outcome<T>? =
        outcomeRef.get()

    override fun joinAsync(onComplete: Runnable): Cancellable =
        fiber.joinAsync(onComplete)

    override fun cancel() {
        fiber.cancel()
    }

    fun registerCancel(token: Cancellable) {
        fiber.registerCancel(token)
    }

    private fun signalComplete(outcome: Outcome<T>) {
        if (outcomeRef.compareAndSet(null, outcome)) {
            fiber.signalComplete()
        } else if (outcome is Outcome.Failed) {
            UncaughtExceptionHandler.logOrRethrow(outcome.exception)
        }
    }

    val onComplete: CompletionCallback<T> =
        object : CompletionCallback<T> {
            override fun onSuccess(value: T) {
                signalComplete(Outcome.succeeded(value))
            }

            override fun onFailure(e: Throwable) {
                UncaughtExceptionHandler.rethrowIfFatal(e)
                signalComplete(Outcome.failed(e))
            }

            override fun onCancel() {
                signalComplete(Outcome.cancelled())
            }

            override fun onCompletion(outcome: Outcome<T>) {
                if (outcome is Outcome.Failed) {
                    UncaughtExceptionHandler.rethrowIfFatal(outcome.exception)
                }
                signalComplete(outcome)
            }
        }
}
