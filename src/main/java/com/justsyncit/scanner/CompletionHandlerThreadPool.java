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

/**
 * Specialized thread pool for CompletionHandler callback processing.
 * Optimized for high-priority callback operations with low latency
 * and efficient task distribution.
 */
public class CompletionHandlerThreadPool extends ManagedThreadPool {

    private static final Logger logger = LoggerFactory.getLogger(CompletionHandlerThreadPool.class);

    private volatile double currentBackpressure = 0.0;
    private final Object backpressureLock = new Object();
    private final AtomicInteger priorityTasksProcessed = new AtomicInteger(0);
    private final AtomicInteger normalTasksProcessed = new AtomicInteger(0);

    /**
     * Creates a new CompletionHandlerThreadPool.
     */
    public CompletionHandlerThreadPool(ThreadPoolConfiguration.PoolConfig poolConfig,
            SystemResourceInfo systemInfo,
            ThreadPoolMonitor monitor) {
        super(poolConfig, systemInfo, monitor, ThreadPoolManager.PoolType.COMPLETION_HANDLER,
                new LinkedBlockingQueue<>(Math.max(1000, poolConfig.getQueueCapacity())),
                new CompletionHandlerThreadFactory(poolConfig.getThreadNamePrefix(),
                        poolConfig.getPriority().getValue()),
                new ThreadPoolExecutor.CallerRunsPolicy());

        logger.info("CompletionHandlerThreadPool initialized with config: {}", poolConfig);
    }

    /**
     * Executes a high-priority completion handler task.
     */
    public void executeHighPriority(Runnable task) {
        priorityTasksProcessed.incrementAndGet();
        execute(task);
    }

    /**
     * Executes a normal-priority completion handler task.
     */
    public void executeNormal(Runnable task) {
        normalTasksProcessed.incrementAndGet();
        execute(task);
    }

    /**
     * Applies backpressure to the completion handler thread pool.
     */
    public void applyBackpressure(double pressureLevel) {
        synchronized (backpressureLock) {
            this.currentBackpressure = Math.max(0.0, Math.min(1.0, pressureLevel));

            if (currentBackpressure > 0.8) {
                // High backpressure - temporarily reduce pool size
                int currentMax = executor.getMaximumPoolSize();
                int reducedMax = Math.max(poolConfig.getCorePoolSize(),
                        (int) (currentMax * (1.0 - currentBackpressure * 0.4)));
                executor.setMaximumPoolSize(reducedMax);

                logger.debug("Applied high backpressure to CompletionHandler pool: reduced max size from {} to {}",
                        currentMax, reducedMax);
            }
        }
    }

    /**
     * Releases backpressure from the completion handler thread pool.
     */
    public void releaseBackpressure() {
        synchronized (backpressureLock) {
            this.currentBackpressure = 0.0;

            // Restore original pool size
            executor.setMaximumPoolSize(poolConfig.getMaximumPoolSize());

            logger.debug("Released backpressure from CompletionHandler pool: restored max size to {}",
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
     * Triggers adaptive resizing based on callback processing load.
     */
    public void triggerAdaptiveResizing() {
        if (executor == null || executor.isShutdown()) {
            return;
        }

        int coreSize = executor.getCorePoolSize();
        int maxSize = executor.getMaximumPoolSize();
        int queueSize = executor.getQueue().size();

        // Calculate callback processing rate
        long totalProcessed = priorityTasksProcessed.get() + normalTasksProcessed.get();
        double processingRate = totalProcessed > 0 ? (double) queueSize / totalProcessed : 0.0;

        if (processingRate > 2.0 && maxSize < poolConfig.getMaximumPoolSize()) {
            // High callback rate - increase pool size
            int newMaxSize = Math.min(poolConfig.getMaximumPoolSize(),
                    (int) (maxSize * 1.3));
            executor.setMaximumPoolSize(newMaxSize);

            logger.debug("Adaptive resize: increased CompletionHandler pool max size to {} (processing rate: {:.2f})",
                    newMaxSize, processingRate);
        } else if (processingRate < 0.5 && maxSize > coreSize) {
            // Low callback rate - decrease pool size
            int newMaxSize = Math.max(coreSize, (int) (maxSize * 0.8));
            executor.setMaximumPoolSize(newMaxSize);

            logger.debug("Adaptive resize: decreased CompletionHandler pool max size to {} (processing rate: {:.2f})",
                    newMaxSize, processingRate);
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

        logger.info("Updated CompletionHandler pool configuration: {}", newConfig);
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
                0.85, // targetEfficiency - high for completion handlers
                executor.getQueue().size() * 100.0, // averageLatency (very low latency target)
                executor.getCompletedTaskCount() > 0
                        ? (double) executor.getCompletedTaskCount() / (System.currentTimeMillis() / 1000.0)
                        : 0.0 // throughput
        );
    }

    /**
     * Gets task processing statistics.
     */
    public TaskProcessingStats getTaskProcessingStats() {
        return new TaskProcessingStats(
                priorityTasksProcessed.get(),
                normalTasksProcessed.get(),
                priorityTasksProcessed.get() + normalTasksProcessed.get());
    }

    /**
     * Task processing statistics.
     */
    public static class TaskProcessingStats {
        public final int priorityTasksProcessed;
        public final int normalTasksProcessed;
        public final int totalTasksProcessed;

        TaskProcessingStats(int priorityTasksProcessed, int normalTasksProcessed, int totalTasksProcessed) {
            this.priorityTasksProcessed = priorityTasksProcessed;
            this.normalTasksProcessed = normalTasksProcessed;
            this.totalTasksProcessed = totalTasksProcessed;
        }

        @Override
        public String toString() {
            return String.format(
                    "TaskProcessingStats{priority=%d, normal=%d, total=%d}",
                    priorityTasksProcessed, normalTasksProcessed, totalTasksProcessed);
        }
    }

    /**
     * Specialized thread factory for completion handler operations.
     */
    private static class CompletionHandlerThreadFactory implements ThreadFactory {
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;
        private final int priority;

        CompletionHandlerThreadFactory(String namePrefix, int priority) {
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
                LoggerFactory.getLogger(CompletionHandlerThreadFactory.class)
                        .error("Uncaught exception in CompletionHandler thread {}", thread.getName(), ex);
            });

            return t;
        }
    }
}