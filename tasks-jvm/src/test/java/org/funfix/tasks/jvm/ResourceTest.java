package org.funfix.tasks.jvm;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ResourceTest {
    @Test
    void readAndWriteFromFile() throws Exception {
        try (
            final var file = createTemporaryFile("test", ".txt").acquireBlocking()
        ) {
            try (final var writer = openWriter(file.get()).acquireBlocking()) {
                writer.get().write("----\n");
                writer.get().write("line 1\n");
                writer.get().write("line 2\n");
                writer.get().write("----\n");
            }

            try (final var reader = openReader(file.get()).acquireBlocking()) {
                final var builder = new StringBuilder();
                String line;
                while ((line = reader.get().readLine()) != null) {
                    builder.append(line).append("\n");
                }
                final String content = builder.toString();
                assertEquals(
                    "----\nline 1\nline 2\n----\n",
                    content,
                    "File content should match the written lines"
                );
            }
        }
    }

    @Test
    void useIsInterruptible() throws InterruptedException {
        final var started = new CountDownLatch(1);
        final var latch = new CountDownLatch(1);
        final var wasShutdown = new CountDownLatch(1);
        final var wasInterrupted = new AtomicInteger(0);
        final var wasReleased = new AtomicReference<@Nullable ExitCase>(null);
        final var resource = Resource.fromBlockingIO(() ->
            Resource.Acquired.fromBlockingIO("my resource", wasReleased::set)
        );

        @SuppressWarnings("NullAway")
        final var task = Task.fromBlockingFuture(() -> {
            try {
                resource.useBlocking(res -> {
                    assertEquals("my resource", res);
                    started.countDown();
                    try {
                        latch.await();
                    } catch (InterruptedException e) {
                        wasInterrupted.incrementAndGet();
                        throw e;
                    }
                    return null;
                });
            } catch (InterruptedException e) {
                wasInterrupted.addAndGet(2);
                throw e;
            } finally {
                wasShutdown.countDown();
            }
            return null;
        });

        final var fiber = task.runFiber();
        TimedAwait.latchAndExpectCompletion(started, "started");
        fiber.cancel();
        TimedAwait.latchAndExpectCompletion(wasShutdown, "wasShutdown");
        TimedAwait.fiberAndExpectCancellation(fiber);
        assertEquals(3, wasInterrupted.get(), "wasInterrupted");
        assertEquals(ExitCase.canceled(), wasReleased.get(), "wasReleased");
    }

    Resource<BufferedReader> openReader(File file) {
        return Resource.fromAutoCloseable(() ->
            new BufferedReader(
                new InputStreamReader(
                    new FileInputStream(file),
                    StandardCharsets.UTF_8
                )
            ));
    }

    Resource<BufferedWriter> openWriter(File file) {
        return Resource.fromAutoCloseable(() ->
            new BufferedWriter(
                new OutputStreamWriter(
                    new FileOutputStream(file),
                    StandardCharsets.UTF_8
                )
            ));
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    Resource<File> createTemporaryFile(String prefix, String suffix) {
        return Resource.fromBlockingIO(() -> {
            File tempFile = File.createTempFile(prefix, suffix);
            tempFile.deleteOnExit(); // Ensure it gets deleted on exit
            return Resource.Acquired.fromBlockingIO(
                tempFile,
                ignored -> tempFile.delete()
            );
        });
    }
}
