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

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Coordinates resources across thread pools.
 * Manages CPU, memory, and I/O resource allocation.
 */
public class ResourceCoordinator {
    
    private static final Logger logger = LoggerFactory.getLogger(ResourceCoordinator.class);
    
    private final ThreadPoolConfiguration config;
    private final SystemResourceInfo systemInfo;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean running = new AtomicBoolean(true);
    
    // Resource tracking
    private final AtomicInteger activeThreads = new AtomicInteger(0);
    private final AtomicLong totalMemoryAllocated = new AtomicLong(0);
    private final AtomicLong totalCpuTime = new AtomicLong(0);
    private final AtomicInteger resourceConflicts = new AtomicInteger(0);
    
    /**
     * Creates a new ResourceCoordinator.
     */
    public ResourceCoordinator(ThreadPoolConfiguration config, SystemResourceInfo systemInfo) {
        this.config = config;
        this.systemInfo = systemInfo;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ResourceCoordinator");
            t.setDaemon(true);
            return t;
        });
        
        logger.info("ResourceCoordinator initialized");
    }
    
    /**
     * Starts the resource coordinator.
     */
    public void start() {
        if (!running.get()) {
            return;
        }
        
        scheduler.scheduleAtFixedRate(this::performCoordination, 5, 5, TimeUnit.SECONDS);
        logger.info("ResourceCoordinator started");
    }
    
    /**
     * Performs resource coordination.
     */
    private void performCoordination() {
        try {
            // Check resource usage
            checkResourceUsage();
            
            // Apply resource limits if needed
            applyResourceLimits();
            
            // Resolve resource conflicts
            resolveResourceConflicts();
            
        } catch (Exception e) {
            logger.error("Error in resource coordination", e);
        }
    }
    
    /**
     * Checks current resource usage.
     */
    private void checkResourceUsage() {
        double memoryUsageRatio = calculateMemoryUsageRatio();
        double cpuUsageRatio = calculateCpuUsageRatio();
        
        if (logger.isDebugEnabled()) {
            logger.debug("Resource usage - memory: {:.2f}%, cpu: {:.2f}%, threads: {}",
                    memoryUsageRatio * 100, cpuUsageRatio * 100, activeThreads.get());
        }
        
        // Trigger alerts if thresholds exceeded
        if (memoryUsageRatio > config.getMemoryPressureThreshold()) {
            logger.warn("Memory usage threshold exceeded: {:.2f}%", memoryUsageRatio * 100);
        }
        
        if (cpuUsageRatio > config.getMaxCpuUsageThreshold()) {
            logger.warn("CPU usage threshold exceeded: {:.2f}%", cpuUsageRatio * 100);
        }
    }
    
    /**
     * Calculates memory usage ratio.
     */
    private double calculateMemoryUsageRatio() {
        long usedMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long maxMemory = Runtime.getRuntime().maxMemory();
        return maxMemory > 0 ? (double) usedMemory / maxMemory : 0.0;
    }
    
    /**
     * Calculates CPU usage ratio (simplified).
     */
    private double calculateCpuUsageRatio() {
        // Simplified CPU usage calculation
        // In a real implementation, you'd use OS-specific metrics
        return Math.min(1.0, (double) activeThreads.get() / systemInfo.getAvailableProcessors());
    }
    
    /**
     * Applies resource limits if needed.
     */
    private void applyResourceLimits() {
        // This would implement resource limiting logic
        // For now, just log the action
        if (logger.isTraceEnabled()) {
            logger.trace("Applying resource limits based on current usage");
        }
    }
    
    /**
     * Resolves resource conflicts.
     */
    private void resolveResourceConflicts() {
        // This would implement conflict resolution logic
        // For now, just log the action
        if (resourceConflicts.get() > 0) {
            logger.debug("Resolving {} resource conflicts", resourceConflicts.get());
            resourceConflicts.set(0);
        }
    }
    
    /**
     * Records thread activation.
     */
    public void recordThreadActivation() {
        activeThreads.incrementAndGet();
    }
    
    /**
     * Records thread deactivation.
     */
    public void recordThreadDeactivation() {
        activeThreads.decrementAndGet();
    }
    
    /**
     * Records memory allocation.
     */
    public void recordMemoryAllocation(long bytes) {
        totalMemoryAllocated.addAndGet(bytes);
    }
    
    /**
     * Records memory deallocation.
     */
    public void recordMemoryDeallocation(long bytes) {
        totalMemoryAllocated.addAndGet(-bytes);
    }
    
    /**
     * Records CPU time usage.
     */
    public void recordCpuTime(long nanos) {
        totalCpuTime.addAndGet(nanos);
    }
    
    /**
     * Records resource conflict.
     */
    public void recordResourceConflict() {
        resourceConflicts.incrementAndGet();
    }
    
    /**
     * Gets resource statistics.
     */
    public ResourceStats getResourceStats() {
        return new ResourceStats(
            activeThreads.get(),
            totalMemoryAllocated.get(),
            totalCpuTime.get(),
            resourceConflicts.get(),
            calculateMemoryUsageRatio(),
            calculateCpuUsageRatio()
        );
    }
    
    /**
     * Resource statistics snapshot.
     */
    public static class ResourceStats {
        public final int activeThreads;
        public final long totalMemoryAllocated;
        public final long totalCpuTime;
        public final int resourceConflicts;
        public final double memoryUsageRatio;
        public final double cpuUsageRatio;
        
        ResourceStats(int activeThreads, long totalMemoryAllocated, long totalCpuTime,
                     int resourceConflicts, double memoryUsageRatio, double cpuUsageRatio) {
            this.activeThreads = activeThreads;
            this.totalMemoryAllocated = totalMemoryAllocated;
            this.totalCpuTime = totalCpuTime;
            this.resourceConflicts = resourceConflicts;
            this.memoryUsageRatio = memoryUsageRatio;
            this.cpuUsageRatio = cpuUsageRatio;
        }
        
        @Override
        public String toString() {
            return String.format(
                    "ResourceStats{activeThreads=%d, memoryAllocated=%dMB, cpuTime=%ds, " +
                    "conflicts=%d, memoryUsage=%.2f%%, cpuUsage=%.2f%%}",
                    activeThreads, totalMemoryAllocated / 1024 / 1024, totalCpuTime / 1000000000,
                    resourceConflicts, memoryUsageRatio * 100, cpuUsageRatio * 100
            );
        }
    }
    
    /**
     * Shuts down the resource coordinator.
     */
    public void shutdown() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        logger.info("ResourceCoordinator shutdown completed");
    }
}