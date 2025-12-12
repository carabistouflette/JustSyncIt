/*
 * JustSyncIt - Backup solution
 * Copyright (C) 2023 JustSyncIt Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.justsyncit.scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Batch-aware implementation of AsyncFileChunker that integrates with the batch
 * processing system.
 * Provides optimized chunking operations for batch processing scenarios.
 * Enhances performance through batch coordination and resource optimization.
 */
public class BatchAwareAsyncFileChunker implements AsyncFileChunker {

    private static final Logger logger = LoggerFactory.getLogger(BatchAwareAsyncFileChunker.class);

    private final AsyncFileChunker delegate;
    private final AsyncBatchProcessor batchProcessor;
    private final BatchConfiguration batchConfig;
    private final AtomicInteger activeBatchOperations;

    /**
     * Creates a new BatchAwareAsyncFileChunker.
     *
     * @param delegate       the underlying async file chunker
     * @param batchProcessor the batch processor for coordination
     * @param batchConfig    the batch configuration
     * @throws IllegalArgumentException if any parameter is null
     */
    public BatchAwareAsyncFileChunker(AsyncFileChunker delegate,
            AsyncBatchProcessor batchProcessor,
            BatchConfiguration batchConfig) {
        if (delegate == null) {
            throw new IllegalArgumentException("Delegate chunker cannot be null");
        }
        if (batchProcessor == null) {
            throw new IllegalArgumentException("Batch processor cannot be null");
        }
        if (batchConfig == null) {
            throw new IllegalArgumentException("Batch configuration cannot be null");
        }

        this.delegate = delegate;
        this.batchProcessor = batchProcessor;
        this.batchConfig = batchConfig;
        this.activeBatchOperations = new AtomicInteger(0);
    }

    @Override
    public void chunkFileAsync(Path file, ChunkingOptions options,
            CompletionHandler<ChunkingResult, Exception> handler) {
        if (file == null) {
            throw new IllegalArgumentException("File cannot be null");
        }
        if (handler == null) {
            throw new IllegalArgumentException("Handler cannot be null");
        }

        // Create batch operation for chunking
        BatchOperation operation = createBatchOperation(file, options);

        // Submit to batch processor
        CompletableFuture<BatchOperationResult> batchFuture = batchProcessor.processOperation(operation,
                new BatchOptions());

        // Handle batch result
        batchFuture.whenComplete((batchResult, throwable) -> {
            if (throwable != null) {
                handler.failed(
                        throwable instanceof Exception ? (Exception) throwable : new RuntimeException(throwable));
            } else if (!batchResult.isSuccess()) {
                Exception error = batchResult.getError();
                if (error != null) {
                    handler.failed(error);
                } else {
                    handler.failed(new RuntimeException("Batch chunking failed: " + batchResult));
                }
            } else {
                // Convert batch result to chunking result
                ChunkingResult chunkingResult = convertBatchOperationResultToChunkingResult(batchResult, file);
                handler.completed(chunkingResult);
            }
        });
    }

    @Override
    public CompletableFuture<ChunkingResult> chunkFileAsync(Path file, ChunkingOptions options) {
        if (file == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("File cannot be null"));
        }

        // Create batch operation for chunking
        BatchOperation operation = createBatchOperation(file, options);

