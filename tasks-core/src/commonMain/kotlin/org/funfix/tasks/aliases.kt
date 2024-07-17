@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.funfix.tasks

/**
 * Marker for types that should be serializable.
 *
 * On top of the JVM this is aliased to `java.io.Serializable`.
 */
public expect interface Serializable {}

/**
 * Exception thrown when attempting to retrieve the result of a task
 * that aborted by throwing an exception. This exception can be
 * inspected via [cause].
 *
 * On the JVM, this is alias for `java.util.concurrent.ExecutionException`.
 */
public expect open class ExecutionException(message: String?, cause: Throwable): Exception {
    public constructor(cause: Throwable)
}

/**
 * Documents functions that are inherently non-blocking and that can be used in
 * a non-blocking context.
 *
 * When this annotation is used on a `class`, all the methods declared by the
 * annotated class are considered `non-blocking`.
 *
 * This annotation is aliased to
 * [org.jetbrains.annotations.NonBlocking](https://github.com/JetBrains/java-annotations).
 */
@MustBeDocumented
@Retention(AnnotationRetention.BINARY)
@Target(allowedTargets = [AnnotationTarget.FUNCTION, AnnotationTarget.CONSTRUCTOR, AnnotationTarget.CLASS])
public expect annotation class NonBlocking() {}

@MustBeDocumented
@Retention(AnnotationRetention.BINARY)
@Target(allowedTargets = [AnnotationTarget.FUNCTION, AnnotationTarget.CONSTRUCTOR, AnnotationTarget.CLASS])
public expect annotation class Blocking() {}
