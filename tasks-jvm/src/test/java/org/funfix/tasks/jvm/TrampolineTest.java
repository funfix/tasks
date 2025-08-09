package org.funfix.tasks.jvm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TrampolineTest {
    Runnable recursiveRunnable(int level, int maxLevel, Runnable onComplete) {
        return () -> {
            if (maxLevel >= level)
                Trampoline.execute(onComplete);
            else
                Trampoline.execute(recursiveRunnable(level + 1, maxLevel, onComplete));
        };
    }

    @Test
    void tasksAreExecutedInFIFOOrder() {
        final int[] calls = { 0 };
        Trampoline.execute(() -> {
            for (int i = 0; i < 100; i++) {
                Trampoline.execute(() -> calls[0]++);
            }
        });
        assertEquals(100, calls[0]);
    }

    @Test
    void testDepth() {
        final boolean[] wasExecuted = { false };
        Trampoline.execute(recursiveRunnable(
            0, 10000, () -> wasExecuted[0] = true
        ));
        assertTrue(wasExecuted[0]);
    }

    @Test
    void testBreath() {
        final int[] calls = { 0 };
        Trampoline.execute(() -> {
            for (int i = 0; i < 10000; i++) {
                Trampoline.execute(() -> calls[0]++);
            }
        });
        assertEquals(10000, calls[0]);
    }
}
