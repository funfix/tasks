package org.funfix.tasks

@FunctionalInterface
interface BuilderFunction<out T> {
    @Throws(Exception::class)
    operator fun invoke(): T
}
