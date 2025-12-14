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
    * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    * GNU General Public License for more details.
    *
    * You should have received a copy of the GNU General Public License
    * along with this program.  If not, see <https://www.gnu.org/licenses/>.
    */

package com.justsyncit.performance.util;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Utility class for collecting detailed performance metrics during benchmark
 * execution.
 * Provides real-time monitoring of CPU, memory, thread usage, and other system
 * resources.
 */
public class BenchmarkMetricsCollector {

    private final MemoryMXBean memoryBean;
    private final ThreadMXBean threadBean;
    private final AtomicLong cpuStartNanoTime;
    private final AtomicLong cpuStartMilliTime;
    private final AtomicLong memoryStartTime;
    private final AtomicReference<MemoryStats> currentMemoryStats;
    private final AtomicReference<CpuStats> currentCpuStats;
    private final AtomicLong totalAllocations;

    /**
     * Creates a new benchmark metrics collector.
     */
    public BenchmarkMetricsCollector() {
        this.memoryBean = ManagementFactory.getMemoryMXBean();
        this.threadBean = ManagementFactory.getThreadMXBean();
        this.cpuStartNanoTime = new AtomicLong(0);
        this.cpuStartMilliTime = new AtomicLong(0);
        this.memoryStartTime = new AtomicLong(0);
        this.currentMemoryStats = new AtomicReference<>();
        this.currentCpuStats = new AtomicReference<>();
        this.totalAllocations = new AtomicLong(0);
    }

    /**
     * Starts CPU monitoring for the current operation.
     */
    public void startCpuMonitoring() {
        cpuStartNanoTime.set(System.nanoTime());
        cpuStartMilliTime.set(System.currentTimeMillis());

        // Record initial CPU metrics
        double initialCpuUsage = getCurrentCpuUsage();
        currentCpuStats.set(new CpuStats(initialCpuUsage, 0.0));
    }

    /**
     * Stops CPU monitoring and returns the average CPU usage.
     *
     * @return average CPU usage percentage during monitoring period
     */
    public double stopCpuMonitoring() {
        // long endTime = System.nanoTime();
        // long durationMs = (endTime - cpuStartNanoTime.get()) / 1_000_000;

        double finalCpuUsage = getCurrentCpuUsage();
        CpuStats stats = currentCpuStats.get();

        if (stats != null) {
            double averageCpuUsage = (stats.initialUsage + finalCpuUsage) / 2.0;
            return averageCpuUsage;
        }

        return finalCpuUsage;
    }

    /**
     * Starts memory monitoring for the current operation.
     */
    public void startMemoryMonitoring() {
        memoryStartTime.set(System.nanoTime());

        // Record initial memory metrics
        long initialHeapUsed = memoryBean.getHeapMemoryUsage().getUsed();
        long initialNonHeapUsed = memoryBean.getNonHeapMemoryUsage().getUsed();

        currentMemoryStats.set(new MemoryStats(
                initialHeapUsed, initialNonHeapUsed,
                initialHeapUsed, initialNonHeapUsed, 0));
    }

    /**
     * Stops memory monitoring and returns memory statistics.
     *
     * @return memory statistics collected during monitoring period
     */
    public MemoryStats stopMemoryMonitoring() {
        // long endTime = System.nanoTime();

        // Record final memory metrics
        long finalHeapUsed = memoryBean.getHeapMemoryUsage().getUsed();
        long finalNonHeapUsed = memoryBean.getNonHeapMemoryUsage().getUsed();

        MemoryStats stats = currentMemoryStats.get();
        if (stats != null) {
            // long peakHeapUsed = Math.max(stats.initialHeapUsed, finalHeapUsed);
            // long peakNonHeapUsed = Math.max(stats.initialNonHeapUsed, finalNonHeapUsed);

            return new MemoryStats(
                    stats.initialHeapUsed, stats.initialNonHeapUsed,
                    finalHeapUsed, finalNonHeapUsed,
                    totalAllocations.get());
        }

        return new MemoryStats(
                finalHeapUsed, finalNonHeapUsed,
                finalHeapUsed, finalNonHeapUsed,
                totalAllocations.get());
    }

    /**
     * Records a memory allocation event.
     *
     * @param bytes number of bytes allocated
     */
    public void recordAllocation(long bytes) {
        totalAllocations.addAndGet(bytes);
    }

    /**
     * Gets current CPU usage percentage.
     *
     * @return current CPU usage as percentage (0-100)
     */
    private double getCurrentCpuUsage() {
        try {
            com.sun.management.OperatingSystemMXBean osBean = (com.sun.management.OperatingSystemMXBean) ManagementFactory
                    .getOperatingSystemMXBean();
            double cpuUsage = osBean.getProcessCpuLoad() * 100.0;
            return Math.max(0.0, Math.min(100.0, cpuUsage));
        } catch (Exception e) {
            // Fallback to system load average
            double loadAverage = ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage();
            return loadAverage > 0 ? loadAverage : 0.0;
        }
    }

