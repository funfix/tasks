package org.funfix.tasks.jvm;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@NullMarked
public class TaskExecutorTest {
    @Test
    void trampolineIsTaskExecutor() {
        final var ec1 = TaskExecutors.trampoline();
        final var ec2 = TaskExecutor.from(ec1);
        assertEquals(ec1, ec2);
    }

    @Test
    void trampolineResumeIsExecute() {
        final var count = new AtomicInteger(0);
        final var ec = TaskExecutor.from(TaskExecutors.trampoline());

        ec.execute(() -> {
            count.incrementAndGet();
            ec.resumeOnExecutor(count::incrementAndGet);
            count.incrementAndGet();
            ec.resumeOnExecutor(() -> {
                count.incrementAndGet();
                ec.resumeOnExecutor(() -> {
                    count.incrementAndGet();
                    ec.resumeOnExecutor(count::incrementAndGet);
                });
                count.incrementAndGet();
            });
        });

        assertEquals(7, count.get());
    }

    @Test
    void sharedBlockingIOIsTaskExecutorOnOlderJava() {
        final var sysProp = SysProp.withVirtualThreads(false);
        assertFalse(VirtualThreads.areVirtualThreadsSupported());
        try {
            final var ec1 = TaskExecutors.sharedBlockingIO();
            final var ec2 = TaskExecutor.from(ec1);
            assertEquals(ec1, ec2);
        } finally {
            sysProp.close();
        }
    }

    @Test
    void sharedBlockingIOIsTaskExecutorOnJava21() {
        assumeTrue(VirtualThreads.areVirtualThreadsSupported(), "Requires Java 21+");
        final var ec1 = TaskExecutors.sharedBlockingIO();
        final var ec2 = TaskExecutor.from(ec1);
        assertEquals(ec1, ec2);
    }

    private void testThreadName() throws InterruptedException {
        final TaskExecutor executor = TaskExecutor.from(TaskExecutors.sharedBlockingIO());
        final var threadName = new AtomicReference<@Nullable String>(null);
        final var isComplete = new CountDownLatch(1);
        executor.execute(() -> {
            threadName.set(Thread.currentThread().getName());
            isComplete.countDown();
        });

        TimedAwait.latchAndExpectCompletion(isComplete, "isComplete");
        assertTrue(
            Objects.requireNonNull(threadName.get()).startsWith("tasks-io-"),
            "\"" + threadName.get() + "\".startsWith(\"tasks-io-\")"
        );
    }

    @Test
    void testThreadNameOlderJava() throws InterruptedException {
        final var sysProp = SysProp.withVirtualThreads(false);
        assertFalse(VirtualThreads.areVirtualThreadsSupported());
        try {
            testThreadName();
        } finally {
            sysProp.close();
        }
    }

    @Test
    void testThreadNameJava21() throws InterruptedException {
        assumeTrue(VirtualThreads.areVirtualThreadsSupported(), "Requires Java 21+");
        testThreadName();
    }

    private void testThreadNameNested() throws InterruptedException {
        final TaskExecutor executor = TaskExecutor.from(TaskExecutors.sharedBlockingIO());
        final var threadName1 = new AtomicReference<@Nullable String>(null);
        final var threadName2 = new AtomicReference<@Nullable String>(null);
        final var isComplete = new CountDownLatch(1);

        executor.execute(() -> {
            threadName1.set(Thread.currentThread().getName());
            executor.execute(() -> {
                threadName2.set(Thread.currentThread().getName());
                isComplete.countDown();
            });
        });

        TimedAwait.latchAndExpectCompletion(isComplete, "isComplete");
        assertTrue(
            Objects.requireNonNull(threadName1.get()).startsWith("tasks-io-"),
            "\"" + threadName1.get() + "\".startsWith(\"tasks-io-\")"
        );
        assertTrue(
            Objects.requireNonNull(threadName1.get()).startsWith("tasks-io-"),
            "\"" + threadName1.get() + "\".startsWith(\"tasks-io-\")"
        );
        assertNotSame(
            threadName1.get(),
            threadName2.get(),
            "threadName1.get() != threadName2.get()"
        );
    }

