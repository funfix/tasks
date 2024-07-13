package org.funfix.tasks

import org.funfix.tasks.internals.*
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeoutException

interface Fiber : Cancellable {
    fun joinAsync(onComplete: Runnable): Cancellable

    @Throws(InterruptedException::class, TimeoutException::class)
    fun joinBlockingTimed(timeout: Duration) {
        val latch = AwaitSignal()
        val token = joinAsync { latch.signal() }
        try {
            latch.await(timeout)
        } catch (e: InterruptedException) {
            token.cancel()
            throw e
        } catch (e: TimeoutException) {
            token.cancel()
            throw e
        }
    }

    @Throws(InterruptedException::class)
    fun joinBlocking() {
        val latch = AwaitSignal()
        val token = joinAsync { latch.signal() }
        try {
            latch.await()
        } finally {
            token.cancel()
        }
    }

    fun joinAsync(): CancellableFuture<Void?> {
        val p = CompletableFuture<Void?>()
        val token = joinAsync { p.complete(null) }
        val cRef = Cancellable {
            try {
                token.cancel()
            } finally {
                p.cancel(false)
            }
        }
        return CancellableFuture(p, cRef)
    }
}
