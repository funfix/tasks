package org.funfix.tasks;

import org.jspecify.annotations.NullMarked;

import java.util.concurrent.CompletionStage;

/**
 * This is a wrapper around a {@link CompletionStage} with a
 * {@link Cancellable} reference attached.
 * <p>
 * A standard Java {@link CompletionStage} is not connected to its
 * asynchronous task and cannot be cancelled. Thus, if we want to cancel
 * a task, we need to keep a reference to a {@link Cancellable} object that
 * can do the job.
 */
@NullMarked
public record CancellableCompletionStage<T>(
    CompletionStage<T> completionStage,
    Cancellable cancellable
) {}
