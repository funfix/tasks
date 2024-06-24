package org.funfix.tasks

/**
 * Exception thrown when trying to get the result of a concurrent job,
 * that should have been completed, but wasn't.
 */
class NotCompletedException(message: String?): java.lang.Exception(message) {
    constructor() : this(null)
}
