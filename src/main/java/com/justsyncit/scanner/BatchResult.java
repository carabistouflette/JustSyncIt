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

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Result of a batch processing operation.
 * Contains comprehensive information about batch execution including
 * success/failure status, timing information, and per-file results.
 */
public class BatchResult {

    /** Unique identifier for this batch operation. */
    private final String batchId;
    
    /** Files that were processed in this batch. */
    private final List<Path> files;
    
    /** Whether the batch operation was successful. */
    private final boolean success;
    
    /** Error that occurred during batch processing, if any. */
    private final Exception error;
    
    /** Timestamp when batch processing started. */
    private final Instant startTime;
    
    /** Timestamp when batch processing completed. */
    private final Instant endTime;
    
    /** Total processing time in milliseconds. */
    private final long processingTimeMs;
    
    /** Number of files successfully processed. */
    private final int successfulFiles;
    
    /** Number of files that failed processing. */
    private final int failedFiles;
    
    /** Total bytes processed. */
    private final long totalBytesProcessed;
    
    /** Per-file processing results. */
    private final Map<Path, FileProcessingResult> fileResults;
    
    /** Batch processing statistics. */
    private final BatchProcessingStats statistics;
    
    /** Performance metrics for this batch. */
    private final BatchPerformanceMetrics performanceMetrics;

    /**
     * Creates a successful batch result.
     *
     * @param batchId unique identifier for this batch
     * @param files files that were processed
     * @param startTime start timestamp
     * @param endTime end timestamp
     * @param successfulFiles number of successful files
     * @param failedFiles number of failed files
     * @param totalBytesProcessed total bytes processed
     * @param fileResults per-file processing results
     * @param statistics batch processing statistics
     * @param performanceMetrics performance metrics
     */
    public BatchResult(String batchId, List<Path> files, Instant startTime, Instant endTime,
                    int successfulFiles, int failedFiles, long totalBytesProcessed,
                    Map<Path, FileProcessingResult> fileResults,
                    BatchProcessingStats statistics, BatchPerformanceMetrics performanceMetrics) {
        this.batchId = batchId;
        this.files = new java.util.ArrayList<>(files);
        this.success = true;
        this.error = null;
        this.startTime = startTime;
        this.endTime = endTime;
        this.processingTimeMs = java.time.Duration.between(startTime, endTime).toMillis();
        this.successfulFiles = successfulFiles;
        this.failedFiles = failedFiles;
        this.totalBytesProcessed = totalBytesProcessed;
        this.fileResults = fileResults != null ? new java.util.HashMap<>(fileResults) : new java.util.HashMap<>();
        this.statistics = statistics;
        this.performanceMetrics = performanceMetrics;
    }

    /**
     * Creates a failed batch result.
     *
     * @param batchId unique identifier for this batch
     * @param files files that were attempted to be processed
     * @param startTime start timestamp
     * @param endTime end timestamp
     * @param error error that occurred
     * @param fileResults per-file processing results (may be partial)
     */
    public BatchResult(String batchId, List<Path> files, Instant startTime, Instant endTime,
                    Exception error, Map<Path, FileProcessingResult> fileResults) {
        this.batchId = batchId;
        this.files = new java.util.ArrayList<>(files);
        this.success = false;
        this.error = error;
        this.startTime = startTime;
        this.endTime = endTime;
        this.processingTimeMs = java.time.Duration.between(startTime, endTime).toMillis();
        this.successfulFiles = 0;
        this.failedFiles = files.size();
        this.totalBytesProcessed = 0;
        this.fileResults = fileResults != null ? new java.util.HashMap<>(fileResults) : new java.util.HashMap<>();
        this.statistics = null;
        this.performanceMetrics = null;
    }

    /**
     * Gets the batch ID.
     *
     * @return batch ID
     */
    public String getBatchId() {
        return batchId;
    }

    /**
     * Gets the list of files processed.
     *
     * @return immutable list of files
     */
    public List<Path> getFiles() {
        return new java.util.ArrayList<>(files);
    }

    /**
     * Checks if the batch was successful.
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
     * @return processing time in milliseconds
     */
    public long getProcessingTimeMs() {
        return processingTimeMs;
    }

    /**
     * Gets the number of successfully processed files.
     *
     * @return number of successful files
     */
    public int getSuccessfulFiles() {
        return successfulFiles;
    }

    /**
     * Gets the number of failed files.
     *
     * @return number of failed files
     */
    public int getFailedFiles() {
        return failedFiles;
    }

    /**
     * Gets the total bytes processed.
     *
     * @return total bytes processed
     */
    public long getTotalBytesProcessed() {
        return totalBytesProcessed;
    }

    /**
     * Gets the per-file processing results.
     *
     * @return immutable map of file results
     */
    public Map<Path, FileProcessingResult> getFileResults() {
        return new java.util.HashMap<>(fileResults);
    }

    /**
     * Gets the batch processing statistics.
     *
     * @return batch processing statistics
     */
    public BatchProcessingStats getStatistics() {
        return statistics;
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
        int totalFiles = successfulFiles + failedFiles;
        return totalFiles > 0 ? (double) successfulFiles / totalFiles * 100.0 : 0.0;
    }

    /**
     * Gets the throughput in MB/s.
     *
     * @return throughput in MB/s
     */
    public double getThroughputMBps() {
        return processingTimeMs > 0 ? (double) totalBytesProcessed / (1024 * 1024) / (processingTimeMs / 1000.0) : 0.0;
    }

    @Override
    public String toString() {
        if (success) {
            return String.format(
                    "BatchResult{id='%s', files=%d, success=true, time=%dms, " +
                    "successful=%d, failed=%d, bytes=%dMB, throughput=%.2fMB/s}",
                    batchId, files.size(), processingTimeMs, successfulFiles, failedFiles,
                    totalBytesProcessed / (1024 * 1024), getThroughputMBps()
            );
        } else {
            return String.format(
                    "BatchResult{id='%s', files=%d, success=false, time=%dms, error='%s'}",
                    batchId, files.size(), processingTimeMs, error != null ? error.getMessage() : "Unknown"
            );
        }
    }

    /**
     * Result of processing a single file within a batch.
     */
    public static class FileProcessingResult {
        public final Path file;
        public final boolean success;
        public final Exception error;
        public final long processingTimeMs;
        public final long fileSize;
        public final int chunkCount;
        public final String fileHash;

        public FileProcessingResult(Path file, boolean success, Exception error, long processingTimeMs,
                               long fileSize, int chunkCount, String fileHash) {
            this.file = file;
            this.success = success;
            this.error = error;
            this.processingTimeMs = processingTimeMs;
            this.fileSize = fileSize;
            this.chunkCount = chunkCount;
            this.fileHash = fileHash;
        }

        @Override
        public String toString() {
            if (success) {
                return String.format(
                        "FileResult{file=%s, success=true, time=%dms, size=%d, chunks=%d}",
                        file, processingTimeMs, fileSize, chunkCount
                );
            } else {
                return String.format(
                        "FileResult{file=%s, success=false, error='%s'}",
                        file, error != null ? error.getMessage() : "Unknown"
                );
            }
        }
    }
}