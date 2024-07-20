package org.funfix.tasks.jvm;

import org.jspecify.annotations.NullMarked;

@NullMarked
public interface Continuation<T> extends CompletionCallback<T> {
    void registerCancellable(Cancellable cancellable);
}
