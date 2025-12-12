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
import java.util.List;
import java.util.Map;

/**
 * Aggregated result of multiple batch processing operations.
 * Provides comprehensive summary of batch execution across
 * multiple batches with combined statistics and performance metrics.
 */
public class BatchAggregatedResult {

    /** Unique identifier for this aggregated operation. */
    private final String operationId;

    /** Individual batch results. */
    private final List<BatchResult> batchResults;

    /** Overall success status. */
    private final boolean overallSuccess;

    /** Timestamp when aggregated operation started. */
    private final Instant startTime;

    /** Timestamp when aggregated operation completed. */
    private final Instant endTime;

    /** Total processing time in milliseconds. */
    private final long totalProcessingTimeMs;

    /** Total number of batches processed. */
    private final int totalBatches;

    /** Total number of successful batches. */
    private final int successfulBatches;

    /** Total number of failed batches. */
    private final int failedBatches;

    /** Total files processed across all batches. */
    private final int totalFilesProcessed;

    /** Total bytes processed across all batches. */
    private final long totalBytesProcessed;

    /** Aggregated performance metrics. */
    private final BatchPerformanceMetrics aggregatedMetrics;

    /** Per-batch statistics. */
    private final Map<String, Object> aggregatedStatistics;

    /**
     * Creates a new aggregated result.
     *
     * @param operationId          unique identifier for this operation
     * @param batchResults         individual batch results
     * @param startTime            start timestamp
     * @param endTime              end timestamp
     * @param totalBatches         total number of batches
     * @param successfulBatches    number of successful batches
     * @param failedBatches        number of failed batches
     * @param totalFilesProcessed  total files processed
     * @param totalBytesProcessed  total bytes processed
     * @param aggregatedMetrics    aggregated performance metrics
     * @param aggregatedStatistics aggregated statistics
     */
    public BatchAggregatedResult(String operationId, List<BatchResult> batchResults,
            Instant startTime, Instant endTime, int totalBatches,
            int successfulBatches, int failedBatches,
            int totalFilesProcessed, long totalBytesProcessed,
            BatchPerformanceMetrics aggregatedMetrics,
            Map<String, Object> aggregatedStatistics) {
        this.operationId = operationId;
        this.batchResults = new java.util.ArrayList<>(batchResults);
        this.overallSuccess = successfulBatches > 0 && failedBatches == 0;
        this.startTime = startTime;
        this.endTime = endTime;
        this.totalProcessingTimeMs = java.time.Duration.between(startTime, endTime).toMillis();
        this.totalBatches = totalBatches;
        this.successfulBatches = successfulBatches;
        this.failedBatches = failedBatches;
        this.totalFilesProcessed = totalFilesProcessed;
        this.totalBytesProcessed = totalBytesProcessed;
        this.aggregatedMetrics = aggregatedMetrics;
        this.aggregatedStatistics = aggregatedStatistics != null
                ? new java.util.HashMap<>(aggregatedStatistics)
                : new java.util.HashMap<>();
    }

    /**
     * Gets the operation ID.
     *
     * @return operation ID
     */
    public String getOperationId() {
        return operationId;
    }

    /**
     * Gets the individual batch results.
     *
     * @return immutable list of batch results
     */
    public List<BatchResult> getBatchResults() {
        return new java.util.ArrayList<>(batchResults);
    }

    /**
     * Checks if the overall operation was successful.
     *
     * @return true if successful, false otherwise
     */
    public boolean isOverallSuccess() {
        return overallSuccess;
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
     * Gets the end timestamp.
     *
     * @return end timestamp
     */
    public Instant getEndTime() {
        return endTime;
    }

    /**
     * Gets the total processing time in milliseconds.
     *
     * @return total processing time
     */
    public long getTotalProcessingTimeMs() {
        return totalProcessingTimeMs;
    }

    /**
     * Gets the total number of batches processed.
     *
     * @return total batches
     */
    public int getTotalBatches() {
        return totalBatches;
    }

    /**
     * Gets the number of successful batches.
     *
     * @return successful batches
     */
    public int getSuccessfulBatches() {
        return successfulBatches;
    }

    /**
     * Gets the number of failed batches.
     *
     * @return failed batches
     */
    public int getFailedBatches() {
        return failedBatches;
    }

    /**
     * Gets the total files processed across all batches.
     *
     * @return total files processed
     */
    public int getTotalFilesProcessed() {
        return totalFilesProcessed;
    }

    /**
     * Gets the total bytes processed across all batches.
     *
     * @return total bytes processed
     */
    public long getTotalBytesProcessed() {
        return totalBytesProcessed;
    }

    /**
     * Gets the aggregated performance metrics.
     *
     * @return aggregated performance metrics
     */
    public BatchPerformanceMetrics getAggregatedMetrics() {
        return aggregatedMetrics;
    }

    /**
     * Gets the aggregated statistics.
     *
     * @return immutable map of aggregated statistics
     */
    public Map<String, Object> getAggregatedStatistics() {
        return new java.util.HashMap<>(aggregatedStatistics);
    }

    /**
     * Gets the overall success rate as a percentage.
     *
     * @return success rate (0.0 to 100.0)
     */
    public double getOverallSuccessRate() {
        return totalBatches > 0 ? (double) successfulBatches / totalBatches * 100.0 : 0.0;
    }

    /**
     * Gets the overall throughput in MB/s.
     *
     * @return throughput in MB/s
     */
    public double getOverallThroughputMBps() {
        return totalProcessingTimeMs > 0
                ? (double) totalBytesProcessed / (1024 * 1024) / (totalProcessingTimeMs / 1000.0)
                : 0.0;
    }

    /**
     * Gets the average processing time per batch in milliseconds.
     *
     * @return average processing time per batch
     */
    public double getAverageProcessingTimePerBatchMs() {
        return totalBatches > 0 ? (double) totalProcessingTimeMs / totalBatches : 0.0;
    }

    /**
     * Gets the average processing time per file in milliseconds.
     *
     * @return average processing time per file
     */
    public double getAverageProcessingTimePerFileMs() {
        return totalFilesProcessed > 0 ? (double) totalProcessingTimeMs / totalFilesProcessed : 0.0;
    }

    @Override
    public String toString() {
        if (overallSuccess) {
            return String.format(
                    "BatchAggregatedResult{id='%s', batches=%d, success=true, time=%dms, "
                            + "successfulBatches=%d, failedBatches=%d, files=%d, bytes=%dMB, "
                            + "throughput=%.2fMB/s, avgBatchTime=%.1fms, avgFileTime=%.1fms}",
                    operationId, totalBatches, totalProcessingTimeMs,
                    successfulBatches, failedBatches, totalFilesProcessed,
                    totalBytesProcessed / (1024 * 1024), getOverallThroughputMBps(),
                    getAverageProcessingTimePerBatchMs(), getAverageProcessingTimePerFileMs());
        } else {
            return String.format(
                    "BatchAggregatedResult{id='%s', batches=%d, success=false, time=%dms, "
                            + "successfulBatches=%d, failedBatches=%d}",
                    operationId, totalBatches, totalProcessingTimeMs,
                    successfulBatches, failedBatches);
        }
    }
}