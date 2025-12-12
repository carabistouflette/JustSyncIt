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

package com.justsyncit.scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Optimizes performance for async directory scanning operations.
 * Provides adaptive sizing, memory management, and system resource
 * optimization.
 */
public class PerformanceOptimizer {

    private static final Logger logger = LoggerFactory.getLogger(PerformanceOptimizer.class);

    /** Memory management bean. */
    private final MemoryMXBean memoryBean;

    /** Operating system bean. */
    private final OperatingSystemMXBean osBean;

    /** Current optimal thread count. */
    private final AtomicReference<Integer> optimalThreadCount;

    /** Current optimal buffer size. */
    private final AtomicReference<Integer> optimalBufferSize;

    /** Last optimization timestamp. */
    private final AtomicLong lastOptimizationTime;

    /** Number of optimizations performed. */
    private final AtomicLong optimizationCount;

    /**
     * Creates a new PerformanceOptimizer.
     */
    public PerformanceOptimizer() {
        this.memoryBean = ManagementFactory.getMemoryMXBean();
        this.osBean = ManagementFactory.getOperatingSystemMXBean();
        this.optimalThreadCount = new AtomicReference<>(calculateInitialThreadCount());
        this.optimalBufferSize = new AtomicReference<>(calculateInitialBufferSize());
        this.lastOptimizationTime = new AtomicLong(System.currentTimeMillis());
        this.optimizationCount = new AtomicLong(0);
    }

    /**
     * Optimizes performance parameters based on current system state.
     */
    public void optimize() {
        long startTime = System.currentTimeMillis();

        try {
            // Calculate memory pressure
            double memoryPressure = calculateMemoryPressure();

            // Calculate CPU load
            double cpuLoad = 0.0;
            try {
                // Try to get process CPU load using reflection for compatibility
                java.lang.reflect.Method getProcessCpuLoad = osBean.getClass().getMethod("getProcessCpuLoad");
                Object value = getProcessCpuLoad.invoke(osBean);
                if (value instanceof Double) {
                    cpuLoad = (Double) value;
                    if (cpuLoad < 0) {
                        cpuLoad = 0.0; // Handle unavailable CPU load
                    }
                }
            } catch (Exception e) {
                // Fallback to system load average if available
                double loadAverage = osBean.getSystemLoadAverage();
                if (loadAverage >= 0) {
                    cpuLoad = Math.min(1.0, loadAverage / Runtime.getRuntime().availableProcessors());
                }
            }

            // Optimize thread count based on system load
            int newThreadCount = calculateOptimalThreadCount(memoryPressure, cpuLoad);
            optimalThreadCount.set(newThreadCount);

            // Optimize buffer size based on available memory
            int newBufferSize = calculateOptimalBufferSize(memoryPressure);
            optimalBufferSize.set(newBufferSize);

            // Update optimization metadata
            lastOptimizationTime.set(System.currentTimeMillis());
            optimizationCount.incrementAndGet();

            logger.debug(
                    "Performance optimization completed: threads={}, bufferSize={}, memoryPressure={:.2f}, cpuLoad={:.2f}",
                    newThreadCount, newBufferSize, memoryPressure, cpuLoad);

        } catch (Exception e) {
            logger.warn("Performance optimization failed", e);
        }
    }

    /**
     * Gets the current optimal thread count.
     *
     * @return optimal thread count
     */
    public int getOptimalThreadCount() {
        return optimalThreadCount.get();
    }

    /**
     * Gets the current optimal buffer size.
     *
     * @return optimal buffer size in bytes
     */
    public int getOptimalBufferSize() {
        return optimalBufferSize.get();
    }

    /**
     * Gets the last optimization timestamp.
     *
     * @return last optimization time in milliseconds
     */
    public long getLastOptimizationTime() {
        return lastOptimizationTime.get();
    }

    /**
     * Gets the total number of optimizations performed.
     *
     * @return optimization count
     */
    public long getOptimizationCount() {
        return optimizationCount.get();
    }

    /**
     * Calculates memory pressure (0.0 to 1.0).
     *
     * @return memory pressure level
     */
    private double calculateMemoryPressure() {
        try {
            long usedMemory = memoryBean.getHeapMemoryUsage().getUsed();
            long maxMemory = memoryBean.getHeapMemoryUsage().getMax();

            if (maxMemory <= 0) {
                return 0.5; // Default pressure if max memory is unavailable
            }

            return (double) usedMemory / maxMemory;
        } catch (Exception e) {
            logger.debug("Failed to calculate memory pressure", e);
            return 0.5; // Default pressure on error
        }
    }

    /**
     * Calculates the initial thread count based on available processors.
     *
     * @return initial thread count
     */
    private int calculateInitialThreadCount() {
        int processors = Runtime.getRuntime().availableProcessors();
        return Math.max(2, processors / 2); // Use half of available processors, minimum 2
    }

    /**
     * Calculates the optimal thread count based on system load.
     *
     * @param memoryPressure current memory pressure
     * @param cpuLoad        current CPU load
     * @return optimal thread count
     */
    private int calculateOptimalThreadCount(double memoryPressure, double cpuLoad) {
        int baseCount = Runtime.getRuntime().availableProcessors();

        // Reduce thread count under high memory pressure
        if (memoryPressure > 0.8) {
            baseCount = Math.max(1, baseCount / 4);
        } else if (memoryPressure > 0.6) {
            baseCount = Math.max(2, baseCount / 2);
        }

        // Reduce thread count under high CPU load
        if (cpuLoad > 0.8) {
            baseCount = Math.max(1, baseCount / 3);
        } else if (cpuLoad > 0.6) {
            baseCount = Math.max(2, baseCount / 2);
        }

        // Ensure minimum thread count
        return Math.max(1, baseCount);
    }

    /**
     * Calculates the initial buffer size.
     *
     * @return initial buffer size in bytes
     */
    private int calculateInitialBufferSize() {
        long maxMemory = memoryBean.getHeapMemoryUsage().getMax();
        if (maxMemory <= 0) {
            return 64 * 1024; // 64KB default
        }

        // Use 0.1% of max memory, capped at 1MB
        int bufferSize = (int) Math.min(maxMemory / 1000, 1024 * 1024);
        return Math.max(8 * 1024, bufferSize); // Minimum 8KB
    }

    /**
     * Calculates the optimal buffer size based on memory pressure.
     *
     * @param memoryPressure current memory pressure
     * @return optimal buffer size in bytes
     */
    private int calculateOptimalBufferSize(double memoryPressure) {
        int baseSize = calculateInitialBufferSize();

        // Reduce buffer size under memory pressure
        if (memoryPressure > 0.8) {
            baseSize = baseSize / 4;
        } else if (memoryPressure > 0.6) {
            baseSize = baseSize / 2;
        }

        // Ensure minimum buffer size
        return Math.max(4 * 1024, baseSize); // Minimum 4KB
    }

    /**
     * Gets a summary of the current optimization state.
     *
     * @return summary string
     */
    public String getSummary() {
        return String.format(
                "PerformanceOptimizer{threads=%d, bufferSize=%dKB, optimizations=%d, lastOpt=%dms ago}",
                getOptimalThreadCount(), getOptimalBufferSize() / 1024, getOptimizationCount(),
                System.currentTimeMillis() - getLastOptimizationTime());
    }

    @Override
    public String toString() {
        return getSummary();
    }
}