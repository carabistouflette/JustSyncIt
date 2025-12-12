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

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Statistics for batch processing operations.
 * Provides comprehensive metrics for monitoring batch performance,
 * resource utilization, and operational efficiency.
 */
public class BatchProcessingStats {

    /** Total number of batches processed. */
    private final AtomicLong totalBatchesProcessed = new AtomicLong(0);

    /** Total number of files processed across all batches. */
    private final AtomicLong totalFilesProcessed = new AtomicLong(0);

    /** Total number of successful file operations. */
    private final AtomicLong successfulFileOperations = new AtomicLong(0);

    /** Total number of failed file operations. */
    private final AtomicLong failedFileOperations = new AtomicLong(0);

    /** Total bytes processed across all batches. */
    private final AtomicLong totalBytesProcessed = new AtomicLong(0);

    /** Total processing time across all batches in milliseconds. */
    private final AtomicLong totalProcessingTimeMs = new AtomicLong(0);

    /** Number of currently active batch operations. */
    private final AtomicInteger activeBatchOperations = new AtomicInteger(0);

    /** Number of currently active file operations. */
    private final AtomicInteger activeFileOperations = new AtomicInteger(0);

    /** Peak number of concurrent batch operations. */
    private final AtomicInteger peakConcurrentBatches = new AtomicInteger(0);

    /** Peak number of concurrent file operations. */
    private final AtomicInteger peakConcurrentFileOps = new AtomicInteger(0);

    /** Timestamp when statistics collection started. */
    private final Instant startTime;

    /** Last time statistics were updated. */
    private volatile Instant lastUpdateTime;

    /**
     * Creates a new BatchProcessingStats.
     */
    public BatchProcessingStats() {
        this.startTime = Instant.now();
        this.lastUpdateTime = Instant.now();
    }

    /**
     * Records the start of a batch operation.
     */
    public void recordBatchStart() {
        activeBatchOperations.incrementAndGet();
        peakConcurrentBatches.updateAndGet(peak -> Math.max(peak, activeBatchOperations.get()));
        totalBatchesProcessed.incrementAndGet();
        lastUpdateTime = Instant.now();
    }

    /**
     * Records the completion of a batch operation.
     *
     * @param processingTimeMs time taken to process the batch
     * @param filesProcessed   number of files processed in the batch
     * @param bytesProcessed   total bytes processed in the batch
     */
    public void recordBatchCompletion(long processingTimeMs, int filesProcessed, long bytesProcessed) {
        activeBatchOperations.decrementAndGet();
        totalFilesProcessed.addAndGet(filesProcessed);
        totalBytesProcessed.addAndGet(bytesProcessed);
        totalProcessingTimeMs.addAndGet(processingTimeMs);
        lastUpdateTime = Instant.now();
    }

    /**
     * Records a successful file operation.
     *
     * @param fileSize         size of the file processed
     * @param processingTimeMs time taken to process the file
     */
    public void recordSuccessfulFileOperation(long fileSize, long processingTimeMs) {
        activeFileOperations.incrementAndGet();
        peakConcurrentFileOps.updateAndGet(peak -> Math.max(peak, activeFileOperations.get()));
        successfulFileOperations.incrementAndGet();
        lastUpdateTime = Instant.now();
    }

    /**
     * Records a failed file operation.
     *
     * @param fileSize         size of the file that failed
     * @param processingTimeMs time taken before failure
     */
    public void recordFailedFileOperation(long fileSize, long processingTimeMs) {
        activeFileOperations.decrementAndGet();
        failedFileOperations.incrementAndGet();
        totalProcessingTimeMs.addAndGet(processingTimeMs);
        lastUpdateTime = Instant.now();
    }

    /**
     * Gets the total number of batches processed.
     *
     * @return total batches processed
     */
    public long getTotalBatchesProcessed() {
        return totalBatchesProcessed.get();
    }

