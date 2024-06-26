package org.funfix.tasks;

import org.jspecify.annotations.NullMarked;

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
 *   the {@link CompletionListener} callback.</li>
 *   <li>Upon calling {@code cancel}, the {@link CompletionListener} should
 *   still be eventually triggered, if it wasn't already. So all paths,
 *   with cancellation or without, must lead to the {@link CompletionListener} being called.</li>
 * </ol>
 */
@FunctionalInterface
@NullMarked
public interface Cancellable {
    /**
     * Triggers (idempotent) cancellation.
     */
    void cancel();

    /**
     * A {@code Cancellable} instance that does nothing.
     */
    Cancellable EMPTY = () -> { };
}
