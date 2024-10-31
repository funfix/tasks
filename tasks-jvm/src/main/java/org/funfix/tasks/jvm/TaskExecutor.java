package org.funfix.tasks.jvm;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.NullMarked;
import java.util.concurrent.Executor;

@NullMarked
@ApiStatus.Internal
interface TaskExecutor extends Executor {
    void resumeOnExecutor(Runnable runnable);

    static TaskExecutor from(final Executor executor) {
        if (executor instanceof TaskExecutor) {
            return (TaskExecutor) executor;
        } else {
            return new TaskExecutorWithForkedResume(executor);
        }
    }
}

@NullMarked
final class TaskExecutorWithForkedResume implements TaskExecutor {
    private final Executor executor;

    TaskExecutorWithForkedResume(Executor executor) {
        this.executor = executor;
    }

    @Override
    public void execute(Runnable command) {
        executor.execute(() -> {
            final int oldLocalHash = localExecutor.get();
            localExecutor.set(System.identityHashCode(executor));
            try {
                command.run();
            } finally {
                localExecutor.set(oldLocalHash);
            }
        });
    }

    @Override
    public void resumeOnExecutor(Runnable runnable) {
        final int localHash = localExecutor.get();
        if (localHash == System.identityHashCode(executor)) {
            Trampoline.INSTANCE.execute(runnable);
        } else {
            execute(runnable);
        }
    }

    private static final ThreadLocal<Integer> localExecutor =
        ThreadLocal.withInitial(() -> 0);
}
