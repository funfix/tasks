package org.funfix.tasks.jvm;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.concurrent.Executor;

/**
 * INTERNAL API.
 * <p>
 * <strong>INTERNAL API:</strong> Internal apis are subject to change or removal
 * without any notice. When code depends on internal APIs, it is subject to
 * breakage between minor version updates.
 */
@ApiStatus.Internal
final class Trampoline {
    private Trampoline() {}

    private static final ThreadLocal<@Nullable ArrayDeque<Runnable>> queue =
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
            } catch (final Throwable e) {
                UncaughtExceptionHandler.logOrRethrow(e);
            }
        }
    }

    public static final Executor INSTANCE =
        new TaskExecutor() {
            @Override
            public void resumeOnExecutor(Runnable runnable) {
                execute(runnable);
            }

            @Override
            public void execute(Runnable command) {
                var current = queue.get();
                if (current == null) {
                    current = new ArrayDeque<>();
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
            }
        };

    public static void forkAll(final Executor executor) {
        final var current = queue.get();
        if (current == null) return;

        final var copy = new ArrayDeque<>(current);
        executor.execute(() -> Trampoline.execute(() -> {
            while (!copy.isEmpty()) {
                final var next = copy.pollFirst();
                Trampoline.execute(next);
            }
        }));
    }

    public static void execute(final Runnable command) {
        INSTANCE.execute(command);
    }
}
