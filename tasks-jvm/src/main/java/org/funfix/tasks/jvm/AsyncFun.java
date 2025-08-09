package org.funfix.tasks.jvm;

import org.jetbrains.annotations.NonBlocking;
import org.jspecify.annotations.Nullable;

import java.io.Serializable;
import java.util.concurrent.Executor;

/**
 * A function that is a delayed, asynchronous computation.
 * <p>
 * This function type is what's needed to describe {@link Task} instances.
 */
@FunctionalInterface
@NonBlocking
public interface AsyncFun<T extends @Nullable Object> extends Serializable {
    Cancellable invoke(
        Executor executor,
        CompletionCallback<? super T> continuation
    );
}
