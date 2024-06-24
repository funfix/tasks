package org.funfix.tasks

/**
 * Represents an exception that is thrown when a task is cancelled.
 *
 * `InterruptedException` has the meaning that the current thread was
 * interrupted. This is a JVM-specific exception. If the current thread
 * is waiting on a concurrent job, if an `InterruptedException` is thrown,
 * this doesn't mean that the concurrent job was cancelled; such a behavior
 * depending on the type of blocking operation being performed.
 *
 * We need to distinguish between `InterruptedException` and cancellation,
 * because there are cases where a concurrent job is cancelled without
 * the current thread being interrupted, and also, the current thread
 * might be interrupted without the concurrent job being cancelled.
 */
class CancellationException(message: String?): java.lang.Exception(message) {
    constructor() : this(null)
}
