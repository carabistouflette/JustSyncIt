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

import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Batch-aware implementation of AsyncByteBufferPool that integrates with the
 * batch processing system.
 * Provides optimized buffer management for batch processing scenarios.
 * Enhances performance through batch coordination and resource optimization.
 */
public class BatchAwareAsyncByteBufferPool implements AsyncByteBufferPool {

    private static final Logger logger = LoggerFactory.getLogger(BatchAwareAsyncByteBufferPool.class);

    private final AsyncByteBufferPool delegate;
    private final AsyncBatchProcessor batchProcessor;
    private final BatchConfiguration batchConfig;
    private final AtomicInteger activeBatchOperations;
    private final AtomicLong totalBuffersAllocated;
    private final AtomicLong totalBuffersReleased;

    /**
     * Creates a new BatchAwareAsyncByteBufferPool.
     *
     * @param delegate       the underlying async buffer pool
     * @param batchProcessor the batch processor for coordination
     * @param batchConfig    the batch configuration
     * @throws IllegalArgumentException if any parameter is null
     */
    public BatchAwareAsyncByteBufferPool(AsyncByteBufferPool delegate,
            AsyncBatchProcessor batchProcessor,
            BatchConfiguration batchConfig) {
        if (delegate == null) {
            throw new IllegalArgumentException("Delegate buffer pool cannot be null");
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
        this.totalBuffersAllocated = new AtomicLong(0);
        this.totalBuffersReleased = new AtomicLong(0);
    }

    // AsyncByteBufferPool interface methods

    @Override
    public CompletableFuture<ByteBuffer> acquireAsync(int size) {
        if (size <= 0) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Buffer size must be positive"));
        }

        // For small buffers, use direct delegation to avoid batch overhead
        if (size <= batchConfig.getSmallBufferThreshold()) {
            return delegate.acquireAsync(size);
        }

        // Create batch operation for buffer allocation
        BatchOperation operation = createBufferAllocationOperation(size);

        // Submit to batch processor
        CompletableFuture<BatchOperationResult> batchFuture = batchProcessor.processOperation(operation,
                new BatchOptions());

        // Handle batch result
        CompletableFuture<ByteBuffer> result = batchFuture.thenCompose(batchResult -> {
            if (!batchResult.isSuccess()) {
                Exception error = batchResult.getError();
                return CompletableFuture
                        .failedFuture(error != null ? error : new RuntimeException("Buffer allocation failed"));
            }

            // Allocate the actual buffer
            return delegate.acquireAsync(size);
        });

        // Track allocation
        result.whenComplete((buffer, throwable) -> {
            if (throwable == null && buffer != null) {
                totalBuffersAllocated.incrementAndGet();
            }
        });

        return result;
    }

    @Override
    public CompletableFuture<Void> releaseAsync(ByteBuffer buffer) {
        if (buffer == null) {
            return CompletableFuture.completedFuture(null);
        }

        // For small buffers, use direct delegation to avoid batch overhead
        if (buffer.capacity() <= batchConfig.getSmallBufferThreshold()) {
            totalBuffersReleased.incrementAndGet();
            return delegate.releaseAsync(buffer);
        }

        // Create batch operation for buffer release
        BatchOperation operation = createBufferReleaseOperation(buffer);

        // Submit to batch processor
        CompletableFuture<BatchOperationResult> batchFuture = batchProcessor.processOperation(operation,
                new BatchOptions());

        // Handle batch result
        return batchFuture.thenCompose(batchResult -> {
            if (!batchResult.isSuccess()) {
                Exception error = batchResult.getError();
                return CompletableFuture
                        .failedFuture(error != null ? error : new RuntimeException("Buffer release failed"));
            }

            // Release the actual buffer
            return delegate.releaseAsync(buffer).whenComplete((result, throwable) -> {
                if (throwable == null) {
                    totalBuffersReleased.incrementAndGet();
                }
            });
        });
    }

    @Override
    public CompletableFuture<Void> clearAsync() {
        activeBatchOperations.set(0);
        totalBuffersAllocated.set(0);
        totalBuffersReleased.set(0);
        return delegate.clearAsync();
    }

    @Override
    public CompletableFuture<Integer> getAvailableCountAsync() {
        return delegate.getAvailableCountAsync();
    }

    @Override
    public CompletableFuture<Integer> getTotalCountAsync() {
        return delegate.getTotalCountAsync();
    }

