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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Statistics for thread pool performance and utilization.
 * Provides comprehensive metrics for monitoring and optimization.
 */
public class ThreadPoolStats {

    private final String poolName;
    private final int corePoolSize;
    private final int maximumPoolSize;
    private final int activeThreads;
    private final int totalTasks;
    private final int completedTasks;
    private final int queueSize;
    private final long submittedTasks;
    private final long completedSubmittedTasks;
    private final int currentQueueSize;

    // Additional performance metrics
    private final double averageExecutionTime;
    private final double throughput;
    private final double utilizationRate;
    private final double efficiency;

    // Pool-specific statistics
    private final Map<ThreadPoolManager.PoolType, PoolSpecificStats> poolStats = new ConcurrentHashMap<>();

    /**
     * Creates new ThreadPoolStats.
     */
    private ThreadPoolStats(Builder builder) {
        this.poolName = builder.poolName;
        this.corePoolSize = builder.corePoolSize;
        this.maximumPoolSize = builder.maximumPoolSize;
        this.activeThreads = builder.activeThreads;
        this.totalTasks = builder.totalTasks;
        this.completedTasks = builder.completedTasks;
        this.queueSize = builder.queueSize;
        this.submittedTasks = builder.submittedTasks;
        this.completedSubmittedTasks = builder.completedSubmittedTasks;
        this.currentQueueSize = builder.currentQueueSize;
        this.averageExecutionTime = builder.averageExecutionTime;
        this.throughput = builder.throughput;
        this.utilizationRate = builder.utilizationRate;
        this.efficiency = builder.efficiency;
    }

    public static class Builder {
        private String poolName;
        private int corePoolSize;
        private int maximumPoolSize;
        private int activeThreads;
        private int totalTasks;
        private int completedTasks;
        private int queueSize;
        private long submittedTasks;
        private long completedSubmittedTasks;
        private int currentQueueSize;
        private double averageExecutionTime;
        private double throughput;
        private double utilizationRate;
        private double efficiency;

        public Builder setPoolName(String poolName) {
            this.poolName = poolName;
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

        public Builder setActiveThreads(int activeThreads) {
            this.activeThreads = activeThreads;
            return this;
        }

        public Builder setTotalTasks(int totalTasks) {
            this.totalTasks = totalTasks;
            return this;
        }

        public Builder setCompletedTasks(int completedTasks) {
            this.completedTasks = completedTasks;
            return this;
        }

        public Builder setQueueSize(int queueSize) {
            this.queueSize = queueSize;
            return this;
        }

        public Builder setSubmittedTasks(long submittedTasks) {
            this.submittedTasks = submittedTasks;
            return this;
        }

        public Builder setCompletedSubmittedTasks(long completedSubmittedTasks) {
            this.completedSubmittedTasks = completedSubmittedTasks;
            return this;
        }

        public Builder setCurrentQueueSize(int currentQueueSize) {
            this.currentQueueSize = currentQueueSize;
            return this;
        }

        public Builder setAverageExecutionTime(double averageExecutionTime) {
            this.averageExecutionTime = averageExecutionTime;
            return this;
        }

        public Builder setThroughput(double throughput) {
            this.throughput = throughput;
            return this;
        }

        public Builder setUtilizationRate(double utilizationRate) {
            this.utilizationRate = utilizationRate;
            return this;
        }

        public Builder setEfficiency(double efficiency) {
            this.efficiency = efficiency;
            return this;
        }

        public ThreadPoolStats build() {
            // Calculate derived metrics if not set
            if (this.averageExecutionTime == 0.0) {
                this.averageExecutionTime = this.completedSubmittedTasks > 0
                        ? 1000000.0 / this.completedSubmittedTasks
                        : 0.0;
            }
            if (this.throughput == 0.0) {
                this.throughput = this.averageExecutionTime > 0
                        ? 1000000000.0 / this.averageExecutionTime
                        : 0.0;
            }
            if (this.utilizationRate == 0.0) {
                this.utilizationRate = this.maximumPoolSize > 0
                        ? (double) this.activeThreads / this.maximumPoolSize
                        : 0.0;
            }
            if (this.efficiency == 0.0) {
                this.efficiency = this.throughput > 0
                        ? this.utilizationRate * (1.0 - (this.averageExecutionTime / 1000000000.0))
                        : 0.0;
            }
            return new ThreadPoolStats(this);
        }
    }

    /**
     * Calculates average execution time.
     */
    /**
     * Gets pool name.
     */
    public String getPoolName() {
        return poolName;
    }

