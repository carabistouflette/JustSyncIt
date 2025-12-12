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

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Specialized thread pool for coordinated batch operations.
 * Optimized for processing batches of tasks with efficient resource utilization
 * and coordinated execution patterns.
 */
public class BatchProcessingThreadPool extends ManagedThreadPool {

    private static final Logger logger = LoggerFactory.getLogger(BatchProcessingThreadPool.class);

    private volatile double currentBackpressure = 0.0;
    private final Object backpressureLock = new Object();
    private final AtomicLong batchesProcessed = new AtomicLong(0);
    private final AtomicLong totalBatchSize = new AtomicLong(0);
    private final AtomicInteger activeBatches = new AtomicInteger(0);

    /**
     * Creates a new BatchProcessingThreadPool.
     */
    public BatchProcessingThreadPool(ThreadPoolConfiguration.PoolConfig poolConfig,
            SystemResourceInfo systemInfo,
            ThreadPoolMonitor monitor) {
        super(poolConfig, systemInfo, monitor, ThreadPoolManager.PoolType.BATCH_PROCESSING,
                new LinkedBlockingQueue<>(Math.max(200, poolConfig.getQueueCapacity())),
                new BatchProcessingThreadFactory(poolConfig.getThreadNamePrefix(), poolConfig.getPriority().getValue()),
                new ThreadPoolExecutor.CallerRunsPolicy());

        logger.info("BatchProcessingThreadPool initialized with config: {}", poolConfig);
    }

    /**
     * Executes a batch of related tasks.
     */
    public void executeBatch(Runnable... tasks) {
        if (tasks == null || tasks.length == 0) {
            return;
        }

        activeBatches.incrementAndGet();
        batchesProcessed.incrementAndGet();
        totalBatchSize.addAndGet(tasks.length);

        // Create a batch wrapper task
        Runnable batchTask = new BatchTask(tasks);
        execute(batchTask);
    }

    /**
     * Applies backpressure to the batch processing thread pool.
     */
    public void applyBackpressure(double pressureLevel) {
        synchronized (backpressureLock) {
            this.currentBackpressure = Math.max(0.0, Math.min(1.0, pressureLevel));

            if (currentBackpressure > 0.7) {
                // High backpressure - reduce pool size
                int currentMax = executor.getMaximumPoolSize();
                int reducedMax = Math.max(poolConfig.getCorePoolSize(),
                        (int) (currentMax * (1.0 - currentBackpressure * 0.3)));
                executor.setMaximumPoolSize(reducedMax);

                logger.debug("Applied backpressure to BatchProcessing pool: reduced max size from {} to {}",
                        currentMax, reducedMax);
            }
        }
    }

    /**
     * Releases backpressure from the batch processing thread pool.
     */
    public void releaseBackpressure() {
        synchronized (backpressureLock) {
            this.currentBackpressure = 0.0;

            // Restore original pool size
            executor.setMaximumPoolSize(poolConfig.getMaximumPoolSize());

            logger.debug("Released backpressure from BatchProcessing pool: restored max size to {}",
                    poolConfig.getMaximumPoolSize());
        }
    }

    /**
     * Gets current backpressure level.
     */
    public double getCurrentBackpressure() {
        synchronized (backpressureLock) {
            return currentBackpressure;
        }
    }

    /**
     * Triggers adaptive resizing based on batch processing load.
     */
    public void triggerAdaptiveResizing() {
        if (executor == null || executor.isShutdown()) {
            return;
        }

        int activeThreads = executor.getActiveCount();
        int coreSize = executor.getCorePoolSize();
        int maxSize = executor.getMaximumPoolSize();
        int queueSize = executor.getQueue().size();

        // Calculate batch processing efficiency
        double avgBatchSize = batchesProcessed.get() > 0
                ? (double) totalBatchSize.get() / batchesProcessed.get()
                : 0.0;

        double loadFactor = (double) (activeThreads + queueSize) / maxSize;

        if (loadFactor > 0.75 && avgBatchSize > 5.0 && maxSize < poolConfig.getMaximumPoolSize()) {
            // High load with large batches - increase pool size
            int newMaxSize = Math.min(poolConfig.getMaximumPoolSize(),
                    (int) (maxSize * 1.2));
            executor.setMaximumPoolSize(newMaxSize);

            logger.debug(
                    "Adaptive resize: increased BatchProcessing pool max size to {} (load: {:.2f}, avgBatch: {:.1f})",
                    newMaxSize, loadFactor, avgBatchSize);
        } else if (loadFactor < 0.3 && avgBatchSize < 3.0 && maxSize > coreSize) {
            // Low load with small batches - decrease pool size
            int newMaxSize = Math.max(coreSize, (int) (maxSize * 0.9));
            executor.setMaximumPoolSize(newMaxSize);

            logger.debug(
                    "Adaptive resize: decreased BatchProcessing pool max size to {} (load: {:.2f}, avgBatch: {:.1f})",
                    newMaxSize, loadFactor, avgBatchSize);
        }
    }