    /**
     * Gets current thread count.
     *
     * @return current number of threads
     */
    public int getCurrentThreadCount() {
        return threadBean.getThreadCount();
    }

    /**
     * Gets current heap memory usage in MB.
     *
     * @return heap memory usage in MB
     */
    public double getCurrentHeapMemoryMB() {
        return bytesToMB(memoryBean.getHeapMemoryUsage().getUsed());
    }

    /**
     * Gets current non-heap memory usage in MB.
     *
     * @return non-heap memory usage in MB
     */
    public double getCurrentNonHeapMemoryMB() {
        return bytesToMB(memoryBean.getNonHeapMemoryUsage().getUsed());
    }

    /**
     * Converts bytes to megabytes.
     *
     * @param bytes number of bytes
     * @return megabytes
     */
    private double bytesToMB(long bytes) {
        return bytes / (1024.0 * 1024.0);
    }

    /**
     * Memory statistics collected during monitoring.
     */
    public static class MemoryStats {
        public final long initialHeapUsed;
        public final long initialNonHeapUsed;
        public final long finalHeapUsed;
        public final long finalNonHeapUsed;
        public final long allocations;
        public final long peakMemoryMB;
        public final double averageMemoryMB;

        public MemoryStats(long initialHeapUsed, long initialNonHeapUsed,
                long finalHeapUsed, long finalNonHeapUsed, long allocations) {
            this.initialHeapUsed = initialHeapUsed;
            this.initialNonHeapUsed = initialNonHeapUsed;
            this.finalHeapUsed = finalHeapUsed;
            this.finalNonHeapUsed = finalNonHeapUsed;
            this.allocations = allocations;

            long peakHeapUsed = Math.max(initialHeapUsed, finalHeapUsed);
            long peakNonHeapUsed = Math.max(initialNonHeapUsed, finalNonHeapUsed);
            this.peakMemoryMB = (peakHeapUsed + peakNonHeapUsed) / (1024 * 1024);

            long avgHeapUsed = (initialHeapUsed + finalHeapUsed) / 2;
            long avgNonHeapUsed = (initialNonHeapUsed + finalNonHeapUsed) / 2;
            this.averageMemoryMB = (avgHeapUsed + avgNonHeapUsed) / (1024.0 * 1024.0);
        }

        public long getPeakMemoryMB() {
            return peakMemoryMB;
        }

        public double getAverageMemoryMB() {
            return averageMemoryMB;
        }

        public long getAllocations() {
            return allocations;
        }
    }

    /**
     * CPU statistics collected during monitoring.
     */
    public static class CpuStats {
        public final double initialUsage;
        public final double finalUsage;
        public final double averageUsage;
        public final long durationMs;

        public CpuStats(double initialUsage, double finalUsage) {
            this.initialUsage = initialUsage;
            this.finalUsage = finalUsage;
            this.averageUsage = (initialUsage + finalUsage) / 2.0;
            this.durationMs = 0; // Will be set by the collector
        }

        public double getAverageUsage() {
            return averageUsage;
        }

        public long getDurationMs() {
            return durationMs;
        }
    }

    /**
     * Thread utilization statistics.
     */
    public static class ThreadStats {
        public final int threadCount;
        public final int peakThreadCount;
        public final long totalStartedThreads;
        public final long totalTerminatedThreads;

        public ThreadStats(int threadCount, int peakThreadCount,
                long totalStartedThreads, long totalTerminatedThreads) {
            this.threadCount = threadCount;
            this.peakThreadCount = peakThreadCount;
            this.totalStartedThreads = totalStartedThreads;
            this.totalTerminatedThreads = totalTerminatedThreads;
        }

        public int getThreadCount() {
            return threadCount;
        }

        public int getPeakThreadCount() {
            return peakThreadCount;
        }
    }

    /**
     * Garbage collection statistics.
     */
    public static class GcStats {
        public final long totalCollections;
        public final long totalCollectionTime;
        public final double averageCollectionTime;
        public final double gcOverheadPercent;

        public GcStats(long totalCollections, long totalCollectionTime, long totalRuntime) {
            this.totalCollections = totalCollections;
            this.totalCollectionTime = totalCollectionTime;
            this.averageCollectionTime = totalCollections > 0 ? (double) totalCollectionTime / totalCollections : 0.0;
            this.gcOverheadPercent = totalRuntime > 0 ? (double) totalCollectionTime / totalRuntime * 100.0 : 0.0;
        }

        public long getTotalCollections() {
            return totalCollections;
        }

        public long getTotalCollectionTime() {
            return totalCollectionTime;
        }

        public double getAverageCollectionTime() {
            return averageCollectionTime;
        }

        public double getGcOverheadPercent() {
            return gcOverheadPercent;
        }
    }
}