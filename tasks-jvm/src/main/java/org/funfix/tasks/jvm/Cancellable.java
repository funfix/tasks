package org.funfix.tasks.jvm;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonBlocking;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * This is a token that can be used for interrupting a scheduled or
 * a running task.
 * <p>
 * {@code Cancellable} is used in several roles:
 * <ul>
 *   <li>As an external handle returned by {@link Task#runAsync} to cancel a task.</li>
 *   <li>As a cancellation finalizer registered via {@link Continuation#invokeOnCancellation}.</li>
 *   <li>As an internal mechanism for deregistration and cleanup.</li>
 * </ul>
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
        while (true) {
            final var current = ref.get();
            if (current instanceof State.Active active) {
                if (ref.compareAndSet(current, State.Cancelled.INSTANCE)) {
                    invokeAll(active);
                    return;
                }
            } else if (current instanceof State.Cancelled || current instanceof State.Completed) {
                return;
            } else {
                throw new IllegalStateException("Invalid state: " + current);
            }
        }
    }

    void complete() {
        while (true) {
            final var current = ref.get();
            if (current instanceof State.Active) {
                if (ref.compareAndSet(current, State.Completed.INSTANCE)) {
                    return;
                }
            } else if (current instanceof State.Cancelled || current instanceof State.Completed) {
                return;
            } else {
                throw new IllegalStateException("Invalid state: " + current);
            }
        }
    }

    public void register(Cancellable token) {
        Objects.requireNonNull(token, "token");
        while (true) {
            final var current = ref.get();
            if (current instanceof State.Active active) {
                final var newOrder = active.order + 1;
                final var update = new State.Active(token, newOrder, active);
                if (ref.compareAndSet(current, update)) { return; }
            } else if (current instanceof State.Cancelled) {
                invoke(token);
                return;
            } else if (current instanceof State.Completed) {
                return;
            } else {
                throw new IllegalStateException("Invalid state: " + current);
            }
        }
    }

    private static void invokeAll(State.@Nullable Active active) {
        while (active != null) {
            invoke(active.token);
            active = active.rest;
        }
    }

    private static void invoke(Cancellable token) {
        try {
            token.cancel();
        } catch (Exception e) {
            UncaughtExceptionHandler.logOrRethrow(e);
        }
    }

    sealed interface State {
        record Active(
            Cancellable token,
            long order,
            @Nullable Active rest
        ) implements State {}

        record Cancelled() implements State {
            static final Cancelled INSTANCE = new Cancelled();
        }

        record Completed() implements State {
            static final Completed INSTANCE = new Completed();
        }
    }
}