    /**
     * Updates pool configuration.
     */
    public void updateConfiguration(ThreadPoolConfiguration.PoolConfig newConfig) {
        if (executor == null || executor.isShutdown()) {
            return;
        }

        executor.setCorePoolSize(newConfig.getCorePoolSize());
        executor.setMaximumPoolSize(newConfig.getMaximumPoolSize());
        executor.setKeepAliveTime(newConfig.getKeepAliveTimeMs(),
                java.util.concurrent.TimeUnit.MILLISECONDS);
        executor.allowCoreThreadTimeOut(newConfig.isAllowCoreThreadTimeout());

        logger.info("Updated BatchProcessing pool configuration: {}", newConfig);
    }

    /**
     * Gets pool-specific statistics.
     */
    public ThreadPoolStats.PoolSpecificStats getPoolStats() {
        return new ThreadPoolStats.PoolSpecificStats(
                0, // resizeCount - not tracked in this implementation
                System.currentTimeMillis(), // lastResizeTime
                0, // consecutiveOptimizations
                getCurrentBackpressure(), // currentEfficiency
                0.75, // targetEfficiency - moderate for batch processing
                executor.getQueue().size() * 200.0, // averageLatency (moderate latency acceptable)
                executor.getCompletedTaskCount() > 0
                        ? (double) executor.getCompletedTaskCount() / (System.currentTimeMillis() / 1000.0)
                        : 0.0 // throughput
        );
    }

    /**
     * Gets batch processing statistics.
     */
    public BatchProcessingStats getBatchProcessingStats() {
        long totalBatches = batchesProcessed.get();
        long totalTasks = totalBatchSize.get();
        double avgBatchSize = totalBatches > 0 ? (double) totalTasks / totalBatches : 0.0;

        return new BatchProcessingStats(
                totalBatches,
                totalTasks,
                avgBatchSize,
                activeBatches.get());
    }

    /**
     * Batch processing statistics.
     */
    public static class BatchProcessingStats {
        public final long totalBatches;
        public final long totalTasks;
        public final double averageBatchSize;
        public final int activeBatches;

        BatchProcessingStats(long totalBatches, long totalTasks,
                double averageBatchSize, int activeBatches) {
            this.totalBatches = totalBatches;
            this.totalTasks = totalTasks;
            this.averageBatchSize = averageBatchSize;
            this.activeBatches = activeBatches;
        }

        @Override
        public String toString() {
            return String.format(
                    "BatchProcessingStats{batches=%d, tasks=%d, avgBatch=%.1f, active=%d}",
                    totalBatches, totalTasks, averageBatchSize, activeBatches);
        }
    }

    /**
     * Wrapper for batch tasks.
     */
    private class BatchTask implements Runnable {
        private final Runnable[] tasks;

        BatchTask(Runnable[] tasks) {
            this.tasks = tasks;
        }

        @Override
        public void run() {
            try {
                // Execute all tasks in the batch
                for (Runnable task : tasks) {
                    if (task != null) {
                        task.run();
                    }
                }
            } finally {
                activeBatches.decrementAndGet();
            }
        }
    }

    /**
     * Specialized thread factory for batch processing operations.
     */
    private static class BatchProcessingThreadFactory implements ThreadFactory {
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;
        private final int priority;

        BatchProcessingThreadFactory(String namePrefix, int priority) {
            this.namePrefix = namePrefix;
            this.priority = priority;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, namePrefix + "-" + threadNumber.getAndIncrement());
            t.setPriority(priority);
            t.setDaemon(false);

            // Set uncaught exception handler
            t.setUncaughtExceptionHandler((thread, ex) -> {
                LoggerFactory.getLogger(BatchProcessingThreadFactory.class)
                        .error("Uncaught exception in BatchProcessing thread {}", thread.getName(), ex);
            });

            return t;
        }
    }
}