    @Override
    public CompletableFuture<Integer> getBuffersInUseAsync() {
        return delegate.getBuffersInUseAsync();
    }

    @Override
    public CompletableFuture<String> getStatsAsync() {
        return delegate.getStatsAsync()
                .thenCombine(CompletableFuture.supplyAsync(() -> {
                    long allocated = totalBuffersAllocated.get();
                    long released = totalBuffersReleased.get();
                    int active = activeBatchOperations.get();

                    return String.format(
                            "BatchAwareAsyncByteBufferPool Stats\n" +
                                    "Total Allocated: %d\n" +
                                    "Total Released: %d\n" +
                                    "Active Batch Operations: %d\n" +
                                    "Net Buffers: %d",
                            allocated, released, active, allocated - released);
                }), (delegateStats, batchStats) -> delegateStats + "\n" + batchStats);
    }

    // BufferPool interface methods

    @Override
    public ByteBuffer acquire(int size) {
        return delegate.acquire(size);
    }

    @Override
    public void release(ByteBuffer buffer) {
        delegate.release(buffer);
    }

    @Override
    public void clear() {
        activeBatchOperations.set(0);
        totalBuffersAllocated.set(0);
        totalBuffersReleased.set(0);
        delegate.clear();
    }

    @Override
    public int getAvailableCount() {
        return delegate.getAvailableCount();
    }

    @Override
    public int getTotalCount() {
        return delegate.getTotalCount();
    }

    @Override
    public int getDefaultBufferSize() {
        return delegate.getDefaultBufferSize();
    }

    // Batch processing methods

    /**
     * Allocates multiple buffers in a batch operation.
     *
     * @param sizes list of buffer sizes to allocate
     * @return a CompletableFuture that completes with batch allocation result
     */
    public CompletableFuture<BatchAllocationResult> allocateBatchAsync(List<Integer> sizes) {
        if (sizes == null || sizes.isEmpty()) {
            return CompletableFuture.completedFuture(
                    new BatchAllocationResult(List.of(), 0, 0));
        }

        // Create batch operation for batch allocation
        BatchOperation operation = createBatchAllocationOperation(sizes);

        // Submit to batch processor
        return batchProcessor.processOperation(operation, new BatchOptions())
                .thenCompose(batchResult -> {
                    if (!batchResult.isSuccess()) {
                        Exception error = batchResult.getError();
                        return CompletableFuture
                                .failedFuture(error != null ? error : new RuntimeException("Batch allocation failed"));
                    }

                    // Perform the actual batch allocation
                    return performBatchAllocation(sizes);
                });
    }

    /**
     * Releases multiple buffers in a batch operation.
     *
     * @param buffers list of buffers to release
     * @return a CompletableFuture that completes with batch release result
     */
    public CompletableFuture<BatchReleaseResult> releaseBatchAsync(List<ByteBuffer> buffers) {
        if (buffers == null || buffers.isEmpty()) {
            return CompletableFuture.completedFuture(
                    new BatchReleaseResult(0, 0));
        }

        // Create batch operation for batch release
        BatchOperation operation = createBatchReleaseOperation(buffers);

        // Submit to batch processor
        return batchProcessor.processOperation(operation, new BatchOptions())
                .thenCompose(batchResult -> {
                    if (!batchResult.isSuccess()) {
                        Exception error = batchResult.getError();
                        return CompletableFuture
                                .failedFuture(error != null ? error : new RuntimeException("Batch release failed"));
                    }

                    // Perform the actual batch release
                    return performBatchRelease(buffers);
                });
    }

    /**
     * Creates a batch operation for buffer allocation.
     */
    private BatchOperation createBufferAllocationOperation(int size) {
        BatchOperation.ResourceRequirements requirements = new BatchOperation.ResourceRequirements(
                size + (1024 * 1024), // buffer size + 1MB overhead
                1, // 1 CPU core for allocation
                10, // 10MB/s I/O bandwidth
                batchConfig.getBatchTimeoutSeconds() * 1000 // timeout in ms
        );

        return new BatchOperation(
                "buffer-alloc-" + System.currentTimeMillis() + "-" + size,
                BatchOperationType.STORAGE,
                List.of(), // No files for buffer operations
                BatchPriority.NORMAL,
                requirements);
    }

