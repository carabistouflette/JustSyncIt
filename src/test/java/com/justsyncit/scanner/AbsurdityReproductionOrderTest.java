package com.justsyncit.scanner;

import com.justsyncit.hash.Blake3Service;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.stubbing.Answer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AbsurdityReproductionOrderTest {

    @TempDir
    Path tempDir;

    @Test
    public void testChunkOrderIsPreservedInAsyncFileChunker() throws Exception {
        testChunkerOrder(true);
    }

    @Test
    public void testChunkOrderIsPreservedInFixedSizeFileChunker() throws Exception {
        testChunkerOrder(false);
    }

    private void testChunkerOrder(boolean useAsyncChunker) throws Exception {
        // Mock Blake3Service to return the string content as hash
        Blake3Service blake3Service = mock(Blake3Service.class);
        when(blake3Service.hashBuffer(any(byte[].class))).thenAnswer((Answer<String>) invocation -> {
            byte[] data = invocation.getArgument(0);
            return new String(data).trim();
        });

        // Setup file with predictable content: "0000 0001 0002 ..."
        // Each chunk will be 5 bytes (4 digits + space)
        int chunks = 100;
        int chunkSize = 5;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chunks; i++) {
            sb.append(String.format("%04d ", i));
        }
        Path testFile = tempDir.resolve("ordered_chunks.dat");
        Files.writeString(testFile, sb.toString());

        // Setup chunking options
        ChunkingOptions options = new ChunkingOptions();
        options.withChunkSize(chunkSize)
                .withMaxConcurrentChunks(10) // Allow concurrency to trigger race conditions
                .withUseAsyncIO(true);

        FileChunker chunker;
        if (useAsyncChunker) {
            chunker = AsyncFileChunkerImpl.create(blake3Service);
        } else {
            chunker = FixedSizeFileChunker.create(blake3Service);
        }

        // Execute chunking
        CompletableFuture<FileChunker.ChunkingResult> future = chunker.chunkFile(testFile, options);
        FileChunker.ChunkingResult result = future.get();

        // Verify result
        List<String> chunkHashes = result.getChunkHashes();
        assertEquals(chunks, chunkHashes.size(), "Should have correct number of chunks");

        // Check order
        List<String> expectedHashes = IntStream.range(0, chunks)
                .mapToObj(i -> String.format("%04d", i))
                .collect(Collectors.toList());

        System.out.println("Expected: " + expectedHashes);
        System.out.println("Actual:   " + chunkHashes);

        assertEquals(expectedHashes, chunkHashes,
                "Chunk hashes should be in sequential order. " +
                        "If they are shuffled, the file cannot be reconstructed correcty!");

        if (chunker instanceof AsyncFileChunker) {
            ((AsyncFileChunker) chunker).closeAsync().join();
        } else if (chunker instanceof FixedSizeFileChunker) {
            ((FixedSizeFileChunker) chunker).close();
        }
    }
}
