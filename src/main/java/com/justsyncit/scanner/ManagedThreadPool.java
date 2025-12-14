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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.RejectedExecutionHandler;

/**
 * Base class for managed thread pools with monitoring and adaptive sizing.
 * Provides common functionality for all specialized thread pool
 * implementations.
 */

/**
 * Base class for managed thread pools with monitoring and adaptive sizing.
 * Provides common functionality for all specialized thread pool
 * implementations.
 */

public abstract class ManagedThreadPool {

    private static final Logger logger = LoggerFactory.getLogger(ManagedThreadPool.class);

    protected volatile ThreadPoolConfiguration.PoolConfig poolConfig;
    protected final SystemResourceInfo systemInfo;
    protected final ThreadPoolMonitor monitor;
    protected final ThreadPoolManager.PoolType poolType;

    protected ThreadPoolExecutor executor;
    protected final AtomicLong totalTasksSubmitted = new AtomicLong(0);
    protected final AtomicLong totalTasksCompleted = new AtomicLong(0);
    protected final AtomicLong totalExecutionTime = new AtomicLong(0);
    protected final AtomicInteger activeThreads = new AtomicInteger(0);
    protected final AtomicInteger currentQueueSize = new AtomicInteger(0);

    /**
     * Creates a new ManagedThreadPool.
     */
    protected ManagedThreadPool(ThreadPoolConfiguration.PoolConfig poolConfig,
            SystemResourceInfo systemInfo,
            ThreadPoolMonitor monitor,
            ThreadPoolManager.PoolType poolType,
            BlockingQueue<Runnable> workQueue,
            ThreadFactory threadFactory,
            RejectedExecutionHandler rejectionHandler) {
        this.poolConfig = poolConfig;
        this.systemInfo = systemInfo;
        this.monitor = monitor;
        this.poolType = poolType;

        this.executor = new ThreadPoolExecutor(
                poolConfig.getCorePoolSize(),
                poolConfig.getMaximumPoolSize(),
                poolConfig.getKeepAliveTimeMs(),
                TimeUnit.MILLISECONDS,
                workQueue,
                threadFactory,
                rejectionHandler);

        // Configure additional settings
        this.executor.allowCoreThreadTimeOut(poolConfig.isAllowCoreThreadTimeout());

        // Start with core threads
        this.executor.prestartAllCoreThreads();

        logger.info("Initialized {} thread pool: min={}, max={}, keepAlive={}ms",
                poolType.getName(), poolConfig.getCorePoolSize(), poolConfig.getMaximumPoolSize(),
                poolConfig.getKeepAliveTimeMs());
    }

    /**
     * Executes a task with monitoring.
     */
    public void execute(Runnable task) {
        if (executor == null || executor.isShutdown()) {
            throw new IllegalStateException("ThreadPool is shutdown");
        }

        totalTasksSubmitted.incrementAndGet();
        currentQueueSize.incrementAndGet();

        // Wrap task for monitoring
        Runnable monitoredTask = new MonitoredTask(task);

        try {
            executor.execute(monitoredTask);
        } catch (Exception e) {
            currentQueueSize.decrementAndGet();
            throw e;
        }
    }

    /**
     * Submits a task for execution.
     */
    public void submit(Runnable task) {
        execute(task);
    }

    /**
     * Gets the underlying executor service.
     */
    public ExecutorService getExecutor() {
        return Executors.unconfigurableExecutorService(executor);
    }

    /**
     * Gets the underlying executor service (alias for getExecutor).
     */
    public ExecutorService getExecutorService() {
        return Executors.unconfigurableExecutorService(executor);
    }

    /**
     * Gets pool-specific statistics.
     */
    public ThreadPoolStats.PoolSpecificStats getPoolStats() {
        return new ThreadPoolStats.PoolSpecificStats(
                0, // resizeCount - not tracked in base implementation
                System.currentTimeMillis(), // lastResizeTime
                0, // consecutiveOptimizations
                0.7, // currentEfficiency - default
                0.8, // targetEfficiency - default
                executor.getQueue().size() * 100.0, // averageLatency
                executor.getCompletedTaskCount() > 0
                        ? (double) executor.getCompletedTaskCount() / (System.currentTimeMillis() / 1000.0)
                        : 0.0 // throughput
        );
    }

