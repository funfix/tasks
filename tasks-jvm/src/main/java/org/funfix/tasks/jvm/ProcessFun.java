package org.funfix.tasks.jvm;

import org.jspecify.annotations.Nullable;

/**
 * The equivalent of {@code Function<In, Out>} for processing I/O that can
 * throw exceptions.
 */
@FunctionalInterface
public interface ProcessFun<In extends @Nullable Object, Out extends  @Nullable Object> {
    Out call(In input) throws Exception;
}
