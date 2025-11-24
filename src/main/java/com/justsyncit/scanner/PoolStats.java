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

/**
 * Statistics for a thread pool.
 * Provides comprehensive metrics for monitoring and optimization.
 */
public class PoolStats {
    
    private final ThreadPoolManager.PoolType poolType;
    private final int corePoolSize;
    private final int maximumPoolSize;
    private final int activeThreadCount;
    private final int queuedTaskCount;
    private final long completedTaskCount;
    private final long totalExecutionTime;
    private final double averageExecutionTime;
    private final double backpressureLevel;
    private final boolean isUnderBackpressure;
    private final long lastResizingTime;
    private final int resizingCount;
    private final double cpuUsage;
    private final long memoryUsage;
    
    /**
     * Creates a new PoolStats.
     */
    public PoolStats(ThreadPoolManager.PoolType poolType,
                   int corePoolSize, int maximumPoolSize,
                   int activeThreadCount, int queuedTaskCount,
                   long completedTaskCount, long totalExecutionTime,
                   double averageExecutionTime, double backpressureLevel,
                   boolean isUnderBackpressure, long lastResizingTime,
                   int resizingCount, double cpuUsage, long memoryUsage) {
        this.poolType = poolType;
        this.corePoolSize = corePoolSize;
        this.maximumPoolSize = maximumPoolSize;
        this.activeThreadCount = activeThreadCount;
        this.queuedTaskCount = queuedTaskCount;
        this.completedTaskCount = completedTaskCount;
        this.totalExecutionTime = totalExecutionTime;
        this.averageExecutionTime = averageExecutionTime;
        this.backpressureLevel = backpressureLevel;
        this.isUnderBackpressure = isUnderBackpressure;
        this.lastResizingTime = lastResizingTime;
        this.resizingCount = resizingCount;
        this.cpuUsage = cpuUsage;
        this.memoryUsage = memoryUsage;
    }
    
    // Getters
    public ThreadPoolManager.PoolType getPoolType() { return poolType; }
    public int getCorePoolSize() { return corePoolSize; }
    public int getMaximumPoolSize() { return maximumPoolSize; }
    public int getActiveThreadCount() { return activeThreadCount; }
    public int getQueuedTaskCount() { return queuedTaskCount; }
    public long getCompletedTaskCount() { return completedTaskCount; }
    public long getTotalExecutionTime() { return totalExecutionTime; }
    public double getAverageExecutionTime() { return averageExecutionTime; }
    public double getBackpressureLevel() { return backpressureLevel; }
    public boolean isUnderBackpressure() { return isUnderBackpressure; }
    public long getLastResizingTime() { return lastResizingTime; }
    public int getResizingCount() { return resizingCount; }
    public double getCpuUsage() { return cpuUsage; }
    public long getMemoryUsage() { return memoryUsage; }
    
    @Override
    public String toString() {
        return String.format(
            "PoolStats{type=%s, core=%d, max=%d, active=%d, queued=%d, " +
            "completed=%d, avgExecTime=%.2fms, backpressure=%.2f, " +
            "underPressure=%b, lastResize=%d, resizeCount=%d, cpu=%.2f%%, memory=%dMB}",
            poolType.getName(), corePoolSize, maximumPoolSize, activeThreadCount,
            queuedTaskCount, completedTaskCount, averageExecutionTime / 1000000.0,
            backpressureLevel, isUnderBackpressure, lastResizingTime,
            resizingCount, cpuUsage * 100, memoryUsage / 1024 / 1024
        );
    }
}