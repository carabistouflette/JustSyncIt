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
 * Specialized thread pool for directory monitoring operations.
 * Optimized for WatchService event processing with low resource usage
 * and efficient event handling patterns.
 */
public class WatchServiceThreadPool extends ManagedThreadPool {

    private static final Logger logger = LoggerFactory.getLogger(WatchServiceThreadPool.class);

    private volatile double currentBackpressure = 0.0;
    private final Object backpressureLock = new Object();
    private final AtomicLong eventsProcessed = new AtomicLong(0);
    private final AtomicInteger activeWatchers = new AtomicInteger(0);
    private final AtomicInteger queuedEvents = new AtomicInteger(0);

    /**
     * Creates a new WatchServiceThreadPool.
     */
    public WatchServiceThreadPool(ThreadPoolConfiguration.PoolConfig poolConfig,
            SystemResourceInfo systemInfo,
            ThreadPoolMonitor monitor) {
        super(poolConfig, systemInfo, monitor, ThreadPoolManager.PoolType.WATCH_SERVICE,
                new LinkedBlockingQueue<>(Math.max(50, poolConfig.getQueueCapacity())),
                new WatchServiceThreadFactory(poolConfig.getThreadNamePrefix(), poolConfig.getPriority().getValue()),
                new ThreadPoolExecutor.CallerRunsPolicy());

        logger.info("WatchServiceThreadPool initialized with config: {}", poolConfig);
    }

    /**
     * Executes a watch service event processing task.
     */
    public void executeWatchEvent(Runnable eventTask) {
        eventsProcessed.incrementAndGet();
        queuedEvents.incrementAndGet();

        // Wrap task for monitoring
        Runnable monitoredTask = new WatchEventTask(eventTask);
        execute(monitoredTask);
    }

    /**
     * Registers a new watcher.
     */
    public void registerWatcher() {
        activeWatchers.incrementAndGet();
        logger.debug("Registered new watcher, total active watchers: {}", activeWatchers.get());
    }

    /**
     * Unregisters a watcher.
     */
    public void unregisterWatcher() {
        activeWatchers.decrementAndGet();
        logger.debug("Unregistered watcher, total active watchers: {}", activeWatchers.get());
    }

    /**
     * Applies backpressure to the watch service thread pool.
     */
    public void applyBackpressure(double pressureLevel) {
        synchronized (backpressureLock) {
            this.currentBackpressure = Math.max(0.0, Math.min(1.0, pressureLevel));

            if (currentBackpressure > 0.8) {
                // High backpressure - reduce pool size
                int currentMax = executor.getMaximumPoolSize();
                int reducedMax = Math.max(poolConfig.getCorePoolSize(),
                        (int) (currentMax * (1.0 - currentBackpressure * 0.5)));
                executor.setMaximumPoolSize(reducedMax);

                logger.debug("Applied high backpressure to WatchService pool: reduced max size from {} to {}",
                        currentMax, reducedMax);
            }
        }
    }

    /**
     * Releases backpressure from the watch service thread pool.
     */
    public void releaseBackpressure() {
        synchronized (backpressureLock) {
            this.currentBackpressure = 0.0;

            // Restore original pool size
            executor.setMaximumPoolSize(poolConfig.getMaximumPoolSize());

            logger.debug("Released backpressure from WatchService pool: restored max size to {}",
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
     * Triggers adaptive resizing based on watch service load.
     */
    public void triggerAdaptiveResizing() {
        if (executor == null || executor.isShutdown()) {
            return;
        }

        int activeThreads = executor.getActiveCount();
        int coreSize = executor.getCorePoolSize();
        int maxSize = executor.getMaximumPoolSize();
        int queueSize = executor.getQueue().size();

        // Calculate event processing rate
        long totalEvents = eventsProcessed.get();
        double eventRate = totalEvents > 0 ? (double) queueSize / totalEvents : 0.0;

        // Consider number of active watchers
        double watcherLoad = activeWatchers.get() > 0
                ? (double) activeThreads / activeWatchers.get()
                : 0.0;

        if ((eventRate > 1.0 || watcherLoad > 2.0) && maxSize < poolConfig.getMaximumPoolSize()) {
            // High event rate or watcher load - increase pool size
            int newMaxSize = Math.min(poolConfig.getMaximumPoolSize(),
                    (int) (maxSize * 1.15));
            executor.setMaximumPoolSize(newMaxSize);

            logger.debug(
                    "Adaptive resize: increased WatchService pool max size to {} (eventRate: {:.2f}, watcherLoad: {:.2f})",
                    newMaxSize, eventRate, watcherLoad);
        } else if (eventRate < 0.2 && watcherLoad < 0.5 && maxSize > coreSize) {
            // Low event rate and watcher load - decrease pool size
            int newMaxSize = Math.max(coreSize, (int) (maxSize * 0.85));
            executor.setMaximumPoolSize(newMaxSize);

            logger.debug(
                    "Adaptive resize: decreased WatchService pool max size to {} (eventRate: {:.2f}, watcherLoad: {:.2f})",
                    newMaxSize, eventRate, watcherLoad);
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

        logger.info("Updated WatchService pool configuration: {}", newConfig);
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
                0.6, // targetEfficiency - lower for watch service (low priority)
                executor.getQueue().size() * 50.0, // averageLatency (very low latency not critical)
                executor.getCompletedTaskCount() > 0
                        ? (double) executor.getCompletedTaskCount() / (System.currentTimeMillis() / 1000.0)
                        : 0.0 // throughput
        );
    }

    /**
     * Gets watch service statistics.
     */
    public WatchServiceStats getWatchServiceStats() {
        return new WatchServiceStats(
                eventsProcessed.get(),
                activeWatchers.get(),
                queuedEvents.get(),
                getCurrentBackpressure());
    }

    /**
     * Watch service statistics.
     */
    public static class WatchServiceStats {
        public final long eventsProcessed;
        public final int activeWatchers;
        public final int queuedEvents;
        public final double backpressureLevel;

        WatchServiceStats(long eventsProcessed, int activeWatchers,
                int queuedEvents, double backpressureLevel) {
            this.eventsProcessed = eventsProcessed;
            this.activeWatchers = activeWatchers;
            this.queuedEvents = queuedEvents;
            this.backpressureLevel = backpressureLevel;
        }

        @Override
        public String toString() {
            return String.format(
                    "WatchServiceStats{events=%d, watchers=%d, queued=%d, backpressure=%.2f}",
                    eventsProcessed, activeWatchers, queuedEvents, backpressureLevel);
        }
    }

    /**
     * Wrapper for watch event tasks.
     */
    private class WatchEventTask implements Runnable {
        private final Runnable delegate;

        WatchEventTask(Runnable delegate) {
            this.delegate = delegate;
        }

        @Override
        public void run() {
            try {
                delegate.run();
            } finally {
                queuedEvents.decrementAndGet();
            }
        }
    }

    /**
     * Specialized thread factory for watch service operations.
     */
    private static class WatchServiceThreadFactory implements ThreadFactory {
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;
        private final int priority;

        WatchServiceThreadFactory(String namePrefix, int priority) {
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
                LoggerFactory.getLogger(WatchServiceThreadFactory.class)
                        .error("Uncaught exception in WatchService thread {}", thread.getName(), ex);
            });

            return t;
        }
    }
}