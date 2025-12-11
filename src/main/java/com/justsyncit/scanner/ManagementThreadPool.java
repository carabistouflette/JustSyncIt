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
import java.util.concurrent.atomic.AtomicLong;

/**
 * Specialized thread pool for management and internal operations.
 * Optimized for low-priority maintenance tasks with minimal resource impact
 * and efficient background processing.
 */
public class ManagementThreadPool extends ManagedThreadPool {
    
    private static final Logger logger = LoggerFactory.getLogger(ManagementThreadPool.class);
    
    private volatile double currentBackpressure = 0.0;
    private final Object backpressureLock = new Object();
    private final AtomicLong maintenanceTasksProcessed = new AtomicLong(0);
    private final AtomicLong cleanupTasksProcessed = new AtomicLong(0);
    private final AtomicInteger activeManagementTasks = new AtomicInteger(0);
    
    /**
     * Creates a new ManagementThreadPool.
     */
    public ManagementThreadPool(ThreadPoolConfiguration.PoolConfig poolConfig,
                           SystemResourceInfo systemInfo,
                           ThreadPoolMonitor monitor) {
        super(poolConfig, systemInfo, monitor, ThreadPoolManager.PoolType.MANAGEMENT);
        
        logger.info("ManagementThreadPool initialized with config: {}", poolConfig);
    }
    
    @Override
    protected BlockingQueue<Runnable> createWorkQueue() {
        // Management tasks benefit from moderate queue capacity
        return new LinkedBlockingQueue<>(Math.max(100, poolConfig.getQueueCapacity()));
    }
    
    @Override
    protected ThreadFactory createThreadFactory() {
        return new ManagementThreadFactory(poolConfig.getThreadNamePrefix(), 
                                      poolConfig.getPriority().getValue());
    }
    
    @Override
    protected RejectedExecutionHandler createRejectionHandler() {
        // For management tasks, prefer caller runs to ensure tasks are processed
        return new ThreadPoolExecutor.CallerRunsPolicy();
    }
    
    /**
     * Executes a maintenance task.
     */
    public void executeMaintenance(Runnable maintenanceTask) {
        maintenanceTasksProcessed.incrementAndGet();
        activeManagementTasks.incrementAndGet();
        
        // Wrap task for monitoring
        Runnable monitoredTask = new ManagementTask(maintenanceTask, TaskType.MAINTENANCE);
        execute(monitoredTask);
    }
    
    /**
     * Executes a cleanup task.
     */
    public void executeCleanup(Runnable cleanupTask) {
        cleanupTasksProcessed.incrementAndGet();
        activeManagementTasks.incrementAndGet();
        
        // Wrap task for monitoring
        Runnable monitoredTask = new ManagementTask(cleanupTask, TaskType.CLEANUP);
        execute(monitoredTask);
    }
    
    /**
     * Executes a general management task.
     */
    public void executeManagement(Runnable managementTask) {
        activeManagementTasks.incrementAndGet();
        
        // Wrap task for monitoring
        Runnable monitoredTask = new ManagementTask(managementTask, TaskType.GENERAL);
        execute(monitoredTask);
    }
    
    /**
     * Applies backpressure to the management thread pool.
     */
    public void applyBackpressure(double pressureLevel) {
        synchronized (backpressureLock) {
            this.currentBackpressure = Math.max(0.0, Math.min(1.0, pressureLevel));
            
            if (currentBackpressure > 0.9) {
                // Very high backpressure - significantly reduce pool size
                int currentMax = executor.getMaximumPoolSize();
                int reducedMax = Math.max(poolConfig.getCorePoolSize(), 
                                        (int) (currentMax * (1.0 - currentBackpressure * 0.6)));
                executor.setMaximumPoolSize(reducedMax);
                
                logger.debug("Applied high backpressure to Management pool: reduced max size from {} to {}",
                           currentMax, reducedMax);
            }
        }
    }
    
