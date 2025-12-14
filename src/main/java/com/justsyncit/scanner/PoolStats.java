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
    private PoolStats(Builder builder) {
        this.poolType = builder.poolType;
        this.corePoolSize = builder.corePoolSize;
        this.maximumPoolSize = builder.maximumPoolSize;
        this.activeThreadCount = builder.activeThreadCount;
        this.queuedTaskCount = builder.queuedTaskCount;
        this.completedTaskCount = builder.completedTaskCount;
        this.totalExecutionTime = builder.totalExecutionTime;
        this.averageExecutionTime = builder.averageExecutionTime;
        this.backpressureLevel = builder.backpressureLevel;
        this.isUnderBackpressure = builder.isUnderBackpressure;
        this.lastResizingTime = builder.lastResizingTime;
        this.resizingCount = builder.resizingCount;
        this.cpuUsage = builder.cpuUsage;
        this.memoryUsage = builder.memoryUsage;
    }

    public static class Builder {
        private ThreadPoolManager.PoolType poolType;
        private int corePoolSize;
        private int maximumPoolSize;
        private int activeThreadCount;
        private int queuedTaskCount;
        private long completedTaskCount;
        private long totalExecutionTime;
        private double averageExecutionTime;
        private double backpressureLevel;
        private boolean isUnderBackpressure;
        private long lastResizingTime;
        private int resizingCount;
        private double cpuUsage;
        private long memoryUsage;

        public Builder setPoolType(ThreadPoolManager.PoolType poolType) {
            this.poolType = poolType;
            return this;
        }

        public Builder setCorePoolSize(int corePoolSize) {
            this.corePoolSize = corePoolSize;
            return this;
        }

        public Builder setMaximumPoolSize(int maximumPoolSize) {
            this.maximumPoolSize = maximumPoolSize;
            return this;
        }

        public Builder setActiveThreadCount(int activeThreadCount) {
            this.activeThreadCount = activeThreadCount;
            return this;
        }

        public Builder setQueuedTaskCount(int queuedTaskCount) {
            this.queuedTaskCount = queuedTaskCount;
            return this;
        }

        public Builder setCompletedTaskCount(long completedTaskCount) {
            this.completedTaskCount = completedTaskCount;
            return this;
        }

        public Builder setTotalExecutionTime(long totalExecutionTime) {
            this.totalExecutionTime = totalExecutionTime;
            return this;
        }

        public Builder setAverageExecutionTime(double averageExecutionTime) {
            this.averageExecutionTime = averageExecutionTime;
            return this;
        }

        public Builder setBackpressureLevel(double backpressureLevel) {
            this.backpressureLevel = backpressureLevel;
            return this;
        }

        public Builder setUnderBackpressure(boolean isUnderBackpressure) {
            this.isUnderBackpressure = isUnderBackpressure;
            return this;
        }

        public Builder setLastResizingTime(long lastResizingTime) {
            this.lastResizingTime = lastResizingTime;
            return this;
        }

        public Builder setResizingCount(int resizingCount) {
            this.resizingCount = resizingCount;
            return this;
        }

        public Builder setCpuUsage(double cpuUsage) {
            this.cpuUsage = cpuUsage;
            return this;
        }

        public Builder setMemoryUsage(long memoryUsage) {
            this.memoryUsage = memoryUsage;
            return this;
        }

        public PoolStats build() {
            return new PoolStats(this);
        }
    }

    // Getters
    public ThreadPoolManager.PoolType getPoolType() {
        return poolType;
    }

    public int getCorePoolSize() {
        return corePoolSize;
    }

    public int getMaximumPoolSize() {
        return maximumPoolSize;
    }

    public int getActiveThreadCount() {
        return activeThreadCount;
    }

    public int getQueuedTaskCount() {
        return queuedTaskCount;
    }

    public long getCompletedTaskCount() {
        return completedTaskCount;
    }

    public long getTotalExecutionTime() {
        return totalExecutionTime;
    }

    public double getAverageExecutionTime() {
        return averageExecutionTime;
    }

    public double getBackpressureLevel() {
        return backpressureLevel;
    }

    public boolean isUnderBackpressure() {
        return isUnderBackpressure;
    }

    public long getLastResizingTime() {
        return lastResizingTime;
    }

    public int getResizingCount() {
        return resizingCount;
    }

    public double getCpuUsage() {
        return cpuUsage;
    }

    public long getMemoryUsage() {
        return memoryUsage;
    }

    @Override
    public String toString() {
        return String.format(
                "PoolStats{type=%s, core=%d, max=%d, active=%d, queued=%d, "
                        + "completed=%d, avgExecTime=%.2fms, backpressure=%.2f, "
                        + "underPressure=%b, lastResize=%d, resizeCount=%d, cpu=%.2f%%, memory=%dMB}",
                poolType.getName(), corePoolSize, maximumPoolSize, activeThreadCount,
                queuedTaskCount, completedTaskCount, averageExecutionTime / 1000000.0,
                backpressureLevel, isUnderBackpressure, lastResizingTime,
                resizingCount, cpuUsage * 100, memoryUsage / 1024 / 1024);
    }
}