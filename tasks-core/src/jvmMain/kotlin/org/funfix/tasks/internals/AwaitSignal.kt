package org.funfix.tasks.internals

import java.time.Duration
import java.util.concurrent.TimeoutException
import java.util.concurrent.locks.AbstractQueuedSynchronizer

/**
 * INTERNAL API, do not use!
 */
internal class AwaitSignal : AbstractQueuedSynchronizer() {
    override fun tryAcquireShared(arg: Int): Int = if (state != 0) 1 else -1

    override fun tryReleaseShared(arg: Int): Boolean {
        state = 1
        return true
    }

    fun signal() {
        releaseShared(1)
    }

    @Throws(InterruptedException::class)
    fun await() {
        acquireSharedInterruptibly(1)
    }

    @Throws(InterruptedException::class, TimeoutException::class)
    fun await(timeout: Duration) {
        if (!tryAcquireSharedNanos(1, timeout.toNanos())) {
            throw TimeoutException("Timed out after $timeout")
        }
    }
}
