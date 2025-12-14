package com.justsyncit.network.transfer.pipeline;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ReadStageTest {

    @TempDir
    Path tempDir;

    @Test
    void testProcess_ReadsCorrectData() throws Exception {
        // Setup
        Path file = tempDir.resolve("testfile.txt");
        String content = "Hello World From Pipeline";
        Files.writeString(file, content);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        ReadStage stage = new ReadStage(executor);

        long fileSize = Files.size(file);
        ChunkTask task = new ChunkTask("id", file, 0, (int) fileSize, fileSize);

        // Execute
        ChunkTask result = stage.process(task).join();

        // Verify
        assertNotNull(result.getRawData());
        assertEquals(content, new String(result.getRawData()));

        executor.shutdown();
    }

    @Test
    void testProcess_BufferProperlySized() throws Exception {
        // Setup
        Path file = tempDir.resolve("testfile_large.txt");
        byte[] bytes = new byte[1024];
        Files.write(file, bytes);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        ReadStage stage = new ReadStage(executor);

        // Read just first 100 bytes
        ChunkTask task = new ChunkTask("id", file, 0, 100, 1024);

        // Execute
        ChunkTask result = stage.process(task).join();

        // Verify
        assertEquals(100, result.getRawData().length);

        executor.shutdown();
    }
}
