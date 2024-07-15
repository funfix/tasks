package org.funfix.tasks;

import org.jspecify.annotations.NullMarked;
import java.util.concurrent.CompletableFuture;

@NullMarked
public record CancellableFuture<T>(
        CompletableFuture<? extends T> future,
        Cancellable cancellable
) {}