    /**
     * Triggers adaptive resizing.
     */
    public void triggerAdaptiveResizing() {
        // Base implementation - can be overridden by subclasses
        if (executor == null || executor.isShutdown()) {
            return;
        }

        int activeThreads = executor.getActiveCount();
        int coreSize = executor.getCorePoolSize();
        int maxSize = executor.getMaximumPoolSize();
        int queueSize = executor.getQueue().size();

        // Simple adaptive logic
        double loadFactor = (double) (activeThreads + queueSize) / maxSize;

        if (loadFactor > 0.8 && maxSize < poolConfig.getMaximumPoolSize()) {
            int newMaxSize = Math.min(poolConfig.getMaximumPoolSize(),
                    (int) (maxSize * 1.2));
            executor.setMaximumPoolSize(newMaxSize);
        } else if (loadFactor < 0.3 && maxSize > coreSize) {
            int newMaxSize = Math.max(coreSize, (int) (maxSize * 0.9));
            executor.setMaximumPoolSize(newMaxSize);
        }
    }

    /**
     * Applies backpressure.
     */
    public void applyBackpressure(double pressureLevel) {
        // Base implementation - can be overridden by subclasses
        if (executor == null || executor.isShutdown()) {
            return;
        }

        double adjustedPressure = Math.max(0.0, Math.min(1.0, pressureLevel));

        if (adjustedPressure > 0.7) {
            int currentMax = executor.getMaximumPoolSize();
            int reducedMax = Math.max(poolConfig.getCorePoolSize(),
                    (int) (currentMax * (1.0 - adjustedPressure * 0.3)));
            executor.setMaximumPoolSize(reducedMax);
        }
    }

    /**
     * Releases backpressure.
     */
    public void releaseBackpressure() {
        // Base implementation - can be overridden by subclasses
        if (executor != null && !executor.isShutdown()) {
            executor.setMaximumPoolSize(poolConfig.getMaximumPoolSize());
        }
    }

    /**
     * Updates configuration.
     */
    public void updateConfiguration(ThreadPoolConfiguration.PoolConfig newConfig) {
        if (executor == null || executor.isShutdown()) {
            return;
        }

        this.poolConfig = newConfig;

        executor.setCorePoolSize(newConfig.getCorePoolSize());
        executor.setMaximumPoolSize(newConfig.getMaximumPoolSize());
        executor.setKeepAliveTime(newConfig.getKeepAliveTimeMs(),
                java.util.concurrent.TimeUnit.MILLISECONDS);
        executor.allowCoreThreadTimeOut(newConfig.isAllowCoreThreadTimeout());
    }

    /**
     * Shuts down asynchronously.
     */
    public java.util.concurrent.CompletableFuture<Void> shutdownAsync() {
        return java.util.concurrent.CompletableFuture.runAsync(() -> shutdown());
    }

    /**
     * Gets pool statistics.
     */
    public ThreadPoolStats getStats() {
        if (executor == null) {
            return new ThreadPoolStats.Builder().setPoolName(poolType.getName()).build();
        }

        int corePoolSize = executor.getCorePoolSize();
        int maximumPoolSize = executor.getMaximumPoolSize();
        int activeCount = executor.getActiveCount();
        long taskCount = executor.getTaskCount();
        long completedTaskCount = executor.getCompletedTaskCount();
        int queueSize = executor.getQueue().size();

        return new ThreadPoolStats.Builder()
                .setPoolName(poolType.getName())
                .setCorePoolSize(corePoolSize)
                .setMaximumPoolSize(maximumPoolSize)
                .setActiveThreads(activeCount)
                .setTotalTasks((int) taskCount)
                .setCompletedTasks((int) completedTaskCount)
                .setQueueSize(queueSize)
                .setSubmittedTasks(totalTasksSubmitted.get())
                .setCompletedSubmittedTasks(totalTasksCompleted.get())
                .setCurrentQueueSize(currentQueueSize.get())
                .build();
    }

    /**
     * Adjusts pool size.
     */
    public void adjustPoolSize(int coreSize, int maxSize) {
        if (executor != null && !executor.isShutdown()) {
            executor.setCorePoolSize(coreSize);
            executor.setMaximumPoolSize(maxSize);

            logger.debug("Adjusted {} pool size: core={}, max={}",
                    poolType.getName(), coreSize, maxSize);
        }
    }

    /**
     * Shuts down the thread pool.
     */
    public void shutdown() {
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                    if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                        logger.warn("ThreadPool {} did not terminate gracefully", poolType.getName());
                    }
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Monitored task wrapper.
     */
    private class MonitoredTask implements Runnable {
        private final Runnable delegate;
        private volatile long startTime;

        MonitoredTask(Runnable delegate) {
            this.delegate = delegate;
        }

        @Override
        public void run() {
            startTime = System.nanoTime();
            activeThreads.incrementAndGet();
            currentQueueSize.decrementAndGet();

            try {
                delegate.run();
            } finally {
                long executionTime = System.nanoTime() - startTime;
                totalExecutionTime.addAndGet(executionTime);
                totalTasksCompleted.incrementAndGet();
                activeThreads.decrementAndGet();

                // Record metrics
                if (monitor != null) {
                    monitor.recordTaskCompletion(poolType, executionTime);
                }
            }
        }
    }
}