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

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Memory pressure detector for monitoring JVM memory usage and triggering
 * responses.
 * Implements adaptive memory management and garbage collection optimization.
 */
public final class MemoryPressureDetector implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(MemoryPressureDetector.class);

    private final OptimizedAsyncByteBufferPool.PoolConfiguration config;
    private final PerformanceMonitor performanceMonitor;
    private final MemoryMXBean memoryBean;

    // Memory pressure tracking
    private final AtomicReference<MemoryPressure> currentPressure = new AtomicReference<>(MemoryPressure.LOW);
    private final AtomicLong lastGcTime = new AtomicLong(0);
    private final AtomicLong gcCount = new AtomicLong(0);
    private final AtomicLong lastPressureTime = new AtomicLong(0);

    // Pressure thresholds
    private final double warningThreshold;
    private final double criticalThreshold;
    private final double emergencyThreshold;

    // Background monitoring
    private final ScheduledExecutorService scheduler;
    private volatile boolean running = true;

    /**
     * Memory pressure levels.
     */
    public enum MemoryPressure {
        LOW(0.0),
        MEDIUM(0.5),
        HIGH(0.8),
        CRITICAL(0.9),
        EMERGENCY(1.0);

        private final double level;

        MemoryPressure(double level) {
            this.level = level;
        }

        public double getLevel() {
            return level;
        }
    }

    /**
     * Creates a new MemoryPressureDetector.
     */
    public MemoryPressureDetector(OptimizedAsyncByteBufferPool.PoolConfiguration config,
            PerformanceMonitor performanceMonitor) {
        this.config = config;
        this.performanceMonitor = performanceMonitor;
        this.memoryBean = ManagementFactory.getMemoryMXBean();

        // Set thresholds based on configuration
        this.warningThreshold = config.getMemoryPressureThreshold() * 0.7;
        this.criticalThreshold = config.getMemoryPressureThreshold() * 0.85;
        this.emergencyThreshold = config.getMemoryPressureThreshold();

        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "MemoryPressureDetector");
            t.setDaemon(true);
            return t;
        });

        // Start monitoring
        scheduler.scheduleAtFixedRate(this, 1, 1, TimeUnit.SECONDS);

        logger.debug(
                "MemoryPressureDetector initialized with thresholds: warning={:.2f}, critical={:.2f}, emergency={:.2f}",
                warningThreshold, criticalThreshold, emergencyThreshold);
    }

    @Override
    public void run() {
        if (!running) {
            return;
        }

        try {
            detectMemoryPressure();
        } catch (Exception e) {
            logger.error("Error in memory pressure detection", e);
        }
    }

    /**
     * Detects current memory pressure and triggers responses.
     */
    private void detectMemoryPressure() {
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        MemoryUsage nonHeapUsage = memoryBean.getNonHeapMemoryUsage();

        long heapUsed = heapUsage.getUsed();
        long heapMax = heapUsage.getMax();
        long nonHeapUsed = nonHeapUsage.getUsed();
        long nonHeapMax = nonHeapUsage.getMax();

        // Calculate memory usage ratios
        double heapRatio = heapMax > 0 ? (double) heapUsed / heapMax : 0.0;
        double nonHeapRatio = nonHeapMax > 0 ? (double) nonHeapUsed / nonHeapMax : 0.0;
        double combinedRatio = Math.max(heapRatio, nonHeapRatio);

        // Determine pressure level
        MemoryPressure newPressure = calculatePressureLevel(combinedRatio);
        MemoryPressure oldPressure = currentPressure.getAndSet(newPressure);

        // Log pressure changes
        if (newPressure != oldPressure) {
            logger.info("Memory pressure changed from {} to {} (heap={:.2f}%, non-heap={:.2f}%)",
                    oldPressure, newPressure, heapRatio * 100, nonHeapRatio * 100);
            lastPressureTime.set(System.currentTimeMillis());
        }

        // Trigger appropriate response
        handleMemoryPressure(newPressure, combinedRatio, heapUsed, heapMax);

        // Check for GC activity
        monitorGarbageCollection();
    }

    /**
     * Calculates memory pressure level based on usage ratio.
     */
    private MemoryPressure calculatePressureLevel(double ratio) {
        if (ratio >= emergencyThreshold) {
            return MemoryPressure.EMERGENCY;
        } else if (ratio >= criticalThreshold) {
            return MemoryPressure.CRITICAL;
        } else if (ratio >= warningThreshold) {
            return MemoryPressure.HIGH;
        } else if (ratio >= warningThreshold * 0.7) {
            return MemoryPressure.MEDIUM;
        } else {
            return MemoryPressure.LOW;
        }
    }

    /**
     * Handles memory pressure with appropriate responses.
     */
    private void handleMemoryPressure(MemoryPressure pressure, double ratio, long used, long max) {
        switch (pressure) {
            case EMERGENCY:
                handleEmergencyPressure(used, max);
                break;
            case CRITICAL:
                handleCriticalPressure(used, max);
                break;
            case HIGH:
                handleHighPressure(used, max);
                break;
            case MEDIUM:
                handleMediumPressure(used, max);
                break;
            case LOW:
                handleLowPressure(used, max);
                break;
            default:
                logger.warn("Unknown memory pressure level: {}", pressure);
                break;
        }
    }

    /**
     * Handles emergency memory pressure.
     */
    private void handleEmergencyPressure(long used, long max) {
        logger.warn("EMERGENCY memory pressure: {}MB/{}MB ({:.2f}%)",
                used / 1024 / 1024, max / 1024 / 1024, (double) used / max * 100);

        // Force garbage collection
        System.gc();

        // Request immediate buffer cleanup
        requestBufferCleanup(true);

        // Log emergency state
        performanceMonitor.recordMemoryPressure(MemoryPressure.EMERGENCY);
    }

    /**
     * Handles critical memory pressure.
     */
    private void handleCriticalPressure(long used, long max) {
        logger.warn("CRITICAL memory pressure: {}MB/{}MB ({:.2f}%)",
                used / 1024 / 1024, max / 1024 / 1024, (double) used / max * 100);

        // Suggest garbage collection
        System.gc();

        // Request aggressive buffer cleanup
        requestBufferCleanup(false);

        // Log critical state
        performanceMonitor.recordMemoryPressure(MemoryPressure.CRITICAL);
    }

    /**
     * Handles high memory pressure.
     */
    private void handleHighPressure(long used, long max) {
        logger.info("HIGH memory pressure: {}MB/{}MB ({:.2f}%)",
                used / 1024 / 1024, max / 1024 / 1024, (double) used / max * 100);

        // Request moderate buffer cleanup
        requestBufferCleanup(false);

        // Log high state
        performanceMonitor.recordMemoryPressure(MemoryPressure.HIGH);
    }

    /**
     * Handles medium memory pressure.
     */
    private void handleMediumPressure(long used, long max) {
        logger.debug("MEDIUM memory pressure: {}MB/{}MB ({:.2f}%)",
                used / 1024 / 1024, max / 1024 / 1024, (double) used / max * 100);

        // Log medium state
        performanceMonitor.recordMemoryPressure(MemoryPressure.MEDIUM);
    }

    /**
     * Handles low memory pressure.
     */
    private void handleLowPressure(long used, long max) {
        logger.debug("LOW memory pressure: {}MB/{}MB ({:.2f}%)",
                used / 1024 / 1024, max / 1024 / 1024, (double) used / max * 100);

        // Log low state
        performanceMonitor.recordMemoryPressure(MemoryPressure.LOW);
    }

    /**
     * Requests buffer cleanup from pool.
     */
    private void requestBufferCleanup(boolean aggressive) {
        // This would be implemented by the main buffer pool
        // For now, just log the request
        logger.debug("Requesting {} buffer cleanup", aggressive ? "aggressive" : "moderate");
    }

    /**
     * Monitors garbage collection activity.
     */
    private void monitorGarbageCollection() {
        // Simple GC monitoring - in a real implementation, you'd use
        // GarbageCollectorMXBean for more detailed monitoring
        long currentTime = System.currentTimeMillis();
        long timeSinceLastGc = currentTime - lastGcTime.get();

        // If we haven't seen GC activity recently, trigger one
        if (timeSinceLastGc > 30000) { // 30 seconds
            System.gc();
            lastGcTime.set(currentTime);
            gcCount.incrementAndGet();
        }
    }

    /**
     * Handles out of memory errors.
     */
    public void handleOutOfMemory(int requestedSize) {
        logger.error("OutOfMemory detected for buffer size {} bytes", requestedSize);

        // Set emergency pressure
        currentPressure.set(MemoryPressure.EMERGENCY);
        lastPressureTime.set(System.currentTimeMillis());

        // Force garbage collection
        System.gc();

        // Request immediate cleanup
        requestBufferCleanup(true);

        // Record emergency state
        performanceMonitor.recordMemoryPressure(MemoryPressure.EMERGENCY);
    }

    /**
     * Gets current memory pressure.
     */
    public MemoryPressure getCurrentPressure() {
        return currentPressure.get();
    }

    /**
     * Gets memory usage statistics.
     */
    public MemoryStats getMemoryStats() {
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        MemoryUsage nonHeapUsage = memoryBean.getNonHeapMemoryUsage();

        return new MemoryStats(
                heapUsage.getUsed(), heapUsage.getMax(), heapUsage.getCommitted(),
                nonHeapUsage.getUsed(), nonHeapUsage.getMax(), nonHeapUsage.getCommitted(),
                currentPressure.get(), lastPressureTime.get(), gcCount.get());
    }

    /**
     * Memory statistics snapshot.
     */
    public static class MemoryStats {
        public final long heapUsed;
        public final long heapMax;
        public final long heapCommitted;
        public final long nonHeapUsed;
        public final long nonHeapMax;
        public final long nonHeapCommitted;
        public final MemoryPressure pressure;
        public final long lastPressureTime;
        public final long gcCount;

        MemoryStats(long heapUsed, long heapMax, long heapCommitted,
                long nonHeapUsed, long nonHeapMax, long nonHeapCommitted,
                MemoryPressure pressure, long lastPressureTime, long gcCount) {
            this.heapUsed = heapUsed;
            this.heapMax = heapMax;
            this.heapCommitted = heapCommitted;
            this.nonHeapUsed = nonHeapUsed;
            this.nonHeapMax = nonHeapMax;
            this.nonHeapCommitted = nonHeapCommitted;
            this.pressure = pressure;
            this.lastPressureTime = lastPressureTime;
            this.gcCount = gcCount;
        }

        @Override
        public String toString() {
            return String.format(
                    "MemoryStats{heap=%dMB/%dMB(%.2f%%), non-heap=%dMB/%dMB, pressure=%s, gc=%d}",
                    heapUsed / 1024 / 1024, heapMax / 1024 / 1024,
                    heapMax > 0 ? (double) heapUsed / heapMax * 100 : 0.0,
                    nonHeapUsed / 1024 / 1024, nonHeapMax / 1024 / 1024,
                    pressure, gcCount);
        }
    }

    /**
     * Shuts down the memory pressure detector.
     */
    public void shutdown() {
        running = false;
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        logger.info("MemoryPressureDetector shutdown completed");
    }
}