    /**
     * Releases backpressure from the management thread pool.
     */
    public void releaseBackpressure() {
        synchronized (backpressureLock) {
            this.currentBackpressure = 0.0;
            
            // Restore original pool size
            executor.setMaximumPoolSize(poolConfig.getMaximumPoolSize());
            
            logger.debug("Released backpressure from Management pool: restored max size to {}",
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
     * Triggers adaptive resizing based on management task load.
     */
    public void triggerAdaptiveResizing() {
        if (executor == null || executor.isShutdown()) {
            return;
        }
        
        int activeThreads = executor.getActiveCount();
        int coreSize = executor.getCorePoolSize();
        int maxSize = executor.getMaximumPoolSize();
        int queueSize = executor.getQueue().size();
        
        // Calculate management task processing rate
        long totalTasks = maintenanceTasksProcessed.get() + cleanupTasksProcessed.get();
        double taskRate = totalTasks > 0 ? (double) queueSize / totalTasks : 0.0;
        
        // Management tasks should have minimal impact, so be conservative with resizing
        if (taskRate > 0.5 && maxSize < poolConfig.getMaximumPoolSize()) {
            // High task rate - modest increase in pool size
            int newMaxSize = Math.min(poolConfig.getMaximumPoolSize(), 
                                    (int) (maxSize * 1.1));
            executor.setMaximumPoolSize(newMaxSize);
            
            logger.debug("Adaptive resize: increased Management pool max size to {} (taskRate: {:.2f})",
                       newMaxSize, taskRate);
        } else if (taskRate < 0.1 && maxSize > coreSize) {
            // Low task rate - decrease pool size
            int newMaxSize = Math.max(coreSize, (int) (maxSize * 0.9));
            executor.setMaximumPoolSize(newMaxSize);
            
            logger.debug("Adaptive resize: decreased Management pool max size to {} (taskRate: {:.2f})",
                       newMaxSize, taskRate);
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
        
        logger.info("Updated Management pool configuration: {}", newConfig);
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
            0.5, // targetEfficiency - lower for management (low priority)
            executor.getQueue().size() * 300.0, // averageLatency (higher latency acceptable)
            executor.getCompletedTaskCount() > 0 
                ? (double) executor.getCompletedTaskCount() / (System.currentTimeMillis() / 1000.0)
                : 0.0 // throughput
        );
    }
    
    /**
     * Gets management task statistics.
     */
    public ManagementTaskStats getManagementTaskStats() {
        return new ManagementTaskStats(
            maintenanceTasksProcessed.get(),
            cleanupTasksProcessed.get(),
            activeManagementTasks.get(),
            getCurrentBackpressure()
        );
    }
    
    /**
     * Management task statistics.
     */
    public static class ManagementTaskStats {
        public final long maintenanceTasksProcessed;
        public final long cleanupTasksProcessed;
        public final int activeManagementTasks;
        public final double backpressureLevel;
        
        ManagementTaskStats(long maintenanceTasksProcessed, long cleanupTasksProcessed,
                          int activeManagementTasks, double backpressureLevel) {
            this.maintenanceTasksProcessed = maintenanceTasksProcessed;
            this.cleanupTasksProcessed = cleanupTasksProcessed;
            this.activeManagementTasks = activeManagementTasks;
            this.backpressureLevel = backpressureLevel;
        }
        
        @Override
        public String toString() {
            return String.format(
                    "ManagementTaskStats{maintenance=%d, cleanup=%d, active=%d, backpressure=%.2f}",
                    maintenanceTasksProcessed, cleanupTasksProcessed, activeManagementTasks, backpressureLevel
            );
        }
    }
    
    /**
     * Task types for management operations.
     */
    private enum TaskType {
        MAINTENANCE, CLEANUP, GENERAL
    }
    
    /**
     * Wrapper for management tasks.
     */
    private class ManagementTask implements Runnable {
        private final Runnable delegate;
        private final TaskType taskType;
        
        ManagementTask(Runnable delegate, TaskType taskType) {
            this.delegate = delegate;
            this.taskType = taskType;
        }
        
        @Override
        public void run() {
            try {
                if (logger.isTraceEnabled()) {
                    logger.trace("Executing management task of type: {}", taskType);
                }
                delegate.run();
            } finally {
                activeManagementTasks.decrementAndGet();
            }
        }
    }
    
    /**
     * Specialized thread factory for management operations.
     */
    private static class ManagementThreadFactory implements ThreadFactory {
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;
        private final int priority;
        
        ManagementThreadFactory(String namePrefix, int priority) {
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
                LoggerFactory.getLogger(ManagementThreadFactory.class)
                        .error("Uncaught exception in Management thread {}", thread.getName(), ex);
            });
            
            return t;
        }
    }
}