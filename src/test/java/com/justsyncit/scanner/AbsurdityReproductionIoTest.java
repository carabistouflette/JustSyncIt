package com.justsyncit.scanner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AbsurdityReproductionIoTest {

    @TempDir
    Path tempDir;

    @Test
    public void testIoRequirementIsNotAbsurd() throws Exception {
        // Create a Mock AsyncFileChunker (delegate)
        AsyncFileChunker delegate = mock(AsyncFileChunker.class);

        // Mock BatchProcessor
        AsyncBatchProcessor batchProcessor = mock(AsyncBatchProcessor.class);
        when(batchProcessor.processOperation(any(), any()))
                .thenReturn(CompletableFuture.completedFuture(mock(BatchOperationResult.class)));

        // Real BatchConfiguration
        BatchConfiguration config = new BatchConfiguration();

        // Create the chunker under test
        BatchAwareAsyncFileChunker chunker = new BatchAwareAsyncFileChunker(delegate, batchProcessor, config);

        // Create a large sparse file (100 GB)
        // 100 GB / 10 seconds = 10 GB/s = 10,000 MB/s requirement in current absurd
        // logic
        Path hugeFile = tempDir.resolve("huge_file.dat");
        try (RandomAccessFile raf = new RandomAccessFile(hugeFile.toFile(), "rw")) {
            raf.setLength(100L * 1024 * 1024 * 1024);
        }

        // Run chunking
        chunker.chunkFileAsync(hugeFile, new ChunkingOptions(), mock(CompletionHandler.class));

        // Capture the BatchOperation
        ArgumentCaptor<BatchOperation> captor = ArgumentCaptor.forClass(BatchOperation.class);
        verify(batchProcessor).processOperation(captor.capture(), any());

        BatchOperation operation = captor.getValue();
        int ioReq = operation.getResourceRequirements().ioBandwidthMBps;

        System.out.println("Calculated I/O Requirement: " + ioReq + " MB/s");

        // Assert that the requirement is "Sanity Checked" i.e., less than 2000 MB/s
        // (approx 2GB/s, high end NVMe)
        // The current absurd logic will produce ~10,000 MB/s
        assertTrue(ioReq < 2000,
                "I/O requirement is absurdly high: " + ioReq + " MB/s. logic assumes file must be processed in 10s!");
    }
}
