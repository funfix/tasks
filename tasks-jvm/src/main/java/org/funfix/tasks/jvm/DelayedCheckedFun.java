package org.funfix.tasks.jvm;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
@FunctionalInterface
public interface DelayedCheckedFun<T extends @Nullable Object, E extends Exception> {
    T invoke() throws E;
}
