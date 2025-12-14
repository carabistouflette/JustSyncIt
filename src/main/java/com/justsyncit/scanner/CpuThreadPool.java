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
 * Specialized thread pool for CPU-bound operations.
 * Optimized for hashing and computation-intensive operations with
 * thread affinity and NUMA awareness for multi-socket systems.
 */
public class CpuThreadPool extends ManagedThreadPool {

    private static final Logger logger = LoggerFactory.getLogger(CpuThreadPool.class);

    private volatile double currentBackpressure = 0.0;
    private final Object backpressureLock = new Object();
    private volatile boolean numaEnabled = false;

    /**
     * Creates a new CpuThreadPool.
     */
    public CpuThreadPool(ThreadPoolConfiguration.PoolConfig poolConfig,
            SystemResourceInfo systemInfo,
            ThreadPoolMonitor monitor) {
        super(poolConfig, systemInfo, monitor, ThreadPoolManager.PoolType.CPU,
                new LinkedBlockingQueue<>(Math.max(100, poolConfig.getQueueCapacity())),
                new CpuThreadFactory(poolConfig.getThreadNamePrefix(),
                        poolConfig.getPriority().getValue(),
                        poolConfig.getAffinityCores()),
                new ThreadPoolExecutor.AbortPolicy());

        this.numaEnabled = systemInfo.isNumaAware();

        logger.info("CpuThreadPool initialized with config: {}, NUMA enabled: {}",
                poolConfig, numaEnabled);
    }

    /**
     * Applies backpressure to the CPU thread pool.
     */
    public void applyBackpressure(double pressureLevel) {
        synchronized (backpressureLock) {
            this.currentBackpressure = Math.max(0.0, Math.min(1.0, pressureLevel));

            if (currentBackpressure > 0.6) {
                // High backpressure - temporarily reduce pool size
                int currentMax = executor.getMaximumPoolSize();
                int reducedMax = Math.max(poolConfig.getCorePoolSize(),
                        (int) (currentMax * (1.0 - currentBackpressure * 0.3)));
                executor.setMaximumPoolSize(reducedMax);

                logger.debug("Applied backpressure to CPU pool: reduced max size from {} to {}",
                        currentMax, reducedMax);
            }
        }
    }

    /**
     * Releases backpressure from the CPU thread pool.
     */
    public void releaseBackpressure() {
        synchronized (backpressureLock) {
            this.currentBackpressure = 0.0;

            // Restore original pool size
            executor.setMaximumPoolSize(poolConfig.getMaximumPoolSize());

            logger.debug("Released backpressure from CPU pool: restored max size to {}",
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
     * Triggers adaptive resizing based on CPU utilization.
     */
    public void triggerAdaptiveResizing() {
        if (executor == null || executor.isShutdown()) {
            return;
        }

        // Use thread utilization as a proxy for CPU usage
        double cpuUsage = (double) activeThreads.get() / systemInfo.getAvailableProcessors();
        int activeThreads = executor.getActiveCount();
        int coreSize = executor.getCorePoolSize();
        int maxSize = executor.getMaximumPoolSize();

        // Adaptive resizing based on CPU usage and thread utilization
        if (cpuUsage > 0.8 && activeThreads > coreSize * 0.8) {
            // High CPU usage - reduce pool size
            int newMaxSize = Math.max(coreSize, (int) (maxSize * 0.9));
            executor.setMaximumPoolSize(newMaxSize);

            logger.debug("Adaptive resize: reduced CPU pool max size to {} (CPU usage: {:.2f})",
                    newMaxSize, cpuUsage);
        } else if (cpuUsage < 0.5 && activeThreads < coreSize * 0.5) {
            // Low CPU usage - can increase pool size if needed
            int newMaxSize = Math.min(poolConfig.getMaximumPoolSize(),
                    (int) (maxSize * 1.1));
            executor.setMaximumPoolSize(newMaxSize);

            logger.debug("Adaptive resize: increased CPU pool max size to {} (CPU usage: {:.2f})",
                    newMaxSize, cpuUsage);
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

        logger.info("Updated CPU pool configuration: {}", newConfig);
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
                0.9, // targetEfficiency - higher for CPU pool
                executor.getQueue().size() * 500.0, // averageLatency (CPU operations are faster)
                executor.getCompletedTaskCount() > 0
                        ? (double) executor.getCompletedTaskCount() / (System.currentTimeMillis() / 1000.0)
                        : 0.0 // throughput
        );
    }

    /**
     * Specialized thread factory for CPU operations with affinity support.
     */
    private static class CpuThreadFactory implements ThreadFactory {
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;
        private final int priority;
        private final int[] affinityCores;

        CpuThreadFactory(String namePrefix, int priority, int[] affinityCores) {
            this.namePrefix = namePrefix;
            this.priority = priority;
            this.affinityCores = affinityCores;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, namePrefix + "-" + threadNumber.getAndIncrement());
            t.setPriority(priority);
            t.setDaemon(false);

            // Apply CPU affinity if configured
            if (affinityCores != null && affinityCores.length > 0) {
                try {
                    int coreIndex = (threadNumber.get() - 1) % affinityCores.length;
                    int targetCore = affinityCores[coreIndex];
                    setThreadAffinity(t, targetCore);
                } catch (Exception e) {
                    LoggerFactory.getLogger(CpuThreadFactory.class)
                            .warn("Failed to set CPU affinity for thread {}", t.getName(), e);
                }
            }

            // Set uncaught exception handler
            t.setUncaughtExceptionHandler((thread, ex) -> {
                LoggerFactory.getLogger(CpuThreadFactory.class)
                        .error("Uncaught exception in CPU thread {}", thread.getName(), ex);
            });

            return t;
        }

        /**
         * Sets CPU affinity for a thread (platform-specific implementation).
         */
        private void setThreadAffinity(Thread thread, int coreId) {
            // This would use platform-specific APIs to set affinity
            // For now, just log the intention
            if (logger.isDebugEnabled()) {
                logger.debug("Setting CPU affinity for thread {} to core {}",
                        thread.getName(), coreId);
            }
        }
    }
}