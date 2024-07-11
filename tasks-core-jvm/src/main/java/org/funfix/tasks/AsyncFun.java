package org.funfix.tasks;

import org.jspecify.annotations.NullMarked;

import java.io.Serializable;

/**
 * A function that is a delayed, asynchronous computation.
 */
@NullMarked
@FunctionalInterface
public interface AsyncFun<T> extends Serializable {
    Cancellable invoke(CompletionCallback<? super T> callback);
}
