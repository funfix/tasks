package org.funfix.tasks.jvm;

import org.jetbrains.annotations.NonBlocking;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NullMarked;

import java.io.Serializable;
import java.util.concurrent.Executor;

/**
 * A function that is a delayed, asynchronous computation.
 */
@NullMarked
@FunctionalInterface
@NonBlocking
public interface AsyncFun<T extends @Nullable Object> extends Serializable {
    Cancellable invoke(
        Executor executor,
        CompletionCallback<? super T> continuation
    );
}
