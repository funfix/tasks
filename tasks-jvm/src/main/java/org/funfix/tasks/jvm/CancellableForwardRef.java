package org.funfix.tasks.jvm;

import org.jspecify.annotations.NullMarked;

/**
 * Represents a forward reference to a {@link Cancellable} that was already
 * registered and needs to be filled in later.
 *
 * @see Continuation#registerForwardCancellable()
 */
@NullMarked
@FunctionalInterface
public interface CancellableForwardRef {
    void set(Cancellable cancellable);
}
