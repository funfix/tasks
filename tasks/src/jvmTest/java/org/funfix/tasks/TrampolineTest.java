package org.funfix.tasks;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TrampolineTest {
    Runnable recursiveRunnable(int level, int maxLevel, Runnable onComplete) {
        return () -> {
            if (maxLevel >= level)
                TaskExecutors.trampoline().execute(onComplete);
            else
                TaskExecutors.trampoline().execute(recursiveRunnable(level + 1, maxLevel, onComplete));
        };
    }

    @Test
    void testDepth() {
        final boolean[] wasExecuted = { false };
        TaskExecutors.trampoline().execute(recursiveRunnable(
                0, 10000, () -> wasExecuted[0] = true
        ));
        assertTrue(wasExecuted[0]);
    }

    @Test
    void testBreath() {
        final int[] calls = { 0 };
        TaskExecutors.trampoline().execute(() -> {
            for (int i = 0; i < 10000; i++) {
                TaskExecutors.trampoline().execute(() -> calls[0]++);
            }
        });
        assertEquals(10000, calls[0]);
    }
}
