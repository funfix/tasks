package org.funfix.tasks.jvm;

import org.jetbrains.annotations.NonBlocking;
import org.jspecify.annotations.Nullable;

import java.io.Serializable;

/**
 * A function that is a delayed, asynchronous computation.
 * <p>
 * The injected {@link Continuation} provides:
 * <ul>
 *   <li>An {@code Executor} for scheduling work / introduce async boundaries.</li>
 *   <li>Methods to signal completion ({@link Continuation#onSuccess}, {@link Continuation#onFailure}, {@link Continuation#onCancellation})</li>
 *   <li>A way to register cancellation finalizers ({@link Continuation#invokeOnCancellation})</li>
 * </ul>
 * <p>
 * Example:
 * <pre>{@code
 * Task.fromAsync(continuation -> {
 *     final Executor executor = continuation.getExecutor();
 *     continuation.invokeOnCancellation(() -> System.out.println("cleanup"));
 *     executor.execute(() -> continuation.onSuccess("ok"));
 * });
 * }</pre>
 */
@FunctionalInterface
@NonBlocking
public interface AsyncFun<T extends @Nullable Object> extends Serializable {
    void invoke(final Continuation<? super T> continuation);
}
