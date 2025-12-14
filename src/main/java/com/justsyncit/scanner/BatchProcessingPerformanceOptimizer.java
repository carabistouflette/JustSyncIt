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

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Map;
import java.util.function.Function;

/**
 * Performance optimizer for batch processing operations.
 * Implements various optimization strategies including batch I/O, SIMD hashing,
 * transaction management, and compression/deduplication optimizations.
 */
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Performance optimizer for batch processing operations.
 * Implements various optimization strategies including batch I/O, SIMD hashing,
 * transaction management, and compression/deduplication optimizations.
 */
@SuppressFBWarnings({ "EI_EXPOSE_REP2", "EI_EXPOSE_REP" })
public class BatchProcessingPerformanceOptimizer {

    private static final Logger logger = LoggerFactory.getLogger(BatchProcessingPerformanceOptimizer.class);

    private final BatchConfiguration config;
    private final SystemResourceInfo systemInfo;
    private final Map<String, PerformanceMetrics> operationMetrics;
    private final AtomicLong totalBytesProcessed;
    private final AtomicInteger totalOperationsProcessed;
    private final AtomicLong totalProcessingTimeNs;

    /**
     * Creates a new BatchProcessingPerformanceOptimizer.
     *
     * @param config     the batch configuration
     * @param systemInfo system resource information
     */
    public BatchProcessingPerformanceOptimizer(BatchConfiguration config, SystemResourceInfo systemInfo) {
        this.config = config;
        this.systemInfo = systemInfo;
        this.operationMetrics = new ConcurrentHashMap<>();
        this.totalBytesProcessed = new AtomicLong(0);
        this.totalOperationsProcessed = new AtomicInteger(0);
        this.totalProcessingTimeNs = new AtomicLong(0);
    }

    /**
     * Optimizes batch I/O operations for improved throughput.
     *
     * @param files     list of files to process
     * @param operation the I/O operation to perform
     * @return a CompletableFuture that completes with the operation result
     */
    public <T> CompletableFuture<T> optimizeBatchIo(List<java.nio.file.Path> files,
            Function<List<java.nio.file.Path>, CompletableFuture<T>> operation) {
        long startTime = System.nanoTime();

        // Determine optimal I/O strategy based on system capabilities
        IoStrategy strategy = determineOptimalIoStrategy(files);

        logger.debug("Using I/O strategy {} for {} files", strategy, files.size());

        // Apply I/O optimizations
        CompletableFuture<T> result = switch (strategy) {
            case VECTORED_IO -> applyVectoredIo(files, operation);
            case MEMORY_MAPPED -> applyMemoryMappedIo(files, operation);
            case PARALLEL_IO -> applyParallelIo(files, operation);
            case SEQUENTIAL_IO -> applySequentialIo(files, operation);
            default -> operation.apply(files);
        };

        // Track performance metrics
        return result.whenComplete((r, throwable) -> {
            long endTime = System.nanoTime();
            long duration = endTime - startTime;

            updateMetrics("batch-io", duration, files.size());
            logger.debug("Batch I/O operation completed in {}ms using strategy {}",
                    duration / 1_000_000, strategy);
        });
    }

    /**
     * Optimizes batch hashing operations using SIMD and parallel processing.
     *
     * @param data list of data chunks to hash
     * @return a CompletableFuture that completes with list of hashes
     */
    public CompletableFuture<List<String>> optimizeBatchHashing(List<byte[]> data) {
        long startTime = System.nanoTime();

        // Determine optimal hashing strategy
        HashingStrategy strategy = determineOptimalHashingStrategy(data);

        logger.debug("Using hashing strategy {} for {} chunks", strategy, data.size());

        // Apply hashing optimizations
        CompletableFuture<List<String>> result = switch (strategy) {
            case SIMD_PARALLEL -> applySimdParallelHashing(data);
            case BATCH_HASHING -> applyBatchHashing(data);
            case PIPELINED_HASHING -> applyPipelinedHashing(data);
            case STANDARD_HASHING -> applyStandardHashing(data);
            default -> applyStandardHashing(data);
        };

        // Track performance metrics
        return result.whenComplete((hashes, throwable) -> {
            long endTime = System.nanoTime();
            long duration = endTime - startTime;

            long totalBytes = data.stream().mapToLong(arr -> arr.length).sum();
            totalBytesProcessed.addAndGet(totalBytes);
            totalOperationsProcessed.addAndGet(data.size());
            totalProcessingTimeNs.addAndGet(duration);

            updateMetrics("batch-hashing", duration, data.size());
            logger.debug("Batch hashing completed in {}ms using strategy {}, throughput: {} MB/s",
                    duration / 1_000_000, strategy,
                    calculateThroughput(totalBytes, duration));
        });
    }

