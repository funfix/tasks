package org.funfix.tasks.jvm;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public interface Continuation<T extends @Nullable Object>
    extends CompletionCallback<T> {

    void registerCancellable(Cancellable cancellable);
    CancellableForwardRef registerForwardCancellable();
}

@NullMarked
final class CancellableContinuation<T extends @Nullable Object>
    implements Continuation<T>, Cancellable {

    private final CompletionCallback<T> callback;
    private final MutableCancellable cancellable;

    public CancellableContinuation(final CompletionCallback<T> callback) {
        this.callback = callback;
        this.cancellable = new MutableCancellable();
    }

    @Override
    public void cancel() {
        cancellable.cancel();
    }

    @Override
    public CancellableForwardRef registerForwardCancellable() {
        return cancellable.newCancellableRef();
    }

    @Override
    public void registerCancellable(Cancellable cancellable) {
        this.cancellable.register(cancellable);
    }

    @Override
    public void onSuccess(T value) {
        callback.onSuccess(value);
    }

    @Override
    public void onFailure(Throwable e) {
        callback.onFailure(e);
    }

    @Override
    public void onCancellation() {
        callback.onCancellation();
    }
}
