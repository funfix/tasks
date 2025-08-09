package org.funfix.tasks.jvm;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.Executor;

@ApiStatus.Internal
interface TaskExecutor extends Executor {
    void resumeOnExecutor(Runnable runnable);

    static TaskExecutor from(final Executor executor) {
        if (executor instanceof TaskExecutor te) {
            return te;
        } else {
            return new TaskExecutorWithForkedResume(executor);
        }
    }
}

@ApiStatus.Internal
final class TaskLocalContext {
    static void signalTheStartOfBlockingCall() {
        // clears the trampoline first
        final var executor = localExecutor.get();
        Trampoline.forkAll(executor);
    }

    static boolean isCurrentExecutor(final TaskExecutor executor) {
        final var currentExecutor = localExecutor.get();
        return currentExecutor == executor;
    }

    static @Nullable TaskExecutor getAndSetExecutor(@Nullable final TaskExecutor executor) {
        final var oldExecutor = localExecutor.get();
        localExecutor.set(executor);
        return oldExecutor;
    }

    private static final ThreadLocal<@Nullable TaskExecutor> localExecutor =
        ThreadLocal.withInitial(() -> null);
}

@ApiStatus.Internal
final class TaskExecutorWithForkedResume implements TaskExecutor {
    private final Executor executor;

    TaskExecutorWithForkedResume(final Executor executor) {
        this.executor = executor;
    }

    @Override
    public void execute(final Runnable command) {
        executor.execute(() -> {
            final var oldExecutor = TaskLocalContext.getAndSetExecutor(this);
            try {
                command.run();
            } finally {
                TaskLocalContext.getAndSetExecutor(oldExecutor);
            }
        });
    }

    @Override
    public void resumeOnExecutor(final Runnable runnable) {
        if (TaskLocalContext.isCurrentExecutor(this)) {
            Trampoline.INSTANCE.execute(runnable);
        } else {
            execute(runnable);
        }
    }
}
