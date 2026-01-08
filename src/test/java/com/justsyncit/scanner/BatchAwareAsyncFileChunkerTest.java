package com.justsyncit.scanner;

import com.justsyncit.scanner.FileChunker.ChunkingResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BatchAwareAsyncFileChunkerTest {

    private AsyncFileChunker delegate;
    private AsyncBatchProcessor batchProcessor;
    private BatchConfiguration batchConfig;
    private BatchAwareAsyncFileChunker chunker;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        delegate = mock(AsyncFileChunker.class);
        batchProcessor = mock(AsyncBatchProcessor.class);
        batchConfig = new BatchConfiguration();
        chunker = new BatchAwareAsyncFileChunker(delegate, batchProcessor, batchConfig);
    }

    @Test
    void testActualHashReturned() throws Exception {
        Path testFile = tempDir.resolve("test.txt");
        Files.writeString(testFile, "Hello World");

        // Mock batch processor to return success
        BatchOperationResult successResult = new BatchOperationResult(
                "op-id",
                BatchOperationType.CHUNKING,
                java.time.Instant.now(),
                java.time.Instant.now().plusMillis(100),
                1,
                0,
                12L,
                null,
                java.util.Map.of(
                        "fileHash", "real-hash-123",
                        "chunkHashes", List.of("chunk-1"),
                        "chunkCount", 1,
                        "totalSize", 12L),
                null);
        when(batchProcessor.processOperation(any(), any()))
                .thenReturn(CompletableFuture.completedFuture(successResult));

        ChunkingResult result = chunker.chunkFileAsync(testFile, new ChunkingOptions()).join();

        String returnedHash = result.getFileHash();
        assertFalse(returnedHash.startsWith("batch-hash-"),
                "Should return actual hash, not placeholder: " + returnedHash);
    }

    @Test
    void testMemoryRequirementSanity() throws Exception {
        // Create a large dummy file (sparse) or just rely on file size reporting
        Path largeFile = tempDir.resolve("large.bin");
        // scalable file creation: valid java code
        try (var ch = java.nio.channels.FileChannel.open(largeFile, java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.WRITE)) {
            ch.position(1024L * 1024L * 1024L); // 1GB
            ch.write(java.nio.ByteBuffer.wrap(new byte[] { 0 }));
        }

        when(batchProcessor.processOperation(any(), any()))
                .thenReturn(CompletableFuture.completedFuture(null)); // Result doesn't matter for this test

        try {
            chunker.chunkFileAsync(largeFile, new ChunkingOptions()).join();
        } catch (Exception ignored) {
            // we catch because result conversion might fail on null return, but we care
            // about the CALL
        }

        ArgumentCaptor<BatchOperation> opCaptor = ArgumentCaptor.forClass(BatchOperation.class);
        verify(batchProcessor).processOperation(opCaptor.capture(), any());

        BatchOperation op = opCaptor.getValue();
        long memoryReq = op.getResourceRequirements().memoryBytes;
        long fileSize = Files.size(largeFile);

        // absurdity: memoryReq ~= fileSize.
        // We expect memoryReq << fileSize (e.g. around default chunk size *
        // concurrency)

        assertTrue(memoryReq < fileSize / 2,
                String.format("Memory requirement (%d) should be much less than file size (%d)", memoryReq, fileSize));
    }
}
