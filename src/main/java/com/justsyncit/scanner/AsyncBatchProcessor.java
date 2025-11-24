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

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.List;

/**
 * Interface for asynchronous batch processing of file operations.
 * Provides high-performance batch processing with adaptive sizing, priority scheduling,
 * and resource-aware coordination for optimal throughput and resource utilization.
 */
public interface AsyncBatchProcessor {

    /**
     * Processes a batch of files using the specified batch options.
     *
     * @param files list of files to process in the batch
     * @param options batch processing options
     * @return a CompletableFuture that completes with the batch result
     * @throws IllegalArgumentException if files is null or empty, or options is null
     */
    CompletableFuture<BatchResult> processBatch(List<Path> files, BatchOptions options);

    /**
     * Processes a batch of files with priority scheduling.
     *
     * @param files list of files to process in the batch
     * @param options batch processing options
     * @param priority priority level for this batch
     * @return a CompletableFuture that completes with the batch result
     * @throws IllegalArgumentException if files is null or empty, or options is null
     */
    CompletableFuture<BatchResult> processBatch(List<Path> files, BatchOptions options, BatchPriority priority);

    /**
     * Processes multiple batches concurrently with resource coordination.
     *
     * @param batches list of file batches to process
     * @param options batch processing options
     * @return a CompletableFuture that completes with aggregated results
     * @throws IllegalArgumentException if batches is null or empty, or options is null
     */
    CompletableFuture<BatchAggregatedResult> processBatches(List<List<Path>> batches, BatchOptions options);

    /**
     * Processes a batch operation with custom operation type.
     *
     * @param operation the batch operation to execute
     * @param options batch processing options
     * @return a CompletableFuture that completes with the operation result
     * @throws IllegalArgumentException if operation is null or options is null
     */
    CompletableFuture<BatchOperationResult> processOperation(BatchOperation operation, BatchOptions options);

    /**
     * Sets the async file chunker for batch chunking operations.
     *
     * @param asyncFileChunker the async file chunker to use
     * @throws IllegalArgumentException if asyncFileChunker is null
     */
    void setAsyncFileChunker(AsyncFileChunker asyncFileChunker);

    /**
     * Gets the current async file chunker.
     *
     * @return the current async file chunker
     */
    AsyncFileChunker getAsyncFileChunker();

    /**
     * Sets the async buffer pool for memory management.
     *
     * @param asyncBufferPool the async buffer pool to use
     * @throws IllegalArgumentException if asyncBufferPool is null
     */
    void setAsyncBufferPool(AsyncByteBufferPool asyncBufferPool);

    /**
     * Gets the current async buffer pool.
     *
     * @return the current async buffer pool
     */
    AsyncByteBufferPool getAsyncBufferPool();

    /**
     * Sets the thread pool manager for resource coordination.
     *
     * @param threadPoolManager the thread pool manager to use
     * @throws IllegalArgumentException if threadPoolManager is null
     */
    void setThreadPoolManager(ThreadPoolManager threadPoolManager);

    /**
     * Gets the current thread pool manager.
     *
     * @return the current thread pool manager
     */
    ThreadPoolManager getThreadPoolManager();

    /**
     * Gets the current batch processing statistics.
     *
     * @return current batch processing statistics
     */
    BatchProcessingStats getBatchProcessingStats();

    /**
     * Applies backpressure to batch processing operations.
     *
     * @param pressureLevel backpressure level (0.0 to 1.0)
     * @throws IllegalArgumentException if pressureLevel is outside valid range
     */
    void applyBackpressure(double pressureLevel);

    /**
     * Releases backpressure from batch processing operations.
     */
    void releaseBackpressure();

    /**
     * Gets current backpressure level.
     *
     * @return current backpressure level (0.0 to 1.0)
     */
    double getCurrentBackpressure();

    /**
     * Checks if the processor supports adaptive batch sizing.
     *
     * @return true if adaptive sizing is supported, false otherwise
     */
    default boolean supportsAdaptiveSizing() {
        return true;
    }

    /**
     * Checks if the processor supports priority scheduling.
     *
     * @return true if priority scheduling is supported, false otherwise
     */
    default boolean supportsPriorityScheduling() {
        return true;
    }

    /**
     * Checks if the processor supports batch operation dependencies.
     *
     * @return true if dependencies are supported, false otherwise
     */
    default boolean supportsDependencies() {
        return true;
    }

    /**
     * Closes the batch processor and releases all resources asynchronously.
     *
     * @return a CompletableFuture that completes when all resources have been released
     */
    CompletableFuture<Void> closeAsync();

    /**
     * Checks if the batch processor has been closed.
     *
     * @return true if closed, false otherwise
     */
    boolean isClosed();

    /**
     * Gets the current number of active batch operations.
     *
     * @return the number of active operations
     */
    int getActiveBatchOperations();

    /**
     * Gets the maximum number of concurrent batch operations allowed.
     *
     * @return the maximum number of concurrent operations
     */
    int getMaxConcurrentBatchOperations();

    /**
     * Sets the maximum number of concurrent batch operations allowed.
     *
     * @param maxConcurrentOperations the maximum number of concurrent operations
     * @throws IllegalArgumentException if maxConcurrentOperations is not positive
     */
    void setMaxConcurrentBatchOperations(int maxConcurrentOperations);

    /**
     * Gets batch processing configuration.
     *
     * @return current batch processing configuration
     */
    BatchConfiguration getConfiguration();

    /**
     * Updates batch processing configuration.
     *
     * @param configuration new configuration to apply
     * @throws IllegalArgumentException if configuration is null
     */
    void updateConfiguration(BatchConfiguration configuration);
}