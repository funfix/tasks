@file:JvmName("AliasesKt")
@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.funfix.tasks.kotlin

public actual typealias Cancellable = org.funfix.tasks.jvm.Cancellable

/**
 * A `CancellableFuture` is a tuple of a `CompletableFuture` and a `Cancellable`
 * reference.
 *
 * It's used to model the result of asynchronous computations that can be
 * cancelled. Needed because `CompletableFuture` doesn't actually support
 * cancellation. It's similar to [Fiber], which should be preferred, because
 * it's more principled. `CancellableFuture` is useful for interop with
 * Java libraries that use `CompletableFuture`.
 */
public typealias CancellableFuture<T> = org.funfix.tasks.jvm.CancellableFuture<out T>

public actual typealias TaskCancellationException = org.funfix.tasks.jvm.TaskCancellationException

public actual typealias FiberNotCompletedException = org.funfix.tasks.jvm.Fiber.NotCompletedException

public actual typealias Runnable = java.lang.Runnable

public actual typealias Executor = java.util.concurrent.Executor
