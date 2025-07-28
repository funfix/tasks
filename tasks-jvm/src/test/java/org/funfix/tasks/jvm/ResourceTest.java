package org.funfix.tasks.jvm;

import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.nio.charset.StandardCharsets;

@NullMarked
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
            return new Resource.Closeable<>(
                tempFile,
                ignored -> tempFile.delete()
            );
        });
    }
}
