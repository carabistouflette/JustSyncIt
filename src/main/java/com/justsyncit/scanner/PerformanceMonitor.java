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

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Performance monitoring system for buffer pool operations.
 * Tracks metrics for adaptive sizing and performance optimization.
 */
public final class PerformanceMonitor implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(PerformanceMonitor.class);

    // Global counters
    private final AtomicLong totalAcquisitionRequests = new AtomicLong(0);
    private final AtomicLong totalSuccessfulAcquisitions = new AtomicLong(0);
    private final AtomicLong totalFailedAcquisitions = new AtomicLong(0);
    private final AtomicLong totalReleases = new AtomicLong(0);
    private final AtomicLong totalSuccessfulReleases = new AtomicLong(0);
    private final AtomicLong totalFailedReleases = new AtomicLong(0);
    private final AtomicLong totalAllocationFailures = new AtomicLong(0);

    // Size-specific metrics
    private final Map<Integer, SizeMetrics> sizeMetrics = new ConcurrentHashMap<>();

    // Timing metrics
    private final AtomicLong totalAcquisitionTime = new AtomicLong(0);
    private final AtomicLong acquisitionCount = new AtomicLong(0);

    // Memory usage tracking
    private final AtomicLong totalMemoryAllocated = new AtomicLong(0);
    private final AtomicLong totalMemoryReleased = new AtomicLong(0);
    private final AtomicInteger currentMemoryUsage = new AtomicInteger(0);

    // Performance window for recent metrics
    private final AtomicReference<PerformanceWindow> currentWindow = new AtomicReference<>(new PerformanceWindow());

    // Background monitoring
    private final ScheduledExecutorService scheduler;
    private volatile boolean running = true;

    /**
     * Metrics for specific buffer sizes.
     */
    private static class SizeMetrics {
        private final AtomicLong acquisitions = new AtomicLong(0);
        private final AtomicLong releases = new AtomicLong(0);
        private final AtomicLong failures = new AtomicLong(0);
        private final AtomicLong totalWaitTime = new AtomicLong(0);
        private final AtomicLong maxWaitTime = new AtomicLong(0);
        private final AtomicInteger currentInUse = new AtomicInteger(0);
        private final AtomicInteger peakUsage = new AtomicInteger(0);

        void recordAcquisition(long waitTime) {
            acquisitions.incrementAndGet();
            totalWaitTime.addAndGet(waitTime);
            maxWaitTime.updateAndGet(max -> Math.max(max, waitTime));
            int current = currentInUse.incrementAndGet();
            peakUsage.updateAndGet(max -> Math.max(max, current));
        }

        void recordRelease() {
            releases.incrementAndGet();
            currentInUse.decrementAndGet();
        }

        void recordFailure() {
            failures.incrementAndGet();
        }

        SizeMetricsSnapshot getSnapshot() {
            return new SizeMetricsSnapshot(
                    acquisitions.get(), releases.get(), failures.get(),
                    totalWaitTime.get(), maxWaitTime.get(),
                    currentInUse.get(), peakUsage.get());
        }
    }

    /**
     * Snapshot of size-specific metrics.
     */
    public static class SizeMetricsSnapshot {
        public final long acquisitions;
        public final long releases;
        public final long failures;
        public final long totalWaitTime;
        public final long maxWaitTime;
        public final int currentInUse;
        public final int peakUsage;

        SizeMetricsSnapshot(long acquisitions, long releases, long failures,
                long totalWaitTime, long maxWaitTime,
                int currentInUse, int peakUsage) {
            this.acquisitions = acquisitions;
            this.releases = releases;
            this.failures = failures;
            this.totalWaitTime = totalWaitTime;
            this.maxWaitTime = maxWaitTime;
            this.currentInUse = currentInUse;
            this.peakUsage = peakUsage;
        }
    }

    /**
     * Rolling performance window for recent metrics.
     */
    private static class PerformanceWindow {
        private final long startTime = System.currentTimeMillis();
        private final AtomicLong windowAcquisitions = new AtomicLong(0);
        private final AtomicLong windowReleases = new AtomicLong(0);
        private final AtomicLong windowFailures = new AtomicLong(0);
        private final AtomicLong windowWaitTime = new AtomicLong(0);

        void recordAcquisition(long waitTime) {
            windowAcquisitions.incrementAndGet();
            windowWaitTime.addAndGet(waitTime);
        }

        void recordRelease() {
            windowReleases.incrementAndGet();
        }

        void recordFailure() {
            windowFailures.incrementAndGet();
        }

        WindowSnapshot getSnapshot() {
            long duration = System.currentTimeMillis() - startTime;
            return new WindowSnapshot(
                    windowAcquisitions.get(), windowReleases.get(), windowFailures.get(),
                    windowWaitTime.get(), duration);
        }
    }

    /**
     * Snapshot of performance window.
     */
    public static class WindowSnapshot {
        public final long acquisitions;
        public final long releases;
        public final long failures;
        public final long totalWaitTime;
        public final long durationMs;

        WindowSnapshot(long acquisitions, long releases, long failures,
                long totalWaitTime, long durationMs) {
            this.acquisitions = acquisitions;
            this.releases = releases;
            this.failures = failures;
            this.totalWaitTime = totalWaitTime;
            this.durationMs = durationMs;
        }

        public double getAcquisitionRate() {
            return durationMs > 0 ? (acquisitions * 1000.0) / durationMs : 0.0;
        }

        public double getFailureRate() {
            return acquisitions > 0 ? (double) failures / acquisitions : 0.0;
        }

        public double getAverageWaitTime() {
            return acquisitions > 0 ? (double) totalWaitTime / acquisitions : 0.0;
        }
    }

    /**
     * Creates a new PerformanceMonitor.
     */
    public PerformanceMonitor() {
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "PerformanceMonitor");
            t.setDaemon(true);
            return t;
        });

        // Start periodic monitoring
        scheduler.scheduleAtFixedRate(this, 10, 10, TimeUnit.SECONDS);
    }

    @Override
    public void run() {
        try {
            // Rotate performance window
            PerformanceWindow oldWindow = currentWindow.getAndSet(new PerformanceWindow());
            WindowSnapshot snapshot = oldWindow.getSnapshot();

            // Log performance metrics
            if (logger.isDebugEnabled()) {
                logger.debug("Performance window: acquisitions={}, rate={:.2f}/s, failures={:.2f}%, avg_wait={:.2f}μs",
                        snapshot.acquisitions, snapshot.getAcquisitionRate(),
                        snapshot.getFailureRate() * 100, snapshot.getAverageWaitTime() / 1000.0);
            }

            // Trigger adaptive sizing if needed
            if (snapshot.getFailureRate() > 0.1) {
                logger.warn("High failure rate detected: {:.2f}%", snapshot.getFailureRate() * 100);
            }

            if (snapshot.getAverageWaitTime() > 1000000) { // > 1ms
                logger.warn("High average wait time detected: {:.2f}ms", snapshot.getAverageWaitTime() / 1000000.0);
            }

        } catch (Exception e) {
            logger.error("Error in performance monitoring", e);
        }
    }

    /**
     * Records an acquisition request.
     */
    public void recordAcquisitionRequest(int size) {
        totalAcquisitionRequests.incrementAndGet();
        getSizeMetricsInternal(size).recordAcquisition(0); // Will be updated with actual wait time
    }

    /**
     * Records a successful buffer acquisition.
     */
    public void recordSuccessfulAcquisition(int size) {
        totalSuccessfulAcquisitions.incrementAndGet();
        totalMemoryAllocated.addAndGet(size);
        currentMemoryUsage.addAndGet(size);
    }

    /**
     * Records a failed buffer acquisition.
     */
    public void recordFailedAcquisition(int size) {
        totalFailedAcquisitions.incrementAndGet();
        getSizeMetricsInternal(size).recordFailure();
        currentWindow.get().recordFailure();
    }

    /**
     * Records a buffer release.
     */
    public void recordRelease(int size) {
        totalReleases.incrementAndGet();
        getSizeMetricsInternal(size).recordRelease();
        currentWindow.get().recordRelease();
    }

    /**
     * Records a successful buffer release.
     */
    public void recordSuccessfulRelease(int size) {
        totalSuccessfulReleases.incrementAndGet();
        totalMemoryReleased.addAndGet(size);
        currentMemoryUsage.addAndGet(-size);
    }

    /**
     * Records a failed buffer release.
     */
    public void recordFailedRelease(int size) {
        totalFailedReleases.incrementAndGet();
    }

    /**
     * Records buffer acquisition from pool.
     */
    public void recordBufferAcquisition(int size, boolean fromPool) {
        SizeMetrics metrics = sizeMetrics.get(size);
        if (fromPool) {
            // This is a pool hit, wait time is minimal
            metrics.recordAcquisition(100); // 100ns typical
        } else {
            // This is a pool miss, wait time includes allocation
            metrics.recordAcquisition(10000); // 10μs typical for allocation
        }
        currentWindow.get().recordAcquisition(fromPool ? 100 : 10000);
    }

    /**
     * Records buffer release to pool.
     */
    public void recordBufferRelease(int size) {
        getSizeMetricsInternal(size).recordRelease();
        currentWindow.get().recordRelease();
    }

    /**
     * Records allocation failure.
     */
    public void recordAllocationFailure(int size) {
        totalAllocationFailures.incrementAndGet();
        getSizeMetricsInternal(size).recordFailure();
        currentWindow.get().recordFailure();
    }

    /**
     * Records acquisition time.
     */
    public void recordAcquisitionTime(int size, long waitTime) {
        acquisitionCount.incrementAndGet();
        totalAcquisitionTime.addAndGet(waitTime);
        getSizeMetricsInternal(size).recordAcquisition(waitTime);
        currentWindow.get().recordAcquisition(waitTime);
    }

    /**
     * Records buffer trimming.
     */
    public void recordBufferTrim(int size, int count) {
        long memoryFreed = (long) size * count;
        totalMemoryReleased.addAndGet(memoryFreed);
        currentMemoryUsage.addAndGet(-(int) memoryFreed);

        logger.debug("Trimmed {} buffers of size {} ({} bytes freed)", count, size, memoryFreed);
    }

    /**
     * Gets or creates size-specific metrics.
     */
    private SizeMetrics getSizeMetricsInternal(int size) {
        return sizeMetrics.computeIfAbsent(size, k -> new SizeMetrics());
    }

    /**
     * Gets metrics for a specific buffer size.
     */
    public SizeMetricsSnapshot getSizeMetrics(int size) {
        SizeMetrics metrics = sizeMetrics.get(size);
        return metrics != null ? metrics.getSnapshot() : null;
    }

    /**
     * Gets current performance statistics.
     */
    public String getStats() {
        WindowSnapshot window = currentWindow.get().getSnapshot();

        return String.format(
                "Acquisition Rate: %.2f/s, Failure Rate: %.2f%%, Avg Wait: %.2fμs, "
                        + "Memory Usage: %d bytes, Total Acquisitions: %d, Total Releases: %d",
                window.getAcquisitionRate(), window.getFailureRate() * 100,
                window.getAverageWaitTime() / 1000.0, currentMemoryUsage.get(),
                totalAcquisitionRequests.get(), totalReleases.get());
    }

    /**
     * Records memory pressure event.
     */
    public void recordMemoryPressure(MemoryPressureDetector.MemoryPressure pressure) {
        // This would be stored for trend analysis
        if (logger.isDebugEnabled()) {
            logger.debug("Memory pressure recorded: {}", pressure);
        }
    }

    /**
     * Records usage pattern for monitoring.
     */
    public void recordUsagePattern(int bufferSize, BufferPrefetchManager.UsagePatternSnapshot snapshot) {
        // This would be stored for trend analysis
        if (logger.isDebugEnabled()) {
            logger.debug("Usage pattern recorded for size {}: {}", bufferSize, snapshot);
        }
    }

    /**
     * Gets comprehensive performance report.
     */
    public String getDetailedStats() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Performance Monitor Stats ===\n");

        // Global stats
        sb.append("Global:\n");
        sb.append(String.format("  Total Acquisition Requests: %d\n", totalAcquisitionRequests.get()));
        sb.append(String.format("  Successful Acquisitions: %d\n", totalSuccessfulAcquisitions.get()));
        sb.append(String.format("  Failed Acquisitions: %d\n", totalFailedAcquisitions.get()));
        sb.append(String.format("  Total Releases: %d\n", totalReleases.get()));
        sb.append(String.format("  Current Memory Usage: %d bytes\n", currentMemoryUsage.get()));

        // Current window
        WindowSnapshot window = currentWindow.get().getSnapshot();
        sb.append("Current Window (").append(window.durationMs).append("ms):\n");
        sb.append(String.format("  Acquisitions: %d (%.2f/s)\n", window.acquisitions, window.getAcquisitionRate()));
        sb.append(String.format("  Failures: %d (%.2f%%)\n", window.failures, window.getFailureRate() * 100));
        sb.append(String.format("  Avg Wait Time: %.2fμs\n", window.getAverageWaitTime() / 1000.0));

        // Size-specific metrics
        sb.append("Size-specific:\n");
        sizeMetrics.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    SizeMetricsSnapshot snapshot = entry.getValue().getSnapshot();
                    sb.append(String.format("  %dB: acquisitions=%d, failures=%d, peak_usage=%d\n",
                            entry.getKey(), snapshot.acquisitions, snapshot.failures, snapshot.peakUsage));
                });

        return sb.toString();
    }

    /**
     * Gets current memory usage.
     */
    public int getCurrentMemoryUsage() {
        return currentMemoryUsage.get();
    }

    /**
     * Gets total memory allocated.
     */
    public long getTotalMemoryAllocated() {
        return totalMemoryAllocated.get();
    }

    /**
     * Gets average acquisition time.
     */
    public double getAverageAcquisitionTime() {
        long count = acquisitionCount.get();
        return count > 0 ? (double) totalAcquisitionTime.get() / count : 0.0;
    }

    /**
     * Gets failure rate.
     */
    public double getFailureRate() {
        long total = totalAcquisitionRequests.get();
        return total > 0 ? (double) totalFailedAcquisitions.get() / total : 0.0;
    }

    /**
     * Shuts down the performance monitor.
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

        logger.info("PerformanceMonitor shutdown completed");
    }
}