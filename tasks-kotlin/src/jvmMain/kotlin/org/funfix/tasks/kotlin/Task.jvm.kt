@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
@file:JvmName("TaskJvmKt")

package org.funfix.tasks.kotlin

public actual typealias PlatformTask<T> = org.funfix.tasks.jvm.Task<T>

@JvmInline
public actual value class Task<out T> public actual constructor(
    public actual val asPlatform: PlatformTask<out T>
) {
    /**
     * Converts this task to a `jvm.Task`.
     */
    public val asJava: PlatformTask<out T> get() = asPlatform

    public actual companion object
}

public actual fun <T> Task<T>.runAsync(
    executor: Executor?,
    callback: Callback<T>
): Cancellable =
    asPlatform.runAsync(
        executor ?: GlobalExecutor,
        callback.asJava()
    )

public actual fun <T> Task.Companion.fromAsync(start: (Executor, Callback<T>) -> Cancellable): Task<T> =
    Task(PlatformTask.fromAsync { executor, cb ->
        start(executor, cb.asKotlin())
    })

public actual fun <T> Task.Companion.fromForkedAsync(start: (Executor, Callback<T>) -> Cancellable): Task<T> =
    Task(PlatformTask.fromForkedAsync { executor, cb ->
        start(executor, cb.asKotlin())
    })