    /**
     * Gets the total number of files processed.
     *
     * @return total files processed
     */
    public long getTotalFilesProcessed() {
        return totalFilesProcessed.get();
    }

    /**
     * Gets the total number of successful file operations.
     *
     * @return successful file operations
     */
    public long getSuccessfulFileOperations() {
        return successfulFileOperations.get();
    }

    /**
     * Gets the total number of failed file operations.
     *
     * @return failed file operations
     */
    public long getFailedFileOperations() {
        return failedFileOperations.get();
    }

    /**
     * Gets the total bytes processed.
     *
     * @return total bytes processed
     */
    public long getTotalBytesProcessed() {
        return totalBytesProcessed.get();
    }

    /**
     * Gets the total processing time in milliseconds.
     *
     * @return total processing time
     */
    public long getTotalProcessingTimeMs() {
        return totalProcessingTimeMs.get();
    }

    /**
     * Gets the number of currently active batch operations.
     *
     * @return active batch operations
     */
    public int getActiveBatchOperations() {
        return activeBatchOperations.get();
    }

    /**
     * Gets the number of currently active file operations.
     *
     * @return active file operations
     */
    public int getActiveFileOperations() {
        return activeFileOperations.get();
    }

    /**
     * Gets the peak number of concurrent batch operations.
     *
     * @return peak concurrent batches
     */
    public int getPeakConcurrentBatches() {
        return peakConcurrentBatches.get();
    }

    /**
     * Gets the peak number of concurrent file operations.
     *
     * @return peak concurrent file operations
     */
    public int getPeakConcurrentFileOps() {
        return peakConcurrentFileOps.get();
    }

    /**
     * Gets the start timestamp.
     *
     * @return start timestamp
     */
    public Instant getStartTime() {
        return startTime;
    }

    /**
     * Gets the last update timestamp.
     *
     * @return last update timestamp
     */
    public Instant getLastUpdateTime() {
        return lastUpdateTime;
    }

    /**
     * Gets the average processing time per batch in milliseconds.
     *
     * @return average processing time per batch
     */
    public double getAverageProcessingTimePerBatchMs() {
        long batches = totalBatchesProcessed.get();
        return batches > 0 ? (double) totalProcessingTimeMs.get() / batches : 0.0;
    }

    /**
     * Gets the average processing time per file in milliseconds.
     *
     * @return average processing time per file
     */
    public double getAverageProcessingTimePerFileMs() {
        long files = totalFilesProcessed.get();
        return files > 0 ? (double) totalProcessingTimeMs.get() / files : 0.0;
    }

    /**
     * Gets the success rate as a percentage.
     *
     * @return success rate (0.0 to 100.0)
     */
    public double getSuccessRate() {
        long successful = successfulFileOperations.get();
        long failed = failedFileOperations.get();
        long total = successful + failed;
        return total > 0 ? (double) successful / total * 100.0 : 0.0;
    }

    /**
     * Gets the throughput in MB/s.
     *
     * @return throughput in MB/s
     */
    public double getThroughputMBps() {
        long totalTimeMs = totalProcessingTimeMs.get();
        long totalBytes = totalBytesProcessed.get();
        return totalTimeMs > 0 ? (double) totalBytes / (1024 * 1024) / (totalTimeMs / 1000.0) : 0.0;
    }

    /**
     * Gets the uptime in milliseconds.
     *
     * @return uptime in milliseconds
     */
    public long getUptimeMs() {
        return java.time.Duration.between(startTime, Instant.now()).toMillis();
    }

    /**
     * Resets all statistics.
     */
    public void reset() {
        totalBatchesProcessed.set(0);
        totalFilesProcessed.set(0);
        successfulFileOperations.set(0);
        failedFileOperations.set(0);
        totalBytesProcessed.set(0);
        totalProcessingTimeMs.set(0);
        activeBatchOperations.set(0);
        activeFileOperations.set(0);
        peakConcurrentBatches.set(0);
        peakConcurrentFileOps.set(0);
        lastUpdateTime = Instant.now();
    }

