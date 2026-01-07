package com.justsyncit.scanner;

import com.justsyncit.ServiceFactory;
import com.justsyncit.hash.Blake3Service;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class ScalabilityAbsurdityTest {

    @TempDir
    Path tempDir;

    @Test
    public void testBoundedSubmissionHandlesManyChunks() throws Exception {
        // Create a file that would generate A LOT of chunks with a tiny chunk size
        Path file = tempDir.resolve("scalability_test.dat");
        int FILE_SIZE = 10 * 1024 * 1024; // 10MB
        int CHUNK_SIZE = 4096; // 4KB chunks -> ~2560 chunks

        // Write random data
        byte[] data = new byte[FILE_SIZE];
        new java.util.Random().nextBytes(data);
        Files.write(file, data);

        Blake3Service blake3Service = new ServiceFactory().createBlake3Service();
        FixedSizeFileChunker chunker = FixedSizeFileChunker.create(
                blake3Service,
                ByteBufferPool.create(),
                CHUNK_SIZE);
        chunker.setMaxConcurrentOperations(100); // Strict limit

        // Measure memory before (rough approx)
        Runtime rt = Runtime.getRuntime();
        long usedBefore = rt.totalMemory() - rt.freeMemory();
        System.out.println("Used Memory Before: " + (usedBefore / 1024 / 1024) + " MB");

        ChunkingOptions options = new ChunkingOptions();
        options.withUseAsyncIO(true).withChunkSize(CHUNK_SIZE);

        long startTime = System.currentTimeMillis();

        // Execute chunking
        FileChunker.ChunkingResult result = chunker.chunkFile(file, options).get(30, TimeUnit.SECONDS);

        long endTime = System.currentTimeMillis();
        System.out.println("Time taken: " + (endTime - startTime) + " ms");

        // If the implementation is NOT bounded, this might have spiked memory usage
        // significantly
        // or taken a long time to setup the graph.
        // With an 80k loop, the old implementation would create 80k futures instantly.
        // The fix should show smooth execution.

        long usedAfter = rt.totalMemory() - rt.freeMemory();
        System.out.println("Used Memory After: " + (usedAfter / 1024 / 1024) + " MB");

        if (!result.isSuccess()) {
            System.err.println("Chunking failed: " + result.getError());
            if (result.getError() != null) {
                result.getError().printStackTrace();
            }
        }
        assertTrue(result.isSuccess());
        assertEquals(2560, result.getChunkCount());
    }
}
