@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.funfix.tasks.support

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
public expect annotation class NonBlocking()
