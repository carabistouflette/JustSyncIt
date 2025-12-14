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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * Integration layer for thread pools with async components.
 * Provides optimized async operations using the specialized thread pools.
 */

/**
 * Integration layer for thread pools with async components.
 * Provides optimized async operations using the specialized thread pools.
 */

public class AsyncThreadPoolIntegration {

    private static final Logger logger = LoggerFactory.getLogger(AsyncThreadPoolIntegration.class);

    private final ThreadPoolManager threadPoolManager;
    private final AsyncByteBufferPool bufferPool;

    /**
     * Creates a new AsyncThreadPoolIntegration.
     */
    public AsyncThreadPoolIntegration(ThreadPoolManager threadPoolManager,
            AsyncByteBufferPool bufferPool) {
        this.threadPoolManager = threadPoolManager;
        this.bufferPool = bufferPool;

        logger.info("AsyncThreadPoolIntegration initialized");
    }

    /**
     * Executes an I/O operation using the I/O thread pool.
     */
    public <T> CompletableFuture<T> executeIoOperation(Callable<T> operation) {
        logger.debug("Executing I/O operation on I/O thread pool");
        return threadPoolManager.submitTask(ThreadPoolManager.PoolType.IO, operation);
    }

    /**
     * Executes a CPU-intensive operation using the CPU thread pool.
     */
    public <T> CompletableFuture<T> executeCpuOperation(Callable<T> operation) {
        logger.debug("Executing CPU operation on CPU thread pool");
        return threadPoolManager.submitTask(ThreadPoolManager.PoolType.CPU, operation);
    }

    /**
     * Executes a completion handler callback using the CompletionHandler thread
     * pool.
     */
    public CompletableFuture<Void> executeCompletionHandler(Runnable callback) {
        logger.debug("Executing completion handler on CompletionHandler thread pool");
        return threadPoolManager.submitTask(
                ThreadPoolManager.PoolType.COMPLETION_HANDLER,
                () -> {
                    callback.run();
                    return null;
                });
    }

    /**
     * Executes a batch operation using the BatchProcessing thread pool.
     */
    public CompletableFuture<Void> executeBatchOperation(Runnable... operations) {
        logger.debug("Executing batch operation with {} tasks", operations.length);

        return threadPoolManager.submitTask(
                ThreadPoolManager.PoolType.BATCH_PROCESSING,
                () -> {
                    for (Runnable operation : operations) {
                        if (operation != null) {
                            operation.run();
                        }
                    }
                    return null;
                });
    }

    /**
     * Executes a watch service event using the WatchService thread pool.
     */
    public CompletableFuture<Void> executeWatchEvent(Runnable eventHandler) {
        logger.debug("Executing watch service event on WatchService thread pool");
        return threadPoolManager.submitTask(
                ThreadPoolManager.PoolType.WATCH_SERVICE,
                () -> {
                    eventHandler.run();
                    return null;
                });
    }

    /**
     * Executes a management task using the Management thread pool.
     */
    public CompletableFuture<Void> executeManagementTask(Runnable managementTask) {
        logger.debug("Executing management task on Management thread pool");
        return threadPoolManager.submitTask(
                ThreadPoolManager.PoolType.MANAGEMENT,
                () -> {
                    managementTask.run();
                    return null;
                });
    }

    /**
     * Executes an async file read operation with buffer pool integration.
     */
    public CompletableFuture<java.nio.ByteBuffer> executeAsyncFileRead(
            String filePath, long position, int size) {

        logger.debug("Executing async file read: {} at {} with size {}", filePath, position, size);

        return bufferPool.acquireAsync(size).thenApply(buffer -> {
            // Here you would integrate with actual async file I/O
            // For now, just return the buffer
            logger.debug("Acquired buffer for async file read");
            return buffer;
        });
    }

    /**
     * Executes an async file write operation with buffer pool integration.
     */
    public CompletableFuture<Void> executeAsyncFileWrite(
            String filePath, java.nio.ByteBuffer buffer) {

        logger.debug("Executing async file write: {} with buffer", filePath);

        return bufferPool.releaseAsync(buffer).thenApply(v -> {
            // Here you would integrate with actual async file I/O
            // For now, just complete the operation
            logger.debug("Released buffer for async file write");
            return null;
        });
    }

