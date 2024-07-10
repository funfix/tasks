package org.funfix.tasks;

import org.jspecify.annotations.NullMarked;
import java.util.concurrent.CompletableFuture;

@NullMarked
public record CancellableFuture<T>(
        Cancellable cancellable,
        CompletableFuture<? extends T> future
) {}
