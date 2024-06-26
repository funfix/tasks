package org.funfix.tasks;

import org.jspecify.annotations.NullMarked;

import java.io.Serializable;

/**
 * Represents a callback that will be invoked when a task completes.
 * <p>
 * A task can complete either successfully with a value, or with an exception,
 * or it can be cancelled.
 * </p><p>
 * MUST BE idempotent AND thread-safe.
 * </p>
 * @param <T> is the type of the value that the task will complete with
 */
@NullMarked
public interface CompletionListener<T> extends Serializable {
    /**
     * Must be called when the task completes successfully.
     *
     * @param value is the successful result of the task, to be signaled
     *
     * @return true if the listener was successfully triggered, or
     *        false if the listener was already triggered previously
     */
    boolean tryOnSuccess(T value);

    /**
     * Must be called when the task completes with an exception.
     *
     * @param e is the exception that the task failed with
     *
     * @return true if the listener was successfully triggered, or
     *       false if the listener was already triggered previously
     */
    boolean tryOnFailure(Throwable e);

    /**
     * Must be called when the task is cancelled.
     *
     * @return true if the listener was successfully triggered, or
     *       false if the listener was already triggered previously
     */
    boolean tryOnCancel();

    /**
     * Convenience method for calling {@link #tryOnSuccess} without
     * having to deal with the return value.
     *
     * @param value is the successful result of the task, to be signaled
     *
     * @throws IllegalStateException if the handler was already
     * completed previously
     */
    default void onSuccess(final T value) {
        if (!tryOnSuccess(value))
            throw new IllegalStateException(
                "CompletionHandler was already completed, cannot call `success`"
            );
    }

    /**
     * Convenience method for calling {@link #tryOnFailure} without
     * having to deal with the return value.
     *
     * @param e is the exception that the task failed with
     *
     * @throws IllegalStateException if the handler was already
     * completed previously
     */
    default void onFailure(final Throwable e) {
        if (!tryOnFailure(e))
            throw new IllegalStateException(
                "CompletionHandler was already completed, cannot call `failure`",
                e
            );
    }

    /**
     * Convenience method for calling {@link #tryOnCancel} without
     * having to deal with the return value.
     *
     * @throws IllegalStateException if the handler was already
     * completed previously
     */
    default void onCancel() {
        if (!tryOnCancel())
            throw new IllegalStateException(
                "CompletionHandler was already completed, cannot call `cancel`"
            );
    }
}
