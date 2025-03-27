package org.funfix.tests

import java.time.Duration
import java.util.concurrent.*

object TimedAwait {
    val TIMEOUT: Duration =
        if (System.getenv("CI") != null) Duration.ofSeconds(20)
        else Duration.ofSeconds(10)

    @Throws(InterruptedException::class)
    fun latchNoExpectations(latch: CountDownLatch): Boolean =
        latch.await(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)

    @Throws(InterruptedException::class)
    fun latchAndExpectCompletion(latch: CountDownLatch) = latchAndExpectCompletion(latch, "latch")

    @Throws(InterruptedException::class)
    fun latchAndExpectCompletion(latch: CountDownLatch, name: String) {
        assert(latch.await(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) { "$name.await" }
    }

    @Throws(InterruptedException::class, TimeoutException::class)
    fun future(future: Future<*>) {
        try {
            future.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
        } catch (e: ExecutionException) {
            throw RuntimeException(e)
        }
    }
}
