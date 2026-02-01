package org.funfix.tasks.jvm;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonBlocking;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * This is a token that can be used for interrupting a scheduled or
 * a running task.
 *
 * <p>The contract for {@code cancel} is:
 * <ol>
 *   <li>Its execution is idempotent, meaning that calling it multiple times
 *   has the same effect as calling it once.</li>
 *   <li>It is safe to call {@code cancel} from any thread.</li>
 *   <li>It must not block, or do anything expensive. Blocking for the task's
 *   interruption should be done by other means, such as by using
 *   the {@link CompletionCallback} callback.</li>
 *   <li>Upon calling {@code cancel}, the {@link CompletionCallback} should
 *   still be eventually triggered, if it wasn't already. So all paths,
 *   with cancellation or without, must lead to the {@link CompletionCallback} being called.</li>
 * </ol>
 */
@FunctionalInterface
public interface Cancellable {
    /**
     * Triggers (idempotent) cancellation.
     */
    @NonBlocking
    void cancel();

    /**
     * Returns an empty token that does nothing when cancelled.
     */
    static Cancellable getEmpty() {
        return CancellableUtils.EMPTY;
    }
}

/**
 * INTERNAL API.
 * <p>
 * <strong>INTERNAL API:</strong> Internal apis are subject to change or removal
 * without any notice. When code depends on internal APIs, it is subject to
 * breakage between minor version updates.
 */
@ApiStatus.Internal
final class CancellableUtils {
    static final Cancellable EMPTY = () -> {};
}

/**
 * Represents a forward reference to a {@link Cancellable} that was already
 * registered and needs to be filled in later.
 * <p>
 * <strong>INTERNAL API:</strong> Internal apis are subject to change or removal
 * without any notice. When code depends on internal APIs, it is subject to
 * breakage between minor version updates.
 */
@ApiStatus.Internal
interface CancellableForwardRef extends Cancellable {
    void set(Cancellable cancellable);
}

/**
 * INTERNAL API.
 * <p>
 * <strong>WARN:</strong> Internal apis are subject to change or removal without
 * any notice. When code depends on internal APIs, it is subject to breakage
 * between minor version updates.
 */
@ApiStatus.Internal
final class MutableCancellable implements Cancellable {
    private final AtomicReference<State> ref;

    MutableCancellable(final Cancellable initialRef) {
        ref = new AtomicReference<>(new State.Active(initialRef, 0, null));
    }

    MutableCancellable() {
        this(CancellableUtils.EMPTY);
    }

    @Override
    public void cancel() {
        @Nullable
        var state = ref.getAndSet(State.Closed.INSTANCE);
        while (state instanceof State.Active active) {
            try {
                active.token.cancel();
            } catch (Exception e) {
                UncaughtExceptionHandler.logOrRethrow(e);
            }
            state = active.rest;
        }
    }

    public CancellableForwardRef newCancellableRef() {
        final var current = ref.get();
        if (current instanceof State.Closed) {
            return new CancellableForwardRef() {
                @Override
                public void set(Cancellable cancellable) {
                    cancellable.cancel();
                }
                @Override
                public void cancel() {}
            };
        } else if (current instanceof State.Active active) {
            return new CancellableForwardRef() {
                @Override
                public void set(Cancellable cancellable) {
                    registerOrdered(
                        active.order,
                        cancellable,
                        active
                    );
                }

                @Override
                public void cancel() {
                    unregister(active.order);
                }
            };
        } else {
            throw new IllegalStateException("Invalid state: " + current);
        }
    }

    public @Nullable Cancellable register(Cancellable token) {
        Objects.requireNonNull(token, "token");
        while (true) {
            final var current = ref.get();
            if (current instanceof State.Active active) {
                final var newOrder = active.order + 1;
                final var update = new State.Active(token, newOrder, active);
                if (ref.compareAndSet(current, update)) { return () -> unregister(newOrder); }
            } else if (current instanceof State.Closed) {
                token.cancel();
                return null;
            } else {
                throw new IllegalStateException("Invalid state: " + current);
            }
        }
    }

    private void unregister(final long order) {
        while (true) {
            final var current = ref.get();
            if (current instanceof State.Active active) {
                @Nullable var cursor = active;
                @Nullable State.Active acc = null;
                while (cursor != null) {
                    if (cursor.order != order) {
                        acc = new State.Active(cursor.token, cursor.order, acc);
                    }
                    cursor = cursor.rest;
                }
                // Reversing
                @Nullable State.Active update = null;
                while (acc != null) {
                    update = new State.Active(acc.token, acc.order, update);
                    acc = acc.rest;
                }
                if (update == null) {
                    update = new State.Active(Cancellable.getEmpty(), 0, null);
                }
                if (ref.compareAndSet(current, update)) {
                    return;
                }
            } else if (current instanceof State.Closed) {
                return;
            } else {
                throw new IllegalStateException("Invalid state: " + current);
            }
        }
    }

    private void registerOrdered(
        final long order,
        final Cancellable newToken,
        State current
    ) {
        while (true) {
            if (current instanceof State.Active active) {
                // Double-check ordering
                if (active.order != order) { return; }
                // Try to update
                final var update = new State.Active(newToken, order + 1, null);
                if (ref.compareAndSet(current, update)) { return; }
                // Retry
                current = ref.get();
            } else if (current instanceof State.Closed) {
                newToken.cancel();
                return;
            } else {
                throw new IllegalStateException("Invalid state: " + current);
            }
        }
    }

    sealed interface State {
        record Active(
            Cancellable token,
            long order,
            @Nullable Active rest
        ) implements State {}

        record Closed() implements State {
            static final Closed INSTANCE = new Closed();
        }
    }
}
