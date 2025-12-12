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
import java.util.Map;

/**
 * Result of a batch operation execution.
 * Provides detailed information about the operation outcome
 * including success status, timing, and resource utilization.
 */
public class BatchOperationResult {

    /** Unique identifier for the batch operation. */
    private final String operationId;

    /** Type of operation that was executed. */
    private final BatchOperationType operationType;

    /** Whether the operation was successful. */
    private final boolean success;

    /** Error that occurred during operation, if any. */
    private final Exception error;

    /** Timestamp when operation started. */
    private final Instant startTime;

    /** Timestamp when operation completed. */
    private final Instant endTime;

    /** Total processing time in milliseconds. */
    private final long processingTimeMs;

    /** Number of files processed in this operation. */
    private final int filesProcessed;

    /** Number of files that failed processing. */
    private final int filesFailed;

    /** Total bytes processed in this operation. */
    private final long bytesProcessed;

    /** Resource utilization during operation. */
    private final ResourceUtilization resourceUtilization;

    /** Operation-specific results and metadata. */
    private final Map<String, Object> results;

    /** Performance metrics for this operation. */
    private final BatchPerformanceMetrics performanceMetrics;

    /**
     * Creates a successful batch operation result.
     *
     * @param operationId         unique identifier for this operation
     * @param operationType       type of operation
     * @param startTime           start timestamp
     * @param endTime             end timestamp
     * @param filesProcessed      number of files processed
     * @param filesFailed         number of files that failed
     * @param bytesProcessed      total bytes processed
     * @param resourceUtilization resource utilization during operation
     * @param results             operation-specific results
     * @param performanceMetrics  performance metrics
     */
    public BatchOperationResult(String operationId, BatchOperationType operationType,
            Instant startTime, Instant endTime, int filesProcessed,
            int filesFailed, long bytesProcessed,
            ResourceUtilization resourceUtilization,
            Map<String, Object> results,
            BatchPerformanceMetrics performanceMetrics) {
        this.operationId = operationId;
        this.operationType = operationType;
        this.success = true;
        this.error = null;
        this.startTime = startTime;
        this.endTime = endTime;
        this.processingTimeMs = java.time.Duration.between(startTime, endTime).toMillis();
        this.filesProcessed = filesProcessed;
        this.filesFailed = filesFailed;
        this.bytesProcessed = bytesProcessed;
        this.resourceUtilization = resourceUtilization;
        this.results = results != null ? new java.util.HashMap<>(results) : new java.util.HashMap<>();
        this.performanceMetrics = performanceMetrics;
    }