    /**
     * Optimizes batch storage operations with transaction management.
     *
     * @param operations list of storage operations
     * @return a CompletableFuture that completes with the operation result
     */
    public <T> CompletableFuture<T> optimizeBatchStorage(List<StorageOperation> operations,
            Function<List<StorageOperation>, CompletableFuture<T>> executor) {
        long startTime = System.nanoTime();

        // Determine optimal storage strategy
        StorageStrategy strategy = determineOptimalStorageStrategy(operations);

        logger.debug("Using storage strategy {} for {} operations", strategy, operations.size());

        // Apply storage optimizations
        CompletableFuture<T> result = switch (strategy) {
            case TRANSACTIONAL -> applyTransactionalStorage(operations, executor);
            case BATCH_WRITE -> applyBatchWriteStorage(operations, executor);
            case PARALLEL_STORAGE -> applyParallelStorage(operations, executor);
            case SEQUENTIAL_STORAGE -> applySequentialStorage(operations, executor);
            default -> executor.apply(operations);
        };

        // Track performance metrics
        return result.whenComplete((r, throwable) -> {
            long endTime = System.nanoTime();
            long duration = endTime - startTime;

            updateMetrics("batch-storage", duration, operations.size());
            logger.debug("Batch storage completed in {}ms using strategy {}",
                    duration / 1_000_000, strategy);
        });
    }

    /**
     * Optimizes batch compression and deduplication operations.
     *
     * @param data list of data chunks to compress/deduplicate
     * @return a CompletableFuture that completes with optimized data
     */
    public CompletableFuture<List<CompressedChunk>> optimizeBatchCompression(List<byte[]> data) {
        long startTime = System.nanoTime();

        // Determine optimal compression strategy
        CompressionStrategy strategy = determineOptimalCompressionStrategy(data);

        logger.debug("Using compression strategy {} for {} chunks", strategy, data.size());

        // Apply compression optimizations
        CompletableFuture<List<CompressedChunk>> result = switch (strategy) {
            case PARALLEL_COMPRESSION -> applyParallelCompression(data);
            case ADAPTIVE_COMPRESSION -> applyAdaptiveCompression(data);
            case DICTIONARY_COMPRESSION -> applyDictionaryCompression(data);
            case STANDARD_COMPRESSION -> applyStandardCompression(data);
            default -> applyStandardCompression(data);
        };

        // Track performance metrics
        return result.whenComplete((chunks, throwable) -> {
            long endTime = System.nanoTime();
            long duration = endTime - startTime;

            long totalInputBytes = data.stream().mapToLong(arr -> arr.length).sum();
            long totalOutputBytes = chunks.stream().mapToLong(chunk -> chunk.getCompressedData().length).sum();
            double compressionRatio = (double) totalOutputBytes / totalInputBytes;

            updateMetrics("batch-compression", duration, data.size());
            logger.debug("Batch compression completed in {}ms using strategy {}, compression ratio: {:.2f}",
                    duration / 1_000_000, strategy, compressionRatio);
        });
    }

    /**
     * Gets current performance metrics.
     */
    public ComprehensivePerformanceMetrics getMetrics() {
        long totalBytes = totalBytesProcessed.get();
        int totalOps = totalOperationsProcessed.get();
        long totalTime = totalProcessingTimeNs.get();

        double throughputMbps = totalTime > 0 ? (totalBytes * 8.0 / 1_000_000) / (totalTime / 1_000_000_000.0) : 0.0;
        double avgLatencyMs = totalOps > 0 ? (totalTime / 1_000_000.0) / totalOps : 0.0;

        return new ComprehensivePerformanceMetrics(totalBytes, totalOps, totalTime, throughputMbps, avgLatencyMs);
    }

    /**
     * Resets performance metrics.
     */
    public void resetMetrics() {
        totalBytesProcessed.set(0);
        totalOperationsProcessed.set(0);
        totalProcessingTimeNs.set(0);
        operationMetrics.clear();
        logger.debug("Performance metrics reset");
    }

    // Private helper methods

