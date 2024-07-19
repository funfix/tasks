package org.funfix.tasks.jvm;

import org.jetbrains.annotations.NonBlocking;
import org.jspecify.annotations.NullMarked;
import java.io.Serializable;

/**
 * A function that is a delayed, asynchronous computation.
 */
@NullMarked
@FunctionalInterface
@NonBlocking
public interface AsyncFun<T> extends Serializable {
    Cancellable invoke(
            FiberExecutor executor,
            CompletionCallback<? super T> callback
    );
}