    @Test
    void testThreadNameNestedOlderJava() throws InterruptedException {
        final var sysProp = SysProp.withVirtualThreads(false);
        assertFalse(VirtualThreads.areVirtualThreadsSupported());
        try {
            testThreadNameNested();
        } finally {
            sysProp.close();
        }
    }

    @Test
    void testThreadNameNestedJava21() throws InterruptedException {
        assumeTrue(VirtualThreads.areVirtualThreadsSupported(), "Requires Java 21+");
        testThreadNameNested();
    }

    private void testResumeCanSwitchThread() throws InterruptedException {
        final TaskExecutor executor = TaskExecutor.from(TaskExecutors.sharedBlockingIO());
        final var threadName1 = new AtomicReference<@Nullable String>(null);
        final var threadName2 = new AtomicReference<@Nullable String>(null);
        final var isComplete = new CountDownLatch(1);

        executor.resumeOnExecutor(() -> {
            threadName1.set(Thread.currentThread().getName());
            executor.resumeOnExecutor(() -> {
                threadName2.set(Thread.currentThread().getName());
                isComplete.countDown();
            });
        });

        TimedAwait.latchAndExpectCompletion(isComplete, "isComplete");
        for (final var name : new String[] {
            Objects.requireNonNull(threadName1.get()),
            Objects.requireNonNull(threadName2.get())
        }) {
            assertTrue(
                Objects.requireNonNull(name).startsWith("tasks-io-"),
                "\"" + name + "\".startsWith(\"tasks-io-\")"
            );
        }
        assertSame(threadName1.get(), threadName2.get());
    }

    @Test
    void testResumeCanSwitchThreadOlderJava() throws InterruptedException {
        final var sysProp = SysProp.withVirtualThreads(false);
        assertFalse(VirtualThreads.areVirtualThreadsSupported());
        try {
            testResumeCanSwitchThread();
        } finally {
            sysProp.close();
        }
    }

    @Test
    void testResumeCanSwitchThreadJava21() throws InterruptedException {
        assumeTrue(VirtualThreads.areVirtualThreadsSupported(), "Requires Java 21+");
        testResumeCanSwitchThread();
    }

    private void resumeCanStayOnTheSameThread() throws InterruptedException {
        final TaskExecutor executor = TaskExecutor.from(TaskExecutors.sharedBlockingIO());
        final var threadName1 = new AtomicReference<@Nullable String>(null);
        final var threadName2 = new AtomicReference<@Nullable String>(null);
        final var isComplete = new CountDownLatch(1);

        executor.execute(() -> {
            threadName1.set(Thread.currentThread().getName());
            executor.resumeOnExecutor(() -> {
                threadName2.set(Thread.currentThread().getName());
                isComplete.countDown();
            });
        });

        TimedAwait.latchAndExpectCompletion(isComplete, "isComplete");
        for (final var name : new String[] {
            Objects.requireNonNull(threadName1.get()),
            Objects.requireNonNull(threadName2.get())
        }) {
            assertTrue(
                Objects.requireNonNull(name).startsWith("tasks-io-"),
                "\"" + name + "\".startsWith(\"tasks-io-\")"
            );
        }
        assertSame(threadName1.get(), threadName2.get());
    }

    @Test
    void resumeCanStayOnTheSameThreadOlderJava() throws InterruptedException {
        final var sysProp = SysProp.withVirtualThreads(false);
        assertFalse(VirtualThreads.areVirtualThreadsSupported());
        try {
            resumeCanStayOnTheSameThread();
        } finally {
            sysProp.close();
        }
    }

    @Test
    void resumeCanStayOnTheSameThreadJava21() throws InterruptedException {
        assumeTrue(VirtualThreads.areVirtualThreadsSupported(), "Requires Java 21+");
        resumeCanStayOnTheSameThread();
    }
}