    /**
     * Creates a failed batch operation result.
     *
     * @param operationId         unique identifier for this operation
     * @param operationType       type of operation
     * @param startTime           start timestamp
     * @param endTime             end timestamp
     * @param error               error that occurred
     * @param filesProcessed      number of files processed before failure
     * @param filesFailed         number of files that failed
     * @param bytesProcessed      bytes processed before failure
     * @param resourceUtilization resource utilization before failure
     * @param results             partial results (may be null)
     */
    public BatchOperationResult(String operationId, BatchOperationType operationType,
            Instant startTime, Instant endTime, Exception error,
            int filesProcessed, int filesFailed, long bytesProcessed,
            ResourceUtilization resourceUtilization,
            Map<String, Object> results) {
        this.operationId = operationId;
        this.operationType = operationType;
        this.success = false;
        this.error = error;
        this.startTime = startTime;
        this.endTime = endTime;
        this.processingTimeMs = java.time.Duration.between(startTime, endTime).toMillis();
        this.filesProcessed = filesProcessed;
        this.filesFailed = filesFailed;
        this.bytesProcessed = bytesProcessed;
        this.resourceUtilization = resourceUtilization;
        this.results = results != null ? new java.util.HashMap<>(results) : new java.util.HashMap<>();
        this.performanceMetrics = null;
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
     * Gets the operation type.
     *
     * @return operation type
     */
    public BatchOperationType getOperationType() {
        return operationType;
    }

    /**
     * Checks if the operation was successful.
     *
     * @return true if successful, false otherwise
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * Gets the error that occurred.
     *
     * @return error, or null if successful
     */
    public Exception getError() {
        return error;
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
     * Gets the processing time in milliseconds.
     *
     * @return processing time
     */
    public long getProcessingTimeMs() {
        return processingTimeMs;
    }

    /**
     * Gets the number of files processed.
     *
     * @return files processed
     */
    public int getFilesProcessed() {
        return filesProcessed;
    }

    /**
     * Gets the number of files that failed.
     *
     * @return files failed
     */
    public int getFilesFailed() {
        return filesFailed;
    }

    /**
     * Gets the total bytes processed.
     *
     * @return bytes processed
     */
    public long getBytesProcessed() {
        return bytesProcessed;
    }

    /**
     * Gets the resource utilization.
     *
     * @return resource utilization
     */
    public ResourceUtilization getResourceUtilization() {
        return resourceUtilization;
    }

    /**
     * Gets the operation results.
     *
     * @return immutable map of results
     */
    public Map<String, Object> getResults() {
        return new java.util.HashMap<>(results);
    }

    /**
     * Gets the performance metrics.
     *
     * @return performance metrics
     */
    public BatchPerformanceMetrics getPerformanceMetrics() {
        return performanceMetrics;
    }

    /**
     * Gets the success rate as a percentage.
     *
     * @return success rate (0.0 to 100.0)
     */
    public double getSuccessRate() {
        int totalFiles = filesProcessed + filesFailed;
        return totalFiles > 0 ? (double) filesProcessed / totalFiles * 100.0 : 0.0;
    }

    /**
     * Gets the throughput in MB/s.
     *
     * @return throughput in MB/s
     */
    public double getThroughputMBps() {
        return processingTimeMs > 0 ? (double) bytesProcessed / (1024 * 1024) / (processingTimeMs / 1000.0) : 0.0;
    }

    @Override
    public String toString() {
        if (success) {
            return String.format(
                    "BatchOperationResult{id='%s', type=%s, success=true, time=%dms, "
                            + "files=%d, failed=%d, bytes=%dMB, throughput=%.2fMB/s}",
                    operationId, operationType, processingTimeMs,
                    filesProcessed, filesFailed, bytesProcessed / (1024 * 1024),
                    getThroughputMBps());
        } else {
            return String.format(
                    "BatchOperationResult{id='%s', type=%s, success=false, time=%dms, "
                            + "error='%s'}",
                    operationId, operationType, processingTimeMs,
                    error != null ? error.getMessage() : "Unknown");
        }
    }

    /**
     * Resource utilization information for batch operations.
     */
    public static class ResourceUtilization {
        public final double cpuUtilizationPercent;
        public final double memoryUtilizationPercent;
        public final double ioUtilizationPercent;
        public final int maxConcurrentOperations;
        public final long peakMemoryUsageMB;
        public final long totalBytesRead;
        public final long totalBytesWritten;

        /**
         * Creates resource utilization information.
         *
         * @param cpuUtilizationPercent    CPU utilization percentage
         * @param memoryUtilizationPercent memory utilization percentage
         * @param ioUtilizationPercent     I/O utilization percentage
         * @param maxConcurrentOperations  maximum concurrent operations
         * @param peakMemoryUsageMB        peak memory usage in MB
         * @param totalBytesRead           total bytes read
         * @param totalBytesWritten        total bytes written
         */
        public ResourceUtilization(double cpuUtilizationPercent, double memoryUtilizationPercent,
                double ioUtilizationPercent, int maxConcurrentOperations,
                long peakMemoryUsageMB, long totalBytesRead, long totalBytesWritten) {
            this.cpuUtilizationPercent = cpuUtilizationPercent;
            this.memoryUtilizationPercent = memoryUtilizationPercent;
            this.ioUtilizationPercent = ioUtilizationPercent;
            this.maxConcurrentOperations = maxConcurrentOperations;
            this.peakMemoryUsageMB = peakMemoryUsageMB;
            this.totalBytesRead = totalBytesRead;
            this.totalBytesWritten = totalBytesWritten;
        }

        @Override
        public String toString() {
            return String.format(
                    "ResourceUtilization{cpu=%.1f%%, memory=%.1f%%, io=%.1f%%, "
                            + "maxConcurrent=%d, peakMemory=%dMB, read=%dMB, written=%dMB}",
                    cpuUtilizationPercent, memoryUtilizationPercent, ioUtilizationPercent,
                    maxConcurrentOperations, peakMemoryUsageMB,
                    totalBytesRead / (1024 * 1024), totalBytesWritten / (1024 * 1024));
        }
    }
}