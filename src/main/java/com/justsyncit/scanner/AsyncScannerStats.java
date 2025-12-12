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

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Comprehensive statistics for async filesystem scanner operations.
 * Provides detailed metrics about scanning performance, resource usage, and operational health.
 */
public class AsyncScannerStats {

    /** Timestamp when statistics were created. */
    private final Instant statsTimestamp;

    /** Total number of scans initiated. */
    private final AtomicLong totalScansInitiated;

    /** Total number of scans completed successfully. */
    private final AtomicLong totalScansCompleted;

    /** Total number of scans failed. */
    private final AtomicLong totalScansFailed;

    /** Total number of scans cancelled. */
    private final AtomicLong totalScansCancelled;

    /** Total number of files scanned. */
    private final AtomicLong totalFilesScanned;

    /** Total number of directories scanned. */
    private final AtomicLong totalDirectoriesScanned;

    /** Total number of bytes processed. */
    private final AtomicLong totalBytesProcessed;

    /** Current number of active scans. */
    private final AtomicInteger activeScans;

    /** Peak number of concurrent scans. */
    private final AtomicInteger peakConcurrentScans;

    /** Total number of watch service registrations. */
    private final AtomicLong totalWatchRegistrations;

    /** Current number of active watch registrations. */
    private final AtomicInteger activeWatchRegistrations;

    /** Total number of file change events processed. */
    private final AtomicLong totalFileChangeEvents;

    /** Total number of watch service errors. */
    private final AtomicLong totalWatchServiceErrors;

    /** Average scanning throughput (files per second). */
    private volatile double averageThroughput;

    /** Peak memory usage during scanning (bytes). */
    private volatile long peakMemoryUsage;

    /** Total backpressure events encountered. */
    private final AtomicLong totalBackpressureEvents;

    /** Total adaptive resizing events. */
    private final AtomicLong totalAdaptiveResizingEvents;

    /** Scanner uptime in milliseconds. */
    private volatile long uptimeMs;

    /** Additional operational metrics. */
    private final Map<String, Object> operationalMetrics;

    /**
     * Creates a new AsyncScannerStats.
     */
    public AsyncScannerStats() {
        this.statsTimestamp = Instant.now();
        this.totalScansInitiated = new AtomicLong(0);
        this.totalScansCompleted = new AtomicLong(0);
        this.totalScansFailed = new AtomicLong(0);
        this.totalScansCancelled = new AtomicLong(0);
        this.totalFilesScanned = new AtomicLong(0);
        this.totalDirectoriesScanned = new AtomicLong(0);
        this.totalBytesProcessed = new AtomicLong(0);
        this.activeScans = new AtomicInteger(0);
        this.peakConcurrentScans = new AtomicInteger(0);
        this.totalWatchRegistrations = new AtomicLong(0);
        this.activeWatchRegistrations = new AtomicInteger(0);
        this.totalFileChangeEvents = new AtomicLong(0);
        this.totalWatchServiceErrors = new AtomicLong(0);
        this.averageThroughput = 0.0;
        this.peakMemoryUsage = 0L;
        this.totalBackpressureEvents = new AtomicLong(0);
        this.totalAdaptiveResizingEvents = new AtomicLong(0);
        this.uptimeMs = 0L;
        this.operationalMetrics = new ConcurrentHashMap<>();
    }

    // Increment methods

    /**
     * Increments total scans initiated.
     */
    public void incrementScansInitiated() {
        totalScansInitiated.incrementAndGet();
    }

    /**
     * Increments total scans completed.
     */
    public void incrementScansCompleted() {
        totalScansCompleted.incrementAndGet();
    }

    /**
     * Increments total scans failed.
     */
    public void incrementScansFailed() {
        totalScansFailed.incrementAndGet();
    }

    /**
     * Increments total scans cancelled.
     */
    public void incrementScansCancelled() {
        totalScansCancelled.incrementAndGet();
    }

    /**
     * Increments total files scanned.
     *
     * @param count number of files to add
     */
    public void addFilesScanned(long count) {
        totalFilesScanned.addAndGet(count);
    }

    /**
     * Increments total directories scanned.
     *
     * @param count number of directories to add
     */
    public void addDirectoriesScanned(long count) {
        totalDirectoriesScanned.addAndGet(count);
    }

    /**
     * Increments total bytes processed.
     *
     * @param bytes number of bytes to add
     */
    public void addBytesProcessed(long bytes) {
        totalBytesProcessed.addAndGet(bytes);
    }

