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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Monitor for thread pool performance and health.
 * Tracks metrics for adaptive sizing and optimization.
 */
public class ThreadPoolMonitor {

    private static final Logger logger = LoggerFactory.getLogger(ThreadPoolMonitor.class);

    private final ThreadPoolConfiguration config;
    private final SystemResourceInfo systemInfo;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean running = new AtomicBoolean(true);

    // System-wide metrics
    private final AtomicLong totalTasksSubmitted = new AtomicLong(0);
    private final AtomicLong totalTasksCompleted = new AtomicLong(0);
    private final AtomicLong totalExecutionTime = new AtomicLong(0);
    private final AtomicLong totalCpuTime = new AtomicLong(0);
    private final AtomicLong totalMemoryAllocated = new AtomicLong(0);

    // Pool-specific metrics
    private final Map<ThreadPoolManager.PoolType, PoolMetrics> poolMetrics = new ConcurrentHashMap<>();

    /**
     * Metrics for a specific thread pool.
     */
    private static class PoolMetrics {
        private final AtomicLong tasksSubmitted = new AtomicLong(0);
        private final AtomicLong tasksCompleted = new AtomicLong(0);
        private final AtomicLong executionTime = new AtomicLong(0);
        private final AtomicLong maxExecutionTime = new AtomicLong(0);
        private final AtomicLong minExecutionTime = new AtomicLong(Long.MAX_VALUE);
        private final AtomicLong failures = new AtomicLong(0);
        private final AtomicLong rejections = new AtomicLong(0);
        private final AtomicLong lastActivity = new AtomicLong(System.currentTimeMillis());

        void recordTaskSubmission() {
            tasksSubmitted.incrementAndGet();
            lastActivity.set(System.currentTimeMillis());
        }

        void recordTaskCompletion(long executionTimeNs) {
            tasksCompleted.incrementAndGet();
            this.executionTime.addAndGet(executionTimeNs);
            maxExecutionTime.updateAndGet(max -> Math.max(max, executionTimeNs));
            minExecutionTime.updateAndGet(min -> Math.min(min, executionTimeNs));
            lastActivity.set(System.currentTimeMillis());
        }

        void recordFailure() {
            failures.incrementAndGet();
            lastActivity.set(System.currentTimeMillis());
        }

        void recordRejection() {
            rejections.incrementAndGet();
            lastActivity.set(System.currentTimeMillis());
        }

        SystemStatsSnapshot getSnapshot() {
            long submitted = tasksSubmitted.get();
            long completed = tasksCompleted.get();
            long execTime = executionTime.get();

            return new SystemStatsSnapshot(
                submitted, completed, failures.get(), rejections.get(),
                execTime, maxExecutionTime.get(), minExecutionTime.get(),
                completed > 0 ? (double) execTime / completed : 0.0,
                lastActivity.get()
            );
        }
    }

    /**
     * System-wide statistics snapshot.
     */
    public static class SystemStatsSnapshot {
        public final long totalSubmitted;
        public final long totalCompleted;
        public final long totalFailures;
        public final long totalRejections;
        public final long totalExecutionTime;
        public final long maxExecutionTime;
        public final long minExecutionTime;
        public final double averageExecutionTime;
        public final long lastActivity;

        SystemStatsSnapshot(long totalSubmitted, long totalCompleted, long totalFailures, long totalRejections,
                           long totalExecutionTime, long maxExecutionTime, long minExecutionTime,
                           double averageExecutionTime, long lastActivity) {
            this.totalSubmitted = totalSubmitted;
            this.totalCompleted = totalCompleted;
            this.totalFailures = totalFailures;
            this.totalRejections = totalRejections;
            this.totalExecutionTime = totalExecutionTime;
            this.maxExecutionTime = maxExecutionTime;
            this.minExecutionTime = minExecutionTime;
            this.averageExecutionTime = averageExecutionTime;
            this.lastActivity = lastActivity;
        }

        @Override
        public String toString() {
            return String.format(
                "SystemStats{submitted=%d, completed=%d, failures=%d, rejections=%d, " +
                "avgExecTime=%.2fms, maxExecTime=%dms, minExecTime=%dms, lastActivity=%d}",
                totalSubmitted, totalCompleted, totalFailures, totalRejections,
                averageExecutionTime / 1000000.0, maxExecutionTime / 1000000,
                minExecutionTime / 1000000, lastActivity
            );
        }
    }

