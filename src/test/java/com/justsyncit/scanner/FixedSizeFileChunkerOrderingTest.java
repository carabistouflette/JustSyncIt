package com.justsyncit.scanner;

import com.justsyncit.hash.Blake3Service;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FixedSizeFileChunkerOrderingTest {

    @TempDir
    Path tempDir;

    @Test
    void testChunkOrderingWithDelays() throws IOException, ExecutionException, InterruptedException {
        // Setup
        Blake3Service mockHashService = mock(Blake3Service.class);
        FixedSizeFileChunker chunker = FixedSizeFileChunker.create(mockHashService);
        int chunkSize = 64 * 1024;
        chunker.setChunkSize(chunkSize);

        // Create a file large enough to trigger Async I/O (> 1MB)
        int numChunks = 20; // 20 * 64KB = 1.25MB
        Path testFile = tempDir.resolve("ordering_test.bin");
        byte[] data = new byte[chunkSize * numChunks];

        // Mark each chunk with its index in the first byte
        for (int i = 0; i < numChunks; i++) {
            data[i * chunkSize] = (byte) i;
        }
        Files.write(testFile, data);

        // Prepare expected hashes
        String[] expectedHashes = new String[numChunks];
        for (int i = 0; i < numChunks; i++) {
            expectedHashes[i] = "hash_" + i;
        }

        when(mockHashService.hashBuffer(any(byte[].class))).thenAnswer(new Answer<String>() {
            @Override
            public String answer(InvocationOnMock invocation) throws Throwable {
                byte[] bytes = invocation.getArgument(0);
                if (bytes.length > 0) {
                    int chunkIndex = bytes[0];
                    if (chunkIndex == 0) {
                        // Chunk 0: Delay it so it finishes LAST
                        Thread.sleep(1000);
                    }
                    if (chunkIndex >= 0 && chunkIndex < numChunks) {
                        return expectedHashes[chunkIndex];
                    }
                }
                return "unknown";
            }
        });

        // Execute
        FileChunker.ChunkingOptions options = new FileChunker.ChunkingOptions().withUseAsyncIO(true);
        // Force concurrency > 1
        chunker.setMaxConcurrentOperations(4);

        FileChunker.ChunkingResult result = chunker.chunkFile(testFile, options).get();

        // Verify
        List<String> hashes = result.getChunkHashes();
        assertEquals(numChunks, hashes.size(), "Should have correct number of chunks");

        // The bug causes the delayed chunk (Chunk 0) to be appended last.
        // We assert that it SHOULD be at index 0.
        // This assertion should fail if the bug exists.
        assertEquals(expectedHashes[0], hashes.get(0), "First chunk hash should be first in the list");
    }
}
