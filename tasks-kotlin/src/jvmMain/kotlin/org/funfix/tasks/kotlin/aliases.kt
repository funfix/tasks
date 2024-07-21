@file:JvmName("AliasesKt")
@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.funfix.tasks.kotlin

public actual typealias Cancellable = org.funfix.tasks.jvm.Cancellable

public typealias CancellableFuture<T> = org.funfix.tasks.jvm.CancellableFuture<out T>

public typealias CompletionCallback<T> = org.funfix.tasks.jvm.CompletionCallback<T>

public typealias Continuation<T> = org.funfix.tasks.jvm.Continuation<T>

public typealias TaskCancellationException = org.funfix.tasks.jvm.TaskCancellationException

public typealias UncaughtExceptionHandler = org.funfix.tasks.jvm.UncaughtExceptionHandler
