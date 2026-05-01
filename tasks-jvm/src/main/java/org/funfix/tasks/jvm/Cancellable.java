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
 * The contract for {@code cancel} is:
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
    private final AtomicReference<@Nullable State> ref;

    MutableCancellable(final @Nullable Cancellable initialRef) {
        ref = new AtomicReference<>(new State.Active(initialRef, 0, null));
    }

    MutableCancellable() {
        this(null);
    }

    /**
     * Cancels all registered cancellation tokens.
     * <p>
     * Tries to execute the finalizers on the same thread, for as long as
     * possible. Normally, `Cancellation` tokens should not be expensive
     * (non-blocking), but we can't force this via the compiler, and it's
     * best to avoid concurrency issues if possible. Although, note that
     * this is not a contract that we can provide. E.g., if the task
     * was canceled, we're forced to cancel future cancellation tokens
     * on the thread calling `register`.
     */
    @Override
    public void cancel() {
        while (true) {
            final var current = ref.get();
            if (current instanceof State.Active active) {
                final var update = new State.Cancelling(
                    // Adding a dummy that preserves the `order` for incoming
                    // subscriptions.
                    new State.Active(null, active.order, null)
                );
                if (ref.compareAndSet(current, update)) {
                    // NOTE: this will eventually set the state to Cancelled
                    // (after managing to cancel all tokens)
                    startCancellationOfEverything(active);
                    return;
                }
            } else {
                return;
            }
        }
    }

    private void startCancellationOfEverything(State.@Nullable Active active) {
        while (active != null) {
            if (active.token != null) {
                invoke(active.token);
            }
            active = active.rest;
            // Tries fetching more tokens, since the state may have been updated
            if (active == null) {
                // Kind of hacky, but we need the loop due to the CAS
                while (true) {
                    final var current = ref.get();
                    if (current instanceof State.Cancelling cancelling) {
                        // We could have newly registered cancellable references.
                        // If we have, then active != null and will get processed.
                        active = cancelling.toCancel;
                        final var update = active == null
                            // If not, closing the loop with a final update
                            ? State.Cancelled.INSTANCE
                            : new State.Cancelling(null);
                        // If CAS succeeds, we break from the loop
                        // otherwise a concurrent update happened, so continue
                        if (ref.compareAndSet(current, update)) {
                            break;
                        }
                    } else {
                        // Once in `Closing`, concurrent updates are only allowed
                        // to go Closing -> Closing
                        final var name = current != null ? current.getClass().getName() : "null";
                        throw new IllegalStateException("Bug — found: " + name);
                    }
                }
            }
        }
    }

    /**
     * The purpose of this method is to be called when invoking the methods
     * on {@code CompletionCallback} in order to prevent leaks.
     * <p>
     * NOTE: in case a concurrent `cancel()` has already happened,
     * then `complete()` becomes a NO-OP.
     */
    void complete() {
        while (true) {
            final var current = ref.get();
            if (current instanceof State.Active) {
                if (ref.compareAndSet(current, State.Completed.INSTANCE)) {
                    return;
                }
            } else {
                return;
            }
        }
    }

    public @Nullable Cancellable register(Cancellable token) {
        Objects.requireNonNull(token, "token");
        while (true) {
            final var current = ref.get();
            if (current instanceof State.Active active) {
                final var update = active.register(token);
                if (ref.compareAndSet(current, update)) {
                    return () -> unregister(update.order);
                }
            } else if (current instanceof State.Cancelling cancelling) {
                final var update = cancelling.register(token);
                if (ref.compareAndSet(current, update)) {
                    return null;
                }
            } else if (current instanceof State.Cancelled) {
                invoke(token);
                return null;
            } else if (current instanceof State.Completed) {
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
                State.@Nullable Active cursor = active;
                State.@Nullable Active newListReversed = null;
                while (cursor != null) {
                    if (cursor.order != order) {
                        newListReversed = new State.Active(cursor.token, cursor.order, newListReversed);
                    }
                    cursor = cursor.rest;
                }
                // Reversing
                State.@Nullable Active update = null;
                while (newListReversed != null) {
                    update = new State.Active(newListReversed.token, newListReversed.order, update);
                    newListReversed = newListReversed.rest;
                }
                if (update == null) {
                    update = new State.Active(null, 0, null);
                }
                if (ref.compareAndSet(current, update)) {
                    return;
                }
            } else {
                return;
            }
        }
    }

    private static void invoke(Cancellable token) {
        try {
            token.cancel();
        } catch (Throwable e) {
            UncaughtExceptionHandler.logOrRethrow(e);
        }
    }

    sealed interface State {
        record Active(
            @Nullable Cancellable token,
            long order,
            @Nullable Active rest
        ) implements State {
            Active register(Cancellable token) {
                final var newOrder = order + 1;
                return new State.Active(token, newOrder, this);
            }
        }

        record Cancelling(
            @Nullable Active toCancel
        ) implements State {
            Cancelling register(Cancellable token) {
                final var newOrder = toCancel != null ? toCancel.order + 1 : 1;
                return new State.Cancelling(
                    new State.Active(token, newOrder, toCancel)
                );
            }
        }

        record Cancelled() implements State {
            static final Cancelled INSTANCE = new Cancelled();
        }

        record Completed() implements State {
            static final Completed INSTANCE = new Completed();
        }
    }
}