        // Submit to batch processor
        return batchOperationResultToChunkingResult(batchProcessor.processOperation(operation, new BatchOptions()),
                file);
    }

    @Override
    public CompletableFuture<ChunkingResult> chunkFile(Path file, ChunkingOptions options) {
        return chunkFileAsync(file, options);
    }

    @Override
    public void setAsyncBufferPool(AsyncByteBufferPool asyncBufferPool) {
        delegate.setAsyncBufferPool(asyncBufferPool);
    }

    @Override
    public AsyncByteBufferPool getAsyncBufferPool() {
        return delegate.getAsyncBufferPool();
    }

    @Override
    public void setAsyncChunkHandler(AsyncChunkHandler asyncChunkHandler) {
        delegate.setAsyncChunkHandler(asyncChunkHandler);
    }

    @Override
    public AsyncChunkHandler getAsyncChunkHandler() {
        return delegate.getAsyncChunkHandler();
    }

    @Override
    public CompletableFuture<String> getStatsAsync() {
        return delegate.getStatsAsync()
                .thenCombine(CompletableFuture.completedFuture("Batch processor stats available"),
                        (chunkerStats, batchStats) -> String.format(
                                "BatchAwareAsyncFileChunker Stats\n" +
                                        "Chunker: %s\n" +
                                        "Batch: %s\n" +
                                        "Active Batch Operations: %d",
                                chunkerStats, batchStats, activeBatchOperations.get()));
    }

    @Override
    public void setBufferPool(BufferPool bufferPool) {
        delegate.setBufferPool(bufferPool);
    }

    @Override
    public void setChunkSize(int chunkSize) {
        delegate.setChunkSize(chunkSize);
    }

    @Override
    public int getChunkSize() {
        return delegate.getChunkSize();
    }

    @Override
    public String storeChunk(byte[] data) throws java.io.IOException {
        return delegate.storeChunk(data);
    }

    @Override
    public byte[] retrieveChunk(String hash)
            throws java.io.IOException, com.justsyncit.storage.StorageIntegrityException {
        return delegate.retrieveChunk(hash);
    }

    @Override
    public boolean existsChunk(String hash) throws java.io.IOException {
        return delegate.existsChunk(hash);
    }

    @Override
    public boolean isClosed() {
        return delegate.isClosed();
    }

    @Override
    public int getActiveOperations() {
        return delegate.getActiveOperations() + activeBatchOperations.get();
    }

    @Override
    public int getMaxConcurrentOperations() {
        return Math.max(delegate.getMaxConcurrentOperations(),
                batchConfig.getMaxConcurrentBatches());
    }

    @Override
    public void setMaxConcurrentOperations(int maxConcurrentOperations) {
        delegate.setMaxConcurrentOperations(maxConcurrentOperations);
    }

    @Override
    public CompletableFuture<Void> closeAsync() {
        activeBatchOperations.set(0);
        return delegate.closeAsync();
    }

    /**
     * Creates a batch operation for chunking the given file.
     */
    private BatchOperation createBatchOperation(Path file, ChunkingOptions options) {
        List<Path> files = List.of(file);

        BatchOperation.ResourceRequirements requirements = new BatchOperation.ResourceRequirements(
                calculateMemoryRequirement(file, options),
                1, // 1 CPU core for chunking
                (int) (calculateIORequirement(file, options) / (1024 * 1024)), // Convert to MB/s
                batchConfig.getBatchTimeoutSeconds() * 1000 // timeout in ms
        );

        return new BatchOperation(
                "chunk-" + System.currentTimeMillis() + "-" + file.getFileName(),
                BatchOperationType.CHUNKING,
                files,
                determinePriority(file, options),
                requirements);
    }

    /**
     * Converts a batch operation result to a chunking result.
     */
    private ChunkingResult convertBatchOperationResultToChunkingResult(BatchOperationResult batchResult, Path file) {
        if (!batchResult.isSuccess()) {
            Exception error = batchResult.getError();
            return ChunkingResult.createFailed(file,
                    error != null ? error : new RuntimeException("Batch operation failed"));
        }

        // Extract chunking information from batch result
        // In a real implementation, this would parse the actual chunking results
        try {
            // For now, create a successful result with placeholder data
            return new ChunkingResult(
                    file,
                    1, // chunkCount
                    java.nio.file.Files.size(file),
                    0, // skippedBytes
                    "batch-hash-" + file.getFileName(), // fileHash
                    List.of("chunk-hash-" + file.getFileName()) // chunkHashes
            );
        } catch (Exception e) {
            return ChunkingResult.createFailed(file, e);
        }
    }

    /**
     * Converts a CompletableFuture<BatchOperationResult> to a
     * CompletableFuture<ChunkingResult>.
     */
    private CompletableFuture<ChunkingResult> batchOperationResultToChunkingResult(
            CompletableFuture<BatchOperationResult> batchFuture, Path file) {
        return batchFuture.thenApply(batchResult -> convertBatchOperationResultToChunkingResult(batchResult, file));
    }

    /**
     * Calculates memory requirement for chunking the file.
     */
    private long calculateMemoryRequirement(Path file, ChunkingOptions options) {
        try {
            long fileSize = java.nio.file.Files.size(file);
            int chunkSize = options != null && options.getChunkSize() > 0
                    ? options.getChunkSize()
                    : getChunkSize();

            // Memory needed for file reading + chunk processing + overhead
            return fileSize + (chunkSize * 2) + (1024 * 1024); // 1MB overhead
        } catch (Exception e) {
            logger.warn("Failed to calculate memory requirement for file: {}", file, e);
            return 1024 * 1024; // Default to 1MB
        }
    }

    /**
     * Calculates I/O requirement for chunking the file.
     */
    private long calculateIORequirement(Path file, ChunkingOptions options) {
        try {
            long fileSize = java.nio.file.Files.size(file);
            // I/O requirement in bytes per second
            // Assume we can process the file in 10 seconds
            return fileSize / 10;
        } catch (Exception e) {
            logger.warn("Failed to calculate I/O requirement for file: {}", file, e);
            return 1024 * 1024; // Default to 1MB/s
        }
    }

    /**
     * Determines the priority for chunking the file.
     */
    private BatchPriority determinePriority(Path file, ChunkingOptions options) {
        // For now, use default priority since ChunkingOptions doesn't have
        // getPriority()
        return BatchPriority.NORMAL;
    }

    /**
     * Gets batch processing statistics.
     */
    public CompletableFuture<String> getBatchStatsAsync() {
        return CompletableFuture.completedFuture("Batch processor stats available");
    }

    /**
     * Gets the delegate chunker.
     */
    public AsyncFileChunker getDelegate() {
        return delegate;
    }

    /**
     * Gets the batch processor.
     */
    public AsyncBatchProcessor getBatchProcessor() {
        return batchProcessor;
    }
}