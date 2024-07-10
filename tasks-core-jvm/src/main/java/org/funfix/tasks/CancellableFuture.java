package org.funfix.tasks;

import java.util.concurrent.CompletionStage;

public record CancellableFuture<T>(
        Cancellable cancellable,
        CompletionStage<? extends T> future
) {}