    /**
     * Creates a new ThreadPoolMonitor.
     */
    public ThreadPoolMonitor(ThreadPoolConfiguration config, SystemResourceInfo systemInfo) {
        this.config = config;
        this.systemInfo = systemInfo;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ThreadPoolMonitor");
            t.setDaemon(true);
            return t;
        });

        logger.info("ThreadPoolMonitor initialized");
    }

    /**
     * Starts the monitoring service.
     */
    public void start() {
        if (!running.get()) {
            return;
        }

        scheduler.scheduleAtFixedRate(this::performMonitoring, 10, 10, TimeUnit.SECONDS);
        logger.info("ThreadPoolMonitor started");
    }

    /**
     * Performs monitoring and analysis.
     */
    private void performMonitoring() {
        try {
            // Log system-wide metrics
            logSystemMetrics();

            // Analyze pool-specific metrics
            analyzePoolMetrics();

            // Check for performance issues
            checkPerformanceIssues();

        } catch (Exception e) {
            logger.error("Error in monitoring", e);
        }
    }

    /**
     * Logs system-wide metrics.
     */
    private void logSystemMetrics() {
        long submitted = totalTasksSubmitted.get();
        long completed = totalTasksCompleted.get();
        long execTime = totalExecutionTime.get();

        if (logger.isDebugEnabled()) {
            logger.debug("System metrics: submitted={}, completed={}, avgExecTime={:.2f}ms",
                submitted, completed, completed > 0 ? (double) execTime / completed / 1000000.0 : 0.0);
        }
    }

    /**
     * Analyzes pool-specific metrics.
     */
    private void analyzePoolMetrics() {
        for (Map.Entry<ThreadPoolManager.PoolType, PoolMetrics> entry : poolMetrics.entrySet()) {
            ThreadPoolManager.PoolType type = entry.getKey();
            PoolMetrics metrics = entry.getValue();
            SystemStatsSnapshot snapshot = metrics.getSnapshot();

            if (logger.isTraceEnabled()) {
                logger.trace("Pool {} metrics: {}", type.getName(), snapshot);
            }
        }
    }

    /**
     * Checks for performance issues.
     */
    private void checkPerformanceIssues() {
        // Check for high failure rates
        for (Map.Entry<ThreadPoolManager.PoolType, PoolMetrics> entry : poolMetrics.entrySet()) {
            PoolMetrics metrics = entry.getValue();
            SystemStatsSnapshot snapshot = metrics.getSnapshot();

            double failureRate = snapshot.totalSubmitted > 0
                ? (double) snapshot.totalFailures / snapshot.totalSubmitted
                : 0.0;

            if (failureRate > 0.1) { // >10% failure rate
                logger.warn("High failure rate detected for pool {}: {:.2f}%",
                    entry.getKey().getName(), failureRate * 100);
            }
        }
    }

    /**
     * Records task submission for a specific pool.
     */
    public void recordTaskSubmission(ThreadPoolManager.PoolType poolType) {
        totalTasksSubmitted.incrementAndGet();
        poolMetrics.computeIfAbsent(poolType, k -> new PoolMetrics()).recordTaskSubmission();
    }

    /**
     * Records task completion for a specific pool.
     */
    public void recordTaskCompletion(ThreadPoolManager.PoolType poolType, long executionTimeNs) {
        totalTasksCompleted.incrementAndGet();
        totalExecutionTime.addAndGet(executionTimeNs);
        poolMetrics.computeIfAbsent(poolType, k -> new PoolMetrics()).recordTaskCompletion(executionTimeNs);
    }

    /**
     * Records task failure for a specific pool.
     */
    public void recordTaskFailure(ThreadPoolManager.PoolType poolType) {
        poolMetrics.computeIfAbsent(poolType, k -> new PoolMetrics()).recordFailure();
    }

    /**
     * Records task rejection for a specific pool.
     */
    public void recordTaskRejection(ThreadPoolManager.PoolType poolType) {
        poolMetrics.computeIfAbsent(poolType, k -> new PoolMetrics()).recordRejection();
    }

    /**
     * Gets system-wide statistics.
     */
    public SystemStatsSnapshot getSystemStats() {
        long submitted = totalTasksSubmitted.get();
        long completed = totalTasksCompleted.get();
        long execTime = totalExecutionTime.get();

        return new SystemStatsSnapshot(
            submitted, completed, 0, 0, execTime, 0, Long.MAX_VALUE,
            completed > 0 ? (double) execTime / completed : 0.0,
            System.currentTimeMillis()
        );
    }

    /**
     * Gets statistics for a specific pool.
     */
    public SystemStatsSnapshot getPoolStats(ThreadPoolManager.PoolType poolType) {
        PoolMetrics metrics = poolMetrics.get(poolType);
        return metrics != null ? metrics.getSnapshot() : null;
    }

    /**
     * Shuts down the monitor.
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

        logger.info("ThreadPoolMonitor shutdown completed");
    }
}