package org.funfix.tasks.jvm;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonBlocking;
import org.jspecify.annotations.NullMarked;

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
@NullMarked
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
@NullMarked
final class CancellableUtils {
    static Cancellable EMPTY = () -> {};
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
@NullMarked
@FunctionalInterface
interface CancellableForwardRef {
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
@NullMarked
final class MutableCancellable implements Cancellable {
    private final AtomicReference<State> ref =
            new AtomicReference<>(new State.Active(Cancellable.getEmpty(), 0));

    @Override
    public void cancel() {
        final var prev = ref.getAndSet(State.Cancelled.INSTANCE);
        if (prev instanceof State.Active) {
            ((State.Active) prev).token.cancel();
        }
    }

    public CancellableForwardRef newCancellableRef() {
        final var current = ref.get();
        if (current instanceof State.Cancelled) {
            return Cancellable::cancel;
        } else if (current instanceof State.Active) {
            final var active = (State.Active) current;
            return cancellable -> registerOrdered(
                active.order,
                cancellable,
                active
            );
        } else {
            throw new IllegalStateException("Invalid state: " + current);
        }
    }

    public void register(Cancellable token) {
        Objects.requireNonNull(token, "token");
        while (true) {
            final var current = ref.get();
            if (current instanceof State.Active) {
                final var active = (State.Active) current;
                final var update = new State.Active(token, active.order + 1);
                if (ref.compareAndSet(current, update)) { return; }
            } else if (current instanceof State.Cancelled) {
                token.cancel();
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
            if (current instanceof State.Active) {
                // Double-check ordering
                final var active = (State.Active) current;
                if (active.order != order) { return; }
                // Try to update
                final var update = new State.Active(newToken, order + 1);
                if (ref.compareAndSet(current, update)) { return; }
                // Retry
                current = ref.get();
            } else if (current instanceof State.Cancelled) {
                newToken.cancel();
                return;
            } else {
                throw new IllegalStateException("Invalid state: " + current);
            }
        }
    }

    static sealed abstract class State {
        @Data
        @EqualsAndHashCode(callSuper = false)
        static final class Active extends State {
            private final Cancellable token;
            private final long order;
        }

        @Data
        @EqualsAndHashCode(callSuper = false)
        static final class Cancelled extends State {
            static Cancelled INSTANCE = new Cancelled();
        }
    }
}
