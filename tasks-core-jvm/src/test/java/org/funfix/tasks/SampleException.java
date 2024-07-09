package org.funfix.tasks;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class SampleException extends Exception {
    public SampleException(@Nullable String message) {
        super(message);
    }
}
