package org.funfix.tasks;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TrampolineTest {
    Runnable recursiveRunnable(int level, int maxLevel, Runnable onComplete) {
        return () -> {
            if (maxLevel >= level)
                ThreadPools.TRAMPOLINE.execute(onComplete);
            else
                ThreadPools.TRAMPOLINE.execute(recursiveRunnable(level + 1, maxLevel, onComplete));
        };
    }

    @Test
    void testDepth() {
        final boolean[] wasExecuted = { false };
        ThreadPools.TRAMPOLINE.execute(recursiveRunnable(
                0, 10000, () -> wasExecuted[0] = true
        ));
        assertTrue(wasExecuted[0]);
    }

    @Test
    void testBreath() {
        final int[] calls = { 0 };
        ThreadPools.TRAMPOLINE.execute(() -> {
            for (int i = 0; i < 10000; i++) {
                ThreadPools.TRAMPOLINE.execute(() -> calls[0]++);
            }
        });
        assertEquals(10000, calls[0]);
    }
}
