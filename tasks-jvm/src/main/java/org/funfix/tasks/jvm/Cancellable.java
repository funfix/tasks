package org.funfix.tasks.jvm;

import lombok.Data;
import lombok.EqualsAndHashCode;
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
     * A {@code Cancellable} instance that does nothing.
     */
    Cancellable EMPTY = () -> { };
}

@NullMarked
final class MutableCancellable implements Cancellable {
    private final AtomicReference<State> ref =
            new AtomicReference<>(new State.Active(Cancellable.EMPTY, 0));

    @Override
    public void cancel() {
        final var prev = ref.getAndSet(State.Cancelled.INSTANCE);
        if (prev instanceof State.Active) {
            ((State.Active) prev).token.cancel();
        }
    }

    public void set(Cancellable token) {
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

    public void setOrdered(Cancellable token, int order) {
        Objects.requireNonNull(token, "token");
        while (true) {
            final var current = ref.get();
            if (current instanceof State.Active) {
                final var active = (State.Active) current;
                if (active.order < order) {
                    final var update = new State.Active(token, order);
                    if (ref.compareAndSet(current, update)) { return; }
                } else {
                    return;
                }
            } else if (current instanceof State.Cancelled) {
                token.cancel();
                return;
            } else {
                throw new IllegalStateException("Invalid state: " + current);
            }
        }
    }

    static abstract class State {
        @Data
        @EqualsAndHashCode(callSuper = false)
        static final class Active extends State {
            private final Cancellable token;
            private final int order;
        }

        @Data
        @EqualsAndHashCode(callSuper = false)
        static final class Cancelled extends State {
            static Cancelled INSTANCE = new Cancelled();
        }
    }
}