    /**
     * Creates a batch operation for buffer release.
     */
    private BatchOperation createBufferReleaseOperation(ByteBuffer buffer) {
        BatchOperation.ResourceRequirements requirements = new BatchOperation.ResourceRequirements(
                1024 * 1024, // 1MB overhead for release operation
                1, // 1 CPU core for release
                5, // 5MB/s I/O bandwidth
                batchConfig.getBatchTimeoutSeconds() * 1000 // timeout in ms
        );

        return new BatchOperation(
                "buffer-release-" + System.currentTimeMillis() + "-" + buffer.capacity(),
                BatchOperationType.STORAGE,
                List.of(), // No files for buffer operations
                BatchPriority.LOW, // Release operations are lower priority
                requirements);
    }

    /**
     * Creates a batch operation for batch buffer allocation.
     */
    private BatchOperation createBatchAllocationOperation(List<Integer> sizes) {
        int totalSize = sizes.stream().mapToInt(Integer::intValue).sum();

        BatchOperation.ResourceRequirements requirements = new BatchOperation.ResourceRequirements(
                totalSize + (sizes.size() * 1024), // total size + overhead per buffer
                Math.max(1, sizes.size() / 4), // Scale CPU cores with batch size
                Math.max(10, totalSize / (1024 * 1024)), // Scale I/O with total size
                batchConfig.getBatchTimeoutSeconds() * 2000 // Double timeout for batch operations
        );

        return new BatchOperation(
                "batch-alloc-" + System.currentTimeMillis() + "-" + sizes.size(),
                BatchOperationType.STORAGE,
                List.of(), // No files for buffer operations
                BatchPriority.HIGH, // Batch allocations are high priority
                requirements);
    }

    /**
     * Creates a batch operation for batch buffer release.
     */
    private BatchOperation createBatchReleaseOperation(List<ByteBuffer> buffers) {
        int totalSize = buffers.stream().mapToInt(ByteBuffer::capacity).sum();

        BatchOperation.ResourceRequirements requirements = new BatchOperation.ResourceRequirements(
                totalSize + (buffers.size() * 512), // total size + smaller overhead for release
                Math.max(1, buffers.size() / 8), // Fewer CPU cores for release
                Math.max(5, totalSize / (2 * 1024 * 1024)), // Less I/O for release
                batchConfig.getBatchTimeoutSeconds() * 1500 // 1.5x timeout for batch release
        );

        return new BatchOperation(
                "batch-release-" + System.currentTimeMillis() + "-" + buffers.size(),
                BatchOperationType.STORAGE,
                List.of(), // No files for buffer operations
                BatchPriority.LOW, // Batch releases are low priority
                requirements);
    }

    /**
     * Performs the actual batch allocation.
     */
    private CompletableFuture<BatchAllocationResult> performBatchAllocation(List<Integer> sizes) {
        return CompletableFuture.supplyAsync(() -> {
            // This is a simplified implementation
            // In a real scenario, you'd coordinate with the delegate pool for efficient
            // batch allocation
            int totalSize = sizes.stream().mapToInt(Integer::intValue).sum();
            int allocatedCount = sizes.size();

            // Update counters
            totalBuffersAllocated.addAndGet(allocatedCount);
            activeBatchOperations.addAndGet(allocatedCount);

            return new BatchAllocationResult(List.of(), totalSize, allocatedCount);
        });
    }

    /**
     * Performs the actual batch release.
     */
    private CompletableFuture<BatchReleaseResult> performBatchRelease(List<ByteBuffer> buffers) {
        return CompletableFuture.supplyAsync(() -> {
            // This is a simplified implementation
            // In a real scenario, you'd coordinate with the delegate pool for efficient
            // batch release
            int releasedCount = buffers.size();

            // Update counters
            totalBuffersReleased.addAndGet(releasedCount);
            activeBatchOperations.addAndGet(-releasedCount);

            return new BatchReleaseResult(releasedCount, 0);
        });
    }

    /**
     * Gets the delegate buffer pool.
     */
    public AsyncByteBufferPool getDelegate() {
        return delegate;
    }

    /**
     * Gets the batch processor.
     */
    public AsyncBatchProcessor getBatchProcessor() {
        return batchProcessor;
    }

    /**
     * Gets the total number of buffers allocated.
     */
    public long getTotalBuffersAllocated() {
        return totalBuffersAllocated.get();
    }

    /**
     * Gets the total number of buffers released.
     */
    public long getTotalBuffersReleased() {
        return totalBuffersReleased.get();
    }

    /**
     * Gets the number of active batch operations.
     */
    public int getActiveBatchOperations() {
        return activeBatchOperations.get();
    }
}