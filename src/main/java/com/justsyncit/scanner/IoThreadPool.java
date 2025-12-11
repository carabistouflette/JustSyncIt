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

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Specialized thread pool for I/O operations.
 * Optimized for AsynchronousFileChannel operations with appropriate queue sizing
 * and thread management strategies for I/O-bound workloads.
 */
public class IoThreadPool extends ManagedThreadPool {
    
    private static final Logger logger = LoggerFactory.getLogger(IoThreadPool.class);
    
    private volatile double currentBackpressure = 0.0;
    private final Object backpressureLock = new Object();
    
    /**
     * Creates a new IoThreadPool.
     */
    public IoThreadPool(ThreadPoolConfiguration.PoolConfig poolConfig,
                       SystemResourceInfo systemInfo,
                       ThreadPoolMonitor monitor) {
        super(poolConfig, systemInfo, monitor, ThreadPoolManager.PoolType.IO);
        
        logger.info("IoThreadPool initialized with config: {}", poolConfig);
    }
    
    @Override
    protected BlockingQueue<Runnable> createWorkQueue() {
        // I/O operations benefit from larger queues to buffer bursts
        return new LinkedBlockingQueue<>(Math.max(500, poolConfig.getQueueCapacity()));
    }
    
    @Override
    protected ThreadFactory createThreadFactory() {
        return new IoThreadFactory(poolConfig.getThreadNamePrefix(), 
                                  poolConfig.getPriority().getValue());
    }
    
    @Override
    protected RejectedExecutionHandler createRejectionHandler() {
        // For I/O operations, prefer caller runs to prevent task loss
        return new ThreadPoolExecutor.CallerRunsPolicy();
    }
    
    /**
     * Applies backpressure to the I/O thread pool.
     */
    public void applyBackpressure(double pressureLevel) {
        synchronized (backpressureLock) {
            this.currentBackpressure = Math.max(0.0, Math.min(1.0, pressureLevel));
            
            if (currentBackpressure > 0.7) {
                // High backpressure - reduce pool size temporarily
                int currentMax = executor.getMaximumPoolSize();
                int reducedMax = Math.max(poolConfig.getCorePoolSize(), 
                                        (int) (currentMax * (1.0 - currentBackpressure * 0.5)));
                executor.setMaximumPoolSize(reducedMax);
                
                logger.debug("Applied high backpressure to I/O pool: reduced max size from {} to {}",
                           currentMax, reducedMax);
            }
        }
    }
    
    /**
     * Releases backpressure from the I/O thread pool.
     */
    public void releaseBackpressure() {
        synchronized (backpressureLock) {
            this.currentBackpressure = 0.0;
            
            // Restore original pool size
            executor.setMaximumPoolSize(poolConfig.getMaximumPoolSize());
            
            logger.debug("Released backpressure from I/O pool: restored max size to {}",
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
     * Triggers adaptive resizing based on current load.
     */
    public void triggerAdaptiveResizing() {
        if (executor == null || executor.isShutdown()) {
            return;
        }
        
        int activeThreads = executor.getActiveCount();
        int coreSize = executor.getCorePoolSize();
        int maxSize = executor.getMaximumPoolSize();
        int queueSize = executor.getQueue().size();
        
        // Calculate load factor
        double loadFactor = (double) (activeThreads + queueSize) / maxSize;
        
        if (loadFactor > 0.8 && maxSize < poolConfig.getMaximumPoolSize()) {
            // High load - increase pool size
            int newMaxSize = Math.min(poolConfig.getMaximumPoolSize(), 
                                    (int) (maxSize * 1.2));
            executor.setMaximumPoolSize(newMaxSize);
            
            logger.debug("Adaptive resize: increased I/O pool max size to {} (load factor: {:.2f})",
                       newMaxSize, loadFactor);
        } else if (loadFactor < 0.3 && maxSize > coreSize) {
            // Low load - decrease pool size
            int newMaxSize = Math.max(coreSize, (int) (maxSize * 0.9));
            executor.setMaximumPoolSize(newMaxSize);
            
            logger.debug("Adaptive resize: decreased I/O pool max size to {} (load factor: {:.2f})",
                       newMaxSize, loadFactor);
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
        
        logger.info("Updated I/O pool configuration: {}", newConfig);
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
            0.8, // targetEfficiency
            executor.getQueue().size() * 1000.0, // averageLatency (approximate)
            executor.getCompletedTaskCount() > 0 
                ? (double) executor.getCompletedTaskCount() / (System.currentTimeMillis() / 1000.0)
                : 0.0 // throughput
        );
    }
    
    /**
     * Specialized thread factory for I/O operations.
     */
    private static class IoThreadFactory implements ThreadFactory {
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;
        private final int priority;
        
        IoThreadFactory(String namePrefix, int priority) {
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
                LoggerFactory.getLogger(IoThreadFactory.class)
                        .error("Uncaught exception in I/O thread {}", thread.getName(), ex);
            });
            
            return t;
        }
    }
}