    /**
     * Increments active scans count.
     *
     * @return new active count
     */
    public int incrementActiveScans() {
        int active = activeScans.incrementAndGet();
        int peak = peakConcurrentScans.get();
        while (active > peak && !peakConcurrentScans.compareAndSet(peak, active)) {
            peak = peakConcurrentScans.get();
        }
        return active;
    }

    /**
     * Decrements active scans count.
     *
     * @return new active count
     */
    public int decrementActiveScans() {
        return activeScans.decrementAndGet();
    }

    /**
     * Increments total watch registrations.
     */
    public void incrementWatchRegistrations() {
        totalWatchRegistrations.incrementAndGet();
    }

    /**
     * Increments active watch registrations.
     *
     * @return new active count
     */
    public int incrementActiveWatchRegistrations() {
        return activeWatchRegistrations.incrementAndGet();
    }

    /**
     * Decrements active watch registrations.
     *
     * @return new active count
     */
    public int decrementActiveWatchRegistrations() {
        return activeWatchRegistrations.decrementAndGet();
    }

    /**
     * Increments total file change events.
     */
    public void incrementFileChangeEvents() {
        totalFileChangeEvents.incrementAndGet();
    }

    /**
     * Increments total watch service errors.
     */
    public void incrementWatchServiceErrors() {
        totalWatchServiceErrors.incrementAndGet();
    }

    /**
     * Increments total backpressure events.
     */
    public void incrementBackpressureEvents() {
        totalBackpressureEvents.incrementAndGet();
    }

    /**
     * Increments total adaptive resizing events.
     */
    public void incrementAdaptiveResizingEvents() {
        totalAdaptiveResizingEvents.incrementAndGet();
    }

    // Setter methods

    /**
     * Sets the average scanning throughput.
     *
     * @param throughput average throughput in files per second
     */
    public void setAverageThroughput(double throughput) {
        this.averageThroughput = throughput;
    }

    /**
     * Sets the peak memory usage.
     *
     * @param peakMemoryUsage peak memory usage in bytes
     */
    public void setPeakMemoryUsage(long peakMemoryUsage) {
        this.peakMemoryUsage = peakMemoryUsage;
    }

    /**
     * Sets the scanner uptime.
     *
     * @param uptimeMs uptime in milliseconds
     */
    public void setUptimeMs(long uptimeMs) {
        this.uptimeMs = uptimeMs;
    }

    /**
     * Sets an operational metric.
     *
     * @param key metric key
     * @param value metric value
     */
    public void setOperationalMetric(String key, Object value) {
        operationalMetrics.put(key, value);
    }

    // Getter methods

    /**
     * Gets the timestamp when statistics were created.
     *
     * @return stats timestamp
     */
    public Instant getStatsTimestamp() {
        return statsTimestamp;
    }

    /**
     * Gets total number of scans initiated.
     *
     * @return total scans initiated
     */
    public long getTotalScansInitiated() {
        return totalScansInitiated.get();
    }

    /**
     * Gets total number of scans completed successfully.
     *
     * @return total scans completed
     */
    public long getTotalScansCompleted() {
        return totalScansCompleted.get();
    }

    /**
     * Gets total number of scans failed.
     *
     * @return total scans failed
     */
    public long getTotalScansFailed() {
        return totalScansFailed.get();
    }

    /**
     * Gets total number of scans cancelled.
     *
     * @return total scans cancelled
     */
    public long getTotalScansCancelled() {
        return totalScansCancelled.get();
    }

    /**
     * Gets current number of active scans.
     *
     * @return active scans count
     */
    public int getActiveScans() {
        return activeScans.get();
    }

    /**
     * Gets peak number of concurrent scans.
     *
     * @return peak concurrent scans
     */
    public int getPeakConcurrentScans() {
        return peakConcurrentScans.get();
    }

    /**
     * Gets scan success rate (percentage).
     *
     * @return success rate (0.0 to 100.0)
     */
    public double getScanSuccessRate() {
        long total = totalScansInitiated.get();
        if (total == 0) {
            return 0.0;
        }
        return (totalScansCompleted.get() * 100.0) / total;
    }

    /**
     * Gets scan failure rate (percentage).
     *
     * @return failure rate (0.0 to 100.0)
     */
    public double getScanFailureRate() {
        long total = totalScansInitiated.get();
        if (total == 0) {
            return 0.0;
        }
        return (totalScansFailed.get() * 100.0) / total;
    }

