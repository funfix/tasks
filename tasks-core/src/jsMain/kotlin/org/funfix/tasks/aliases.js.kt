@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.funfix.tasks

public actual interface Serializable {}

public actual open class ExecutionException actual constructor(
    message: String?,
    cause: Throwable
) : Exception(message, cause) {
    public actual constructor(cause: Throwable) : this(null, cause)
}

@MustBeDocumented
@Retention(AnnotationRetention.BINARY)
@Target(allowedTargets = [AnnotationTarget.FUNCTION, AnnotationTarget.CONSTRUCTOR, AnnotationTarget.CLASS])
public actual annotation class NonBlocking {}

@MustBeDocumented
@Retention(AnnotationRetention.BINARY)
@Target(allowedTargets = [AnnotationTarget.FUNCTION, AnnotationTarget.CONSTRUCTOR, AnnotationTarget.CLASS])
public actual annotation class Blocking actual constructor() {}