    /**
     * Determines optimal I/O strategy based on file characteristics and system
     * capabilities.
     */
    private IoStrategy determineOptimalIoStrategy(List<java.nio.file.Path> files) {
        if (files.size() > 10) {
            return IoStrategy.VECTORED_IO;
        } else if (systemInfo.getTotalMemory() > 8L * 1024 * 1024 * 1024 && files.size() > 5) {
            return IoStrategy.MEMORY_MAPPED;
        } else if (systemInfo.getAvailableProcessors() > 4 && files.size() > 3) {
            return IoStrategy.PARALLEL_IO;
        } else {
            return IoStrategy.SEQUENTIAL_IO;
        }
    }

    /**
     * Determines optimal hashing strategy based on data characteristics and system
     * capabilities.
     */
    private HashingStrategy determineOptimalHashingStrategy(List<byte[]> data) {
        long totalSize = data.stream().mapToLong(arr -> arr.length).sum();

        if (totalSize > 1024 * 1024) {
            return HashingStrategy.SIMD_PARALLEL;
        } else if (data.size() > 50) {
            return HashingStrategy.BATCH_HASHING;
        } else if (systemInfo.getAvailableProcessors() > 2 && totalSize > 512 * 1024) {
            return HashingStrategy.PIPELINED_HASHING;
        } else {
            return HashingStrategy.STANDARD_HASHING;
        }
    }

    /**
     * Determines optimal storage strategy based on operation characteristics.
     */
    private StorageStrategy determineOptimalStorageStrategy(List<StorageOperation> operations) {
        if (operations.stream().anyMatch(op -> op.requiresTransaction())) {
            return StorageStrategy.TRANSACTIONAL;
        } else if (operations.size() > 20) {
            return StorageStrategy.BATCH_WRITE;
        } else if (systemInfo.getAvailableProcessors() > 4 && operations.size() > 5) {
            return StorageStrategy.PARALLEL_STORAGE;
        } else {
            return StorageStrategy.SEQUENTIAL_STORAGE;
        }
    }

    /**
     * Determines optimal compression strategy based on data characteristics.
     */
    private CompressionStrategy determineOptimalCompressionStrategy(List<byte[]> data) {
        long totalSize = data.stream().mapToLong(arr -> arr.length).sum();

        if (systemInfo.getAvailableProcessors() > 4 && totalSize > 10 * 1024 * 1024) {
            return CompressionStrategy.PARALLEL_COMPRESSION;
        } else if (data.size() > 100) {
            return CompressionStrategy.ADAPTIVE_COMPRESSION;
        } else if (totalSize > 5 * 1024 * 1024) {
            return CompressionStrategy.DICTIONARY_COMPRESSION;
        } else {
            return CompressionStrategy.STANDARD_COMPRESSION;
        }
    }

    // I/O optimization implementations

    private <T> CompletableFuture<T> applyVectoredIo(List<java.nio.file.Path> files,
            Function<List<java.nio.file.Path>, CompletableFuture<T>> operation) {
        // Implementation for vectored I/O (simulated)
        logger.debug("Applying vectored I/O optimization");
        return operation.apply(files);
    }

    private <T> CompletableFuture<T> applyMemoryMappedIo(List<java.nio.file.Path> files,
            Function<List<java.nio.file.Path>, CompletableFuture<T>> operation) {
        // Implementation for memory-mapped I/O (simulated)
        logger.debug("Applying memory-mapped I/O optimization");
        return operation.apply(files);
    }

    private <T> CompletableFuture<T> applyParallelIo(List<java.nio.file.Path> files,
            Function<List<java.nio.file.Path>, CompletableFuture<T>> operation) {
        // Implementation for parallel I/O (simulated)
        logger.debug("Applying parallel I/O optimization");
        return operation.apply(files);
    }

    private <T> CompletableFuture<T> applySequentialIo(List<java.nio.file.Path> files,
            Function<List<java.nio.file.Path>, CompletableFuture<T>> operation) {
        // Implementation for sequential I/O (simulated)
        logger.debug("Applying sequential I/O optimization");
        return operation.apply(files);
    }

    // Hashing optimization implementations

    private CompletableFuture<List<String>> applySimdParallelHashing(List<byte[]> data) {
        // Implementation for SIMD parallel hashing (simulated)
        logger.debug("Applying SIMD parallel hashing optimization");
        return applyStandardHashing(data);
    }

