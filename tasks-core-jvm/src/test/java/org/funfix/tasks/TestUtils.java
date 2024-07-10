package org.funfix.tasks;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public class TestUtils {
    static <E extends Exception> void repeatTest(int times, DelayedFun<@Nullable Void, E> fun)
        throws E {

        for (int i = 0; i < times; i++) {
            fun.invoke();
        }
    }
}