    /**
     * Creates a string representation of the statistics.
     *
     * @return string representation
     */
    @Override
    public String toString() {
        return String.format(
                "BatchProcessingStats{batches=%d, files=%d, successful=%d, failed=%d, "
                        + "bytes=%dMB, time=%ds, avgBatchTime=%.1fms, avgFileTime=%.1fms, "
                        + "successRate=%.2f%%, throughput=%.2fMB/s, activeBatches=%d, activeFiles=%d}",
                getTotalBatchesProcessed(), getTotalFilesProcessed(),
                getSuccessfulFileOperations(), getFailedFileOperations(),
                getTotalBytesProcessed() / (1024 * 1024),
                getTotalProcessingTimeMs() / 1000,
                getAverageProcessingTimePerBatchMs(),
                getAverageProcessingTimePerFileMs(),
                getSuccessRate(),
                getThroughputMBps(),
                getActiveBatchOperations(), getActiveFileOperations());
    }

    /**
     * Creates a snapshot of current statistics.
     *
     * @return immutable snapshot
     */
    public BatchProcessingStatsSnapshot createSnapshot() {
        return new BatchProcessingStatsSnapshot(
                getTotalBatchesProcessed(),
                getTotalFilesProcessed(),
                getSuccessfulFileOperations(),
                getFailedFileOperations(),
                getTotalBytesProcessed(),
                getTotalProcessingTimeMs(),
                getActiveBatchOperations(),
                getActiveFileOperations(),
                getPeakConcurrentBatches(),
                getPeakConcurrentFileOps(),
                getSuccessRate(),
                getThroughputMBps(),
                getUptimeMs());
    }

    /**
     * Immutable snapshot of batch processing statistics.
     */
    public static class BatchProcessingStatsSnapshot {
        public final long totalBatchesProcessed;
        public final long totalFilesProcessed;
        public final long successfulFileOperations;
        public final long failedFileOperations;
        public final long totalBytesProcessed;
        public final long totalProcessingTimeMs;
        public final int activeBatchOperations;
        public final int activeFileOperations;
        public final int peakConcurrentBatches;
        public final int peakConcurrentFileOps;
        public final double successRate;
        public final double throughputMBps;
        public final long uptimeMs;

        public BatchProcessingStatsSnapshot(long totalBatchesProcessed, long totalFilesProcessed,
                long successfulFileOperations, long failedFileOperations,
                long totalBytesProcessed, long totalProcessingTimeMs,
                int activeBatchOperations, int activeFileOperations,
                int peakConcurrentBatches, int peakConcurrentFileOps,
                double successRate, double throughputMBps, long uptimeMs) {
            this.totalBatchesProcessed = totalBatchesProcessed;
            this.totalFilesProcessed = totalFilesProcessed;
            this.successfulFileOperations = successfulFileOperations;
            this.failedFileOperations = failedFileOperations;
            this.totalBytesProcessed = totalBytesProcessed;
            this.totalProcessingTimeMs = totalProcessingTimeMs;
            this.activeBatchOperations = activeBatchOperations;
            this.activeFileOperations = activeFileOperations;
            this.peakConcurrentBatches = peakConcurrentBatches;
            this.peakConcurrentFileOps = peakConcurrentFileOps;
            this.successRate = successRate;
            this.throughputMBps = throughputMBps;
            this.uptimeMs = uptimeMs;
        }

        @Override
        public String toString() {
            return String.format(
                    "BatchProcessingStatsSnapshot{batches=%d, files=%d, successRate=%.2f%%, "
                            + "throughput=%.2fMB/s, uptime=%ds}",
                    totalBatchesProcessed, totalFilesProcessed, successRate * 100,
                    throughputMBps, uptimeMs / 1000);
        }
    }
}