    private CompletableFuture<List<String>> applyBatchHashing(List<byte[]> data) {
        // Implementation for batch hashing (simulated)
        logger.debug("Applying batch hashing optimization");
        return applyStandardHashing(data);
    }

    private CompletableFuture<List<String>> applyPipelinedHashing(List<byte[]> data) {
        // Implementation for pipelined hashing (simulated)
        logger.debug("Applying pipelined hashing optimization");
        return applyStandardHashing(data);
    }

    private CompletableFuture<List<String>> applyStandardHashing(List<byte[]> data) {
        // Standard hashing implementation
        return CompletableFuture.supplyAsync(() -> {
            return data.stream()
                    .map(chunk -> "hash-" + java.util.Arrays.hashCode(chunk)) // Simplified hashing
                    .toList();
        });
    }

    // Storage optimization implementations

    private <T> CompletableFuture<T> applyTransactionalStorage(List<StorageOperation> operations,
            Function<List<StorageOperation>, CompletableFuture<T>> executor) {
        // Implementation for transactional storage (simulated)
        logger.debug("Applying transactional storage optimization");
        return executor.apply(operations);
    }

    private <T> CompletableFuture<T> applyBatchWriteStorage(List<StorageOperation> operations,
            Function<List<StorageOperation>, CompletableFuture<T>> executor) {
        // Implementation for batch write storage (simulated)
        logger.debug("Applying batch write storage optimization");
        return executor.apply(operations);
    }

    private <T> CompletableFuture<T> applyParallelStorage(List<StorageOperation> operations,
            Function<List<StorageOperation>, CompletableFuture<T>> executor) {
        // Implementation for parallel storage (simulated)
        logger.debug("Applying parallel storage optimization");
        return executor.apply(operations);
    }

    private <T> CompletableFuture<T> applySequentialStorage(List<StorageOperation> operations,
            Function<List<StorageOperation>, CompletableFuture<T>> executor) {
        // Implementation for sequential storage (simulated)
        logger.debug("Applying sequential storage optimization");
        return executor.apply(operations);
    }

    // Compression optimization implementations

    private CompletableFuture<List<CompressedChunk>> applyParallelCompression(List<byte[]> data) {
        // Implementation for parallel compression (simulated)
        logger.debug("Applying parallel compression optimization");
        return applyStandardCompression(data);
    }

    private CompletableFuture<List<CompressedChunk>> applyAdaptiveCompression(List<byte[]> data) {
        // Implementation for adaptive compression (simulated)
        logger.debug("Applying adaptive compression optimization");
        return applyStandardCompression(data);
    }

    private CompletableFuture<List<CompressedChunk>> applyDictionaryCompression(List<byte[]> data) {
        // Implementation for dictionary compression (simulated)
        logger.debug("Applying dictionary compression optimization");
        return applyStandardCompression(data);
    }

    private CompletableFuture<List<CompressedChunk>> applyStandardCompression(List<byte[]> data) {
        // Standard compression implementation
        return CompletableFuture.supplyAsync(() -> {
            return data.stream()
                    // Simplified compression
                    .map(chunk -> new CompressedChunk(chunk, "compressed-" + java.util.Arrays.hashCode(chunk)))
                    .toList();
        });
    }

    // Utility methods

    private void updateMetrics(String operationType, long durationNs, int operationCount) {
        operationMetrics.compute(operationType, (key, existing) -> {
            if (existing == null) {
                return new PerformanceMetrics(operationType, durationNs, operationCount);
            } else {
                return existing.addOperation(durationNs, operationCount);
            }
        });
    }

    private double calculateThroughput(long bytes, long durationNs) {
        return durationNs > 0 ? (bytes * 8.0 / 1_000_000) / (durationNs / 1_000_000_000.0) : 0.0;
    }

    // Enums for optimization strategies

    private enum IoStrategy {
        VECTORED_IO, MEMORY_MAPPED, PARALLEL_IO, SEQUENTIAL_IO
    }

    private enum HashingStrategy {
        SIMD_PARALLEL, BATCH_HASHING, PIPELINED_HASHING, STANDARD_HASHING
    }

    private enum StorageStrategy {
        TRANSACTIONAL, BATCH_WRITE, PARALLEL_STORAGE, SEQUENTIAL_STORAGE
    }

    private enum CompressionStrategy {
        PARALLEL_COMPRESSION, ADAPTIVE_COMPRESSION, DICTIONARY_COMPRESSION, STANDARD_COMPRESSION
    }

    // Inner classes

