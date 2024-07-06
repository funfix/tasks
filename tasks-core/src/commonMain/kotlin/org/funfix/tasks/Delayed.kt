package org.funfix.tasks

/**
 * Represents a function that is blocking and can throw exceptions.
 */
fun interface Delayed<out T> {
    @Throws(Exception::class)
    operator fun invoke(): T
}
