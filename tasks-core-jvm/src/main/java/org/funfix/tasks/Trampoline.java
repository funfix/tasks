package org.funfix.tasks;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.LinkedList;
import java.util.concurrent.Executor;

@NullMarked
final class Trampoline {
    private Trampoline() {}

    private static final ThreadLocal<@Nullable LinkedList<Runnable>> queue =
            new ThreadLocal<>();

    private static void eventLoop() {
        while (true) {
            final var current = queue.get();
            if (current == null) {
                return;
            }
            final var next = current.pollFirst();
            if (next == null) {
                return;
            }
            try {
                next.run();
            } catch (final Exception e) {
                UncaughtExceptionHandler.logOrRethrowException(e);
            }
        }
    }

    public static final Executor INSTANCE =
            command -> {
                var current = queue.get();
                if (current == null) {
                    current = new LinkedList<>();
                    current.add(command);
                    queue.set(current);
                    try {
                        eventLoop();
                    } finally {
                        queue.remove();
                    }
                } else {
                    current.add(command);
                }
            };

    public static void execute(final Runnable command) {
        INSTANCE.execute(command);
    }
}