    /**
     * Gets core pool size.
     */
    public int getCorePoolSize() {
        return corePoolSize;
    }

    /**
     * Gets maximum pool size.
     */
    public int getMaximumPoolSize() {
        return maximumPoolSize;
    }

    /**
     * Gets active thread count.
     */
    public int getActiveThreads() {
        return activeThreads;
    }

    /**
     * Gets total task count.
     */
    public int getTotalTasks() {
        return totalTasks;
    }

    /**
     * Gets completed task count.
     */
    public int getCompletedTasks() {
        return completedTasks;
    }

    /**
     * Gets queue size.
     */
    public int getQueueSize() {
        return queueSize;
    }

    /**
     * Gets submitted task count.
     */
    public long getSubmittedTasks() {
        return submittedTasks;
    }

    /**
     * Gets completed submitted task count.
     */
    public long getCompletedSubmittedTasks() {
        return completedSubmittedTasks;
    }

    /**
     * Gets current queue size.
     */
    public int getCurrentQueueSize() {
        return currentQueueSize;
    }

    /**
     * Gets average execution time in nanoseconds.
     */
    public double getAverageExecutionTime() {
        return averageExecutionTime;
    }

    /**
     * Gets throughput in tasks per second.
     */
    public double getThroughput() {
        return throughput;
    }

    /**
     * Gets utilization rate (0.0 to 1.0).
     */
    public double getUtilizationRate() {
        return utilizationRate;
    }

    /**
     * Gets efficiency score (0.0 to 1.0).
     */
    public double getEfficiency() {
        return efficiency;
    }

    /**
     * Adds pool-specific statistics.
     */
    public void addPoolStats(ThreadPoolManager.PoolType poolType, PoolSpecificStats stats) {
        poolStats.put(poolType, stats);
    }

    /**
     * Gets pool-specific statistics.
     */
    public PoolSpecificStats getPoolStats(ThreadPoolManager.PoolType poolType) {
        return poolStats.get(poolType);
    }

    /**
     * Gets all pool-specific statistics.
     */
    public Map<ThreadPoolManager.PoolType, PoolSpecificStats> getAllPoolStats() {
        return new ConcurrentHashMap<>(poolStats);
    }

    /**
     * Pool-specific statistics.
     */
    public static class PoolSpecificStats {
        private final int resizeCount;
        private final long lastResizeTime;
        private final int consecutiveOptimizations;
        private final double currentEfficiency;
        private final double targetEfficiency;
        private final double averageLatency;
        private final double throughput;

        public PoolSpecificStats(int resizeCount, long lastResizeTime,
                int consecutiveOptimizations, double currentEfficiency,
                double targetEfficiency, double averageLatency,
                double throughput) {
            this.resizeCount = resizeCount;
            this.lastResizeTime = lastResizeTime;
            this.consecutiveOptimizations = consecutiveOptimizations;
            this.currentEfficiency = currentEfficiency;
            this.targetEfficiency = targetEfficiency;
            this.averageLatency = averageLatency;
            this.throughput = throughput;
        }

        public int getResizeCount() {
            return resizeCount;
        }

        public long getLastResizeTime() {
            return lastResizeTime;
        }

        public int getConsecutiveOptimizations() {
            return consecutiveOptimizations;
        }

        public double getCurrentEfficiency() {
            return currentEfficiency;
        }

        public double getTargetEfficiency() {
            return targetEfficiency;
        }

        public double getAverageLatency() {
            return averageLatency;
        }

        public double getThroughput() {
            return throughput;
        }

        @Override
        public String toString() {
            return String.format(
                    "PoolSpecificStats{resizes=%d, lastResize=%d, consecutive=%d, "
                            + "efficiency=%.2f, target=%.2f, latency=%.2f, throughput=%.2f}",
                    resizeCount, lastResizeTime, consecutiveOptimizations,
                    currentEfficiency, targetEfficiency, averageLatency, throughput);
        }
    }

    @Override
    public String toString() {
        return String.format(
                "ThreadPoolStats{name='%s', core=%d, max=%d, active=%d, "
                        + "tasks=%d, completed=%d, queue=%d, submitted=%d, "
                        + "completedSubmitted=%d, currentQueue=%d, avgExecTime=%.2f, "
                        + "throughput=%.2f, utilization=%.2f, efficiency=%.2f}",
                poolName, corePoolSize, maximumPoolSize, activeThreads,
                totalTasks, completedTasks, queueSize, submittedTasks,
                completedSubmittedTasks, currentQueueSize, averageExecutionTime,
                throughput, utilizationRate, efficiency);
    }
}