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
    public ThreadPoolStats(String poolName, int corePoolSize, int maximumPoolSize,
            int activeThreads, int totalTasks, int completedTasks,
            int queueSize, long submittedTasks, long completedSubmittedTasks,
            int currentQueueSize) {
        this.poolName = poolName;
        this.corePoolSize = corePoolSize;
        this.maximumPoolSize = maximumPoolSize;
        this.activeThreads = activeThreads;
        this.totalTasks = totalTasks;
        this.completedTasks = completedTasks;
        this.queueSize = queueSize;
        this.submittedTasks = submittedTasks;
        this.completedSubmittedTasks = completedSubmittedTasks;
        this.currentQueueSize = currentQueueSize;

        // Calculate derived metrics
        this.averageExecutionTime = calculateAverageExecutionTime();
        this.throughput = calculateThroughput();
        this.utilizationRate = calculateUtilizationRate();
        this.efficiency = calculateEfficiency();
    }

    /**
     * Creates ThreadPoolStats with performance metrics.
     */
    public ThreadPoolStats(String poolName, int corePoolSize, int maximumPoolSize,
            int activeThreads, int totalTasks, int completedTasks,
            int queueSize, long submittedTasks, long completedSubmittedTasks,
            int currentQueueSize, double averageExecutionTime,
            double throughput, double utilizationRate, double efficiency) {
        this.poolName = poolName;
        this.corePoolSize = corePoolSize;
        this.maximumPoolSize = maximumPoolSize;
        this.activeThreads = activeThreads;
        this.totalTasks = totalTasks;
        this.completedTasks = completedTasks;
        this.queueSize = queueSize;
        this.submittedTasks = submittedTasks;
        this.completedSubmittedTasks = completedSubmittedTasks;
        this.currentQueueSize = currentQueueSize;
        this.averageExecutionTime = averageExecutionTime;
        this.throughput = throughput;
        this.utilizationRate = utilizationRate;
        this.efficiency = efficiency;
    }

    /**
     * Calculates average execution time.
     */
    private double calculateAverageExecutionTime() {
        return completedSubmittedTasks > 0 ? 1000000.0 / completedSubmittedTasks : 0.0;
    }

    /**
     * Calculates throughput (tasks per second).
     */
    private double calculateThroughput() {
        return averageExecutionTime > 0 ? 1000000000.0 / averageExecutionTime : 0.0;
    }

    /**
     * Calculates utilization rate.
     */
    private double calculateUtilizationRate() {
        return maximumPoolSize > 0 ? (double) activeThreads / maximumPoolSize : 0.0;
    }

    /**
     * Calculates efficiency.
     */
    private double calculateEfficiency() {
        return throughput > 0 ? utilizationRate * (1.0 - (averageExecutionTime / 1000000000.0)) : 0.0;
    }

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