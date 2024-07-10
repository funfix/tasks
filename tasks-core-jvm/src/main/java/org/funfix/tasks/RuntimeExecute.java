package org.funfix.tasks;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
@FunctionalInterface
public interface RuntimeExecute {
    Cancellable invoke(Runnable command, @Nullable Runnable onComplete);
}