    /**
     * Gets total number of files scanned.
     *
     * @return total files scanned
     */
    public long getTotalFilesScanned() {
        return totalFilesScanned.get();
    }

    /**
     * Gets total number of directories scanned.
     *
     * @return total directories scanned
     */
    public long getTotalDirectoriesScanned() {
        return totalDirectoriesScanned.get();
    }

    /**
     * Gets total number of bytes processed.
     *
     * @return total bytes processed
     */
    public long getTotalBytesProcessed() {
        return totalBytesProcessed.get();
    }

    /**
     * Gets total number of watch service registrations.
     *
     * @return total watch registrations
     */
    public long getTotalWatchRegistrations() {
        return totalWatchRegistrations.get();
    }

    /**
     * Gets current number of active watch registrations.
     *
     * @return active watch registrations
     */
    public int getActiveWatchRegistrations() {
        return activeWatchRegistrations.get();
    }

    /**
     * Gets total number of file change events processed.
     *
     * @return total file change events
     */
    public long getTotalFileChangeEvents() {
        return totalFileChangeEvents.get();
    }

    /**
     * Gets total number of watch service errors.
     *
     * @return total watch service errors
     */
    public long getTotalWatchServiceErrors() {
        return totalWatchServiceErrors.get();
    }

    /**
     * Gets average scanning throughput.
     *
     * @return average throughput in files per second
     */
    public double getAverageThroughput() {
        return averageThroughput;
    }

    /**
     * Gets peak memory usage during scanning.
     *
     * @return peak memory usage in bytes
     */
    public long getPeakMemoryUsage() {
        return peakMemoryUsage;
    }

    /**
     * Gets total backpressure events encountered.
     *
     * @return total backpressure events
     */
    public long getTotalBackpressureEvents() {
        return totalBackpressureEvents.get();
    }

    /**
     * Gets total adaptive resizing events.
     *
     * @return total adaptive resizing events
     */
    public long getTotalAdaptiveResizingEvents() {
        return totalAdaptiveResizingEvents.get();
    }

    /**
     * Gets scanner uptime.
     *
     * @return uptime in milliseconds
     */
    public long getUptimeMs() {
        return uptimeMs;
    }

    /**
     * Gets operational metrics.
     *
     * @return operational metrics map
     */
    public Map<String, Object> getOperationalMetrics() {
        return new ConcurrentHashMap<>(operationalMetrics);
    }

    /**
     * Gets operational metric by key.
     *
     * @param key metric key
     * @return metric value, or null if not found
     */
    public Object getOperationalMetric(String key) {
        return operationalMetrics.get(key);
    }

    /**
     * Creates a summary of the statistics.
     *
     * @return summary string
     */
    public String createSummary() {
        return String.format(
            "AsyncScannerStats{scans=%d(completed)/%d(failed)/%d(cancelled), " +
            "active=%d, peakConcurrent=%d, successRate=%.1f%%, " +
            "files=%d, dirs=%d, bytes=%d, throughput=%.2f/sec, " +
            "watchRegs=%d(active)/%d(total), events=%d, errors=%d, " +
            "uptime=%dms, peakMemory=%d bytes}",
            getTotalScansCompleted(), getTotalScansFailed(), getTotalScansCancelled(),
            getActiveScans(), getPeakConcurrentScans(), getScanSuccessRate(),
            getTotalFilesScanned(), getTotalDirectoriesScanned(), getTotalBytesProcessed(),
            averageThroughput, getActiveWatchRegistrations(), getTotalWatchRegistrations(),
            getTotalFileChangeEvents(), getTotalWatchServiceErrors(), uptimeMs, peakMemoryUsage
        );
    }

    @Override
    public String toString() {
        return createSummary();
    }

    /**
     * Resets all statistics to initial values.
     */
    public void reset() {
        totalScansInitiated.set(0);
        totalScansCompleted.set(0);
        totalScansFailed.set(0);
        totalScansCancelled.set(0);
        totalFilesScanned.set(0);
        totalDirectoriesScanned.set(0);
        totalBytesProcessed.set(0);
        activeScans.set(0);
        peakConcurrentScans.set(0);
        totalWatchRegistrations.set(0);
        activeWatchRegistrations.set(0);
        totalFileChangeEvents.set(0);
        totalWatchServiceErrors.set(0);
        averageThroughput = 0.0;
        peakMemoryUsage = 0L;
        totalBackpressureEvents.set(0);
        totalAdaptiveResizingEvents.set(0);
        uptimeMs = 0L;
        operationalMetrics.clear();
    }
}