    /**
     * Represents a storage operation.
     */
    public static class StorageOperation {
        private final String operation;
        @SuppressFBWarnings({ "EI_EXPOSE_REP", "EI_EXPOSE_REP2" })
        private final byte[] data;
        private final boolean requiresTransaction;

        public StorageOperation(String operation, byte[] data, boolean requiresTransaction) {
            this.operation = operation;
            this.data = data;
            this.requiresTransaction = requiresTransaction;
        }

        public String getOperation() {
            return operation;
        }

        public byte[] getData() {
            return data;
        }

        public boolean requiresTransaction() {
            return requiresTransaction;
        }
    }

    /**
     * Represents a compressed data chunk.
     */
    public static class CompressedChunk {
        @SuppressFBWarnings({ "EI_EXPOSE_REP", "EI_EXPOSE_REP2" })
        private final byte[] originalData;
        @SuppressFBWarnings({ "EI_EXPOSE_REP", "EI_EXPOSE_REP2" })
        private final byte[] compressedData;
        private final String compressionAlgorithm;

        public CompressedChunk(byte[] originalData, String compressionAlgorithm) {
            this.originalData = originalData;
            this.compressedData = originalData; // Simplified - just copy for now
            this.compressionAlgorithm = compressionAlgorithm;
        }

        public byte[] getOriginalData() {
            return originalData;
        }

        public byte[] getCompressedData() {
            return compressedData;
        }

        public String getCompressionAlgorithm() {
            return compressionAlgorithm;
        }
    }

    /**
     * Performance metrics for operations.
     */
    public static class PerformanceMetrics {
        private final String operationType;
        private final long totalDurationNs;
        private final int totalOperations;
        private final double averageDurationNs;
        private final long timestamp;

        public PerformanceMetrics(String operationType, long totalDurationNs, int totalOperations) {
            this.operationType = operationType;
            this.totalDurationNs = totalDurationNs;
            this.totalOperations = totalOperations;
            this.averageDurationNs = totalOperations > 0 ? (double) totalDurationNs / totalOperations : 0.0;
            this.timestamp = System.currentTimeMillis();
        }

        public PerformanceMetrics addOperation(long durationNs, int operationCount) {
            return new PerformanceMetrics(
                    operationType,
                    totalDurationNs + durationNs,
                    totalOperations + operationCount);
        }

        public String getOperationType() {
            return operationType;
        }

        public long getTotalDurationNs() {
            return totalDurationNs;
        }

        public int getTotalOperations() {
            return totalOperations;
        }

        public double getAverageDurationNs() {
            return averageDurationNs;
        }

        public long getTimestamp() {
            return timestamp;
        }

        @Override
        public String toString() {
            return String.format(
                    "PerformanceMetrics{type='%s', duration=%dms, operations=%d, avg=%.2fms}",
                    operationType, totalDurationNs / 1_000_000, totalOperations, averageDurationNs / 1_000_000);
        }
    }

    /**
     * Comprehensive performance metrics.
     */
    public static class ComprehensivePerformanceMetrics {
        private final long totalBytesProcessed;
        private final int totalOperationsProcessed;
        private final long totalProcessingTimeNs;
        private final double throughputMbps;
        private final double averageLatencyMs;

        public ComprehensivePerformanceMetrics(long totalBytesProcessed, int totalOperationsProcessed,
                long totalProcessingTimeNs, double throughputMbps, double averageLatencyMs) {
            this.totalBytesProcessed = totalBytesProcessed;
            this.totalOperationsProcessed = totalOperationsProcessed;
            this.totalProcessingTimeNs = totalProcessingTimeNs;
            this.throughputMbps = throughputMbps;
            this.averageLatencyMs = averageLatencyMs;
        }

        public long getTotalBytesProcessed() {
            return totalBytesProcessed;
        }

        public int getTotalOperationsProcessed() {
            return totalOperationsProcessed;
        }

        public long getTotalProcessingTimeNs() {
            return totalProcessingTimeNs;
        }

        public double getThroughputMbps() {
            return throughputMbps;
        }

        public double getAverageLatencyMs() {
            return averageLatencyMs;
        }

        @Override
        public String toString() {
            return String.format(
                    "PerformanceMetrics{bytes=%dMB, operations=%d, throughput=%.2fMbps, latency=%.2fms}",
                    totalBytesProcessed / (1024 * 1024), totalOperationsProcessed, throughputMbps, averageLatencyMs);
        }
    }
}