    /**
     * Executes a hashing operation using the CPU thread pool.
     */
    public CompletableFuture<byte[]> executeHashing(byte[] data) {
        logger.debug("Executing hashing operation on {} bytes of data", data.length);

        return executeCpuOperation(() -> {
            try {
                // Here you would integrate with actual hashing implementation
                // For now, just return a simple hash
                byte[] hash = new byte[32]; // BLAKE3 hash size
                logger.debug("Computed hash for {} bytes of data", data.length);
                return hash;
            } catch (Exception e) {
                logger.error("Failed to compute hash", e);
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Executes a completion callback with error handling.
     */
    public <T> CompletableFuture<T> executeCompletionCallback(
            T result, Throwable error, Consumer<T> onSuccess, Consumer<Throwable> onError) {

        logger.debug("Executing completion callback: result={}, error={}",
                result != null ? "success" : "null", error != null ? error.getMessage() : "null");

        return executeCompletionHandler(() -> {
            try {
                if (error != null) {
                    if (onError != null) {
                        onError.accept(error);
                    }
                } else {
                    if (onSuccess != null) {
                        onSuccess.accept(result);
                    }
                }
            } catch (Exception e) {
                logger.error("Error in completion callback", e);
            }
        }).thenApply(v -> result);
    }

    /**
     * Executes a coordinated batch operation with resource management.
     */
    @SafeVarargs
    public final <T> CompletableFuture<T> executeCoordinatedBatch(
            BatchOperation<T>... operations) {

        logger.debug("Executing coordinated batch with {} operations", operations.length);

        return threadPoolManager.submitTask(
                ThreadPoolManager.PoolType.BATCH_PROCESSING,
                () -> {
                    try {
                        T result = null;
                        for (BatchOperation<T> operation : operations) {
                            if (operation != null) {
                                T opResult = operation.execute();
                                if (result == null) {
                                    result = opResult;
                                }
                            }
                        }
                        return result;
                    } catch (Exception e) {
                        logger.error("Error in coordinated batch operation", e);
                        throw new RuntimeException(e);
                    }
                });
    }

    /**
     * Gets thread pool statistics for monitoring.
     */
    public ThreadPoolStats getThreadPoolStats() {
        return threadPoolManager.getStats();
    }

    /**
     * Gets statistics for a specific thread pool.
     */
    public ThreadPoolStats.PoolSpecificStats getPoolStats(ThreadPoolManager.PoolType poolType) {
        return threadPoolManager.getPoolStats(poolType);
    }

    /**
     * Applies backpressure to all thread pools.
     */
    public void applyBackpressure(double pressureLevel) {
        logger.info("Applying backpressure level: {:.2f}", pressureLevel);
        threadPoolManager.applyBackpressure(pressureLevel);
    }

    /**
     * Releases backpressure from all thread pools.
     */
    public void releaseBackpressure() {
        logger.info("Releasing backpressure from all thread pools");
        threadPoolManager.releaseBackpressure();
    }

    /**
     * Triggers adaptive resizing of all thread pools.
     */
    public void triggerAdaptiveResizing() {
        logger.info("Triggering adaptive resizing of all thread pools");
        threadPoolManager.triggerAdaptiveResizing();
    }

    /**
     * Interface for batch operations.
     */
    @FunctionalInterface
    public interface BatchOperation<T> {
        T execute() throws Exception;
    }

    /**
     * Gets performance metrics for the integration.
     */
    public IntegrationMetrics getMetrics() {
        ThreadPoolStats stats = getThreadPoolStats();

        return new IntegrationMetrics(
                stats.getThroughput(),
                stats.getUtilizationRate(),
                stats.getEfficiency(),
                getCurrentBackpressureLevel());
    }

    /**
     * Gets current backpressure level across all pools.
     */
    private double getCurrentBackpressureLevel() {
        // Calculate average backpressure across all pools
        double totalBackpressure = 0.0;
        int poolCount = 0;

        for (ThreadPoolManager.PoolType type : ThreadPoolManager.PoolType.values()) {
            ThreadPoolStats.PoolSpecificStats poolStats = getPoolStats(type);
            if (poolStats != null) {
                totalBackpressure += poolStats.getCurrentEfficiency();
                poolCount++;
            }
        }

        return poolCount > 0 ? totalBackpressure / poolCount : 0.0;
    }

    /**
     * Integration performance metrics.
     */
    public static class IntegrationMetrics {
        public final double throughput;
        public final double utilizationRate;
        public final double efficiency;
        public final double backpressureLevel;

        IntegrationMetrics(double throughput, double utilizationRate,
                double efficiency, double backpressureLevel) {
            this.throughput = throughput;
            this.utilizationRate = utilizationRate;
            this.efficiency = efficiency;
            this.backpressureLevel = backpressureLevel;
        }

        @Override
        public String toString() {
            return String.format(
                    "IntegrationMetrics{throughput=%.2f, utilization=%.2f%%, "
                            + "efficiency=%.2f%%, backpressure=%.2f}",
                    throughput, utilizationRate * 100, efficiency * 100, backpressureLevel);
        }
    }
}