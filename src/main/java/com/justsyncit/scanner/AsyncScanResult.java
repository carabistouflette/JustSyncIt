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

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Enhanced result of an asynchronous filesystem scanning operation.
 * Extends ScanResult with additional async-specific metadata and capabilities.
 */
public class AsyncScanResult extends ScanResult {

    /** Unique identifier for this scan operation. */
    private final String scanId;

    /** Number of threads used for parallel scanning. */
    private final int threadCount;

    /** Average throughput in files per second. */
    private final double throughput;

    /** Peak memory usage during scanning. */
    private final long peakMemoryUsage;

    /** Number of directories scanned. */
    private final long directoriesScanned;

    /** Number of symbolic links encountered. */
    private final long symbolicLinksEncountered;

    /** Number of sparse files detected. */
    private final long sparseFilesDetected;

    /** Backpressure events encountered during scanning. */
    private final long backpressureEvents;

    /** Cancellation status. */
    private final boolean wasCancelled;

    /** Async-specific metadata. */
    private final Map<String, Object> asyncMetadata;

    /**
     * Creates a new AsyncScanResult.
     *
     * @param scanId                   unique identifier for this scan
     * @param rootDirectory            the root directory that was scanned
     * @param scannedFiles             list of successfully scanned files
     * @param errors                   list of errors that occurred
     * @param startTime                when the scan started
     * @param endTime                  when the scan completed
     * @param metadata                 additional scan metadata
     * @param threadCount              number of threads used
     * @param throughput               average throughput in files per second
     * @param peakMemoryUsage          peak memory usage in bytes
     * @param directoriesScanned       number of directories scanned
     * @param symbolicLinksEncountered number of symbolic links encountered
     * @param sparseFilesDetected      number of sparse files detected
     * @param backpressureEvents       backpressure events encountered
     * @param wasCancelled             whether the scan was cancelled
     * @param asyncMetadata            async-specific metadata
     */
    private AsyncScanResult(Builder builder) {
        super(builder.rootDirectory, builder.scannedFiles, builder.errors,
                builder.startTime, builder.endTime, builder.metadata);
        this.scanId = builder.scanId;
        this.threadCount = builder.threadCount;
        this.throughput = builder.throughput;
        this.peakMemoryUsage = builder.peakMemoryUsage;
        this.directoriesScanned = builder.directoriesScanned;
        this.symbolicLinksEncountered = builder.symbolicLinksEncountered;
        this.sparseFilesDetected = builder.sparseFilesDetected;
        this.backpressureEvents = builder.backpressureEvents;
        this.wasCancelled = builder.wasCancelled;
        this.asyncMetadata = builder.asyncMetadata != null ? new java.util.HashMap<>(builder.asyncMetadata) : null;
    }

    public static class Builder {
        private String scanId;
        private Path rootDirectory;
        private List<ScannedFile> scannedFiles;
        private List<ScanError> errors;
        private Instant startTime;
        private Instant endTime;
        private Map<String, Object> metadata;
        private int threadCount;
        private double throughput;
        private long peakMemoryUsage;
        private long directoriesScanned;
        private long symbolicLinksEncountered;
        private long sparseFilesDetected;
        private long backpressureEvents;
        private boolean wasCancelled;
        private Map<String, Object> asyncMetadata;

        public Builder setScanId(String scanId) {
            this.scanId = scanId;
            return this;
        }

        public Builder setRootDirectory(Path rootDirectory) {
            this.rootDirectory = rootDirectory;
            return this;
        }

        public Builder setScannedFiles(List<ScannedFile> scannedFiles) {
            this.scannedFiles = scannedFiles;
            return this;
        }

        public Builder setErrors(List<ScanError> errors) {
            this.errors = errors;
            return this;
        }

        public Builder setStartTime(Instant startTime) {
            this.startTime = startTime;
            return this;
        }

        public Builder setEndTime(Instant endTime) {
            this.endTime = endTime;
            return this;
        }

        public Builder setMetadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder setThreadCount(int threadCount) {
            this.threadCount = threadCount;
            return this;
        }

        public Builder setThroughput(double throughput) {
            this.throughput = throughput;
            return this;
        }

        public Builder setPeakMemoryUsage(long peakMemoryUsage) {
            this.peakMemoryUsage = peakMemoryUsage;
            return this;
        }

        public Builder setDirectoriesScanned(long directoriesScanned) {
            this.directoriesScanned = directoriesScanned;
            return this;
        }

        public Builder setSymbolicLinksEncountered(long symbolicLinksEncountered) {
            this.symbolicLinksEncountered = symbolicLinksEncountered;
            return this;
        }

        public Builder setSparseFilesDetected(long sparseFilesDetected) {
            this.sparseFilesDetected = sparseFilesDetected;
            return this;
        }

        public Builder setBackpressureEvents(long backpressureEvents) {
            this.backpressureEvents = backpressureEvents;
            return this;
        }

        public Builder setWasCancelled(boolean wasCancelled) {
            this.wasCancelled = wasCancelled;
            return this;
        }

        public Builder setAsyncMetadata(Map<String, Object> asyncMetadata) {
            this.asyncMetadata = asyncMetadata;
            return this;
        }

        public AsyncScanResult build() {
            return new AsyncScanResult(this);
        }
    }

    /**
     * Gets the unique identifier for this scan operation.
     *
     * @return the scan ID
     */
    public String getScanId() {
        return scanId;
    }

    /**
     * Gets the number of threads used for parallel scanning.
     *
     * @return the thread count
     */
    public int getThreadCount() {
        return threadCount;
    }

    /**
     * Gets the average throughput in files per second.
     *
     * @return the throughput
     */
    public double getThroughput() {
        return throughput;
    }

    /**
     * Gets the peak memory usage during scanning.
     *
     * @return the peak memory usage in bytes
     */
    public long getPeakMemoryUsage() {
        return peakMemoryUsage;
    }

    /**
     * Gets the number of directories scanned.
     *
     * @return the number of directories
     */
    public long getDirectoriesScanned() {
        return directoriesScanned;
    }

    /**
     * Gets the number of symbolic links encountered.
     *
     * @return the number of symbolic links
     */
    public long getSymbolicLinksEncountered() {
        return symbolicLinksEncountered;
    }

    /**
     * Gets the number of sparse files detected.
     *
     * @return the number of sparse files
     */
    public long getSparseFilesDetected() {
        return sparseFilesDetected;
    }

    /**
     * Gets the number of backpressure events encountered.
     *
     * @return the number of backpressure events
     */
    public long getBackpressureEvents() {
        return backpressureEvents;
    }

    /**
     * Checks if the scan was cancelled.
     *
     * @return true if cancelled, false otherwise
     */
    public boolean wasCancelled() {
        return wasCancelled;
    }

    /**
     * Gets async-specific metadata.
     *
     * @return the async metadata map
     */
    public Map<String, Object> getAsyncMetadata() {
        return asyncMetadata != null ? new java.util.HashMap<>(asyncMetadata) : null;
    }

    /**
     * Asynchronously exports this result to a different format.
     *
     * @param format the export format
     * @return a CompletableFuture that completes with the exported data
     */
    public CompletableFuture<String> exportAsync(String format) {
        return CompletableFuture.supplyAsync(() -> {
            switch (format.toLowerCase()) {
                case "json":
                    return toJson();
                case "csv":
                    return toCsv();
                case "xml":
                    return toXml();
                default:
                    return toString();
            }
        });
    }

    /**
     * Converts this result to JSON format.
     *
     * @return JSON representation
     */
    private String toJson() {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"scanId\": \"").append(scanId).append("\",\n");
        json.append("  \"rootDirectory\": \"").append(getRootDirectory()).append("\",\n");
        json.append("  \"startTime\": \"").append(getStartTime()).append("\",\n");
        json.append("  \"endTime\": \"").append(getEndTime()).append("\",\n");
        json.append("  \"durationMillis\": ").append(getDurationMillis()).append(",\n");
        json.append("  \"scannedFileCount\": ").append(getScannedFileCount()).append(",\n");
        json.append("  \"errorCount\": ").append(getErrorCount()).append(",\n");
        json.append("  \"totalSize\": ").append(getTotalSize()).append(",\n");
        json.append("  \"threadCount\": ").append(threadCount).append(",\n");
        json.append("  \"throughput\": ").append(throughput).append(",\n");
        json.append("  \"peakMemoryUsage\": ").append(peakMemoryUsage).append(",\n");
        json.append("  \"directoriesScanned\": ").append(directoriesScanned).append(",\n");
        json.append("  \"symbolicLinksEncountered\": ").append(symbolicLinksEncountered).append(",\n");
        json.append("  \"sparseFilesDetected\": ").append(sparseFilesDetected).append(",\n");
        json.append("  \"backpressureEvents\": ").append(backpressureEvents).append(",\n");
        json.append("  \"wasCancelled\": ").append(wasCancelled).append("\n");
        json.append("}");
        return json.toString();
    }

    /**
     * Converts this result to CSV format.
     *
     * @return CSV representation
     */
    private String toCsv() {
        StringBuilder csv = new StringBuilder();
        csv.append("scanId,rootDirectory,startTime,endTime,durationMillis,")
                .append("scannedFileCount,errorCount,totalSize,threadCount,throughput,")
                .append("peakMemoryUsage,directoriesScanned,symbolicLinksEncountered,")
                .append("sparseFilesDetected,backpressureEvents,wasCancelled\n");
        csv.append(scanId).append(",");
        csv.append(getRootDirectory()).append(",");
        csv.append(getStartTime()).append(",");
        csv.append(getEndTime()).append(",");
        csv.append(getDurationMillis()).append(",");
        csv.append(getScannedFileCount()).append(",");
        csv.append(getErrorCount()).append(",");
        csv.append(getTotalSize()).append(",");
        csv.append(threadCount).append(",");
        csv.append(throughput).append(",");
        csv.append(peakMemoryUsage).append(",");
        csv.append(directoriesScanned).append(",");
        csv.append(symbolicLinksEncountered).append(",");
        csv.append(sparseFilesDetected).append(",");
        csv.append(backpressureEvents).append(",");
        csv.append(wasCancelled);
        return csv.toString();
    }

    /**
     * Converts this result to XML format.
     *
     * @return XML representation
     */
    private String toXml() {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<AsyncScanResult>\n");
        xml.append("  <scanId>").append(scanId).append("</scanId>\n");
        xml.append("  <rootDirectory>").append(getRootDirectory()).append("</rootDirectory>\n");
        xml.append("  <startTime>").append(getStartTime()).append("</startTime>\n");
        xml.append("  <endTime>").append(getEndTime()).append("</endTime>\n");
        xml.append("  <durationMillis>").append(getDurationMillis()).append("</durationMillis>\n");
        xml.append("  <scannedFileCount>").append(getScannedFileCount()).append("</scannedFileCount>\n");
        xml.append("  <errorCount>").append(getErrorCount()).append("</errorCount>\n");
        xml.append("  <totalSize>").append(getTotalSize()).append("</totalSize>\n");
        xml.append("  <threadCount>").append(threadCount).append("</threadCount>\n");
        xml.append("  <throughput>").append(throughput).append("</throughput>\n");
        xml.append("  <peakMemoryUsage>").append(peakMemoryUsage).append("</peakMemoryUsage>\n");
        xml.append("  <directoriesScanned>").append(directoriesScanned).append("</directoriesScanned>\n");
        xml.append("  <symbolicLinksEncountered>").append(symbolicLinksEncountered)
                .append("</symbolicLinksEncountered>\n");
        xml.append("  <sparseFilesDetected>").append(sparseFilesDetected).append("</sparseFilesDetected>\n");
        xml.append("  <backpressureEvents>").append(backpressureEvents).append("</backpressureEvents>\n");
        xml.append("  <wasCancelled>").append(wasCancelled).append("</wasCancelled>\n");
        xml.append("</AsyncScanResult>");
        return xml.toString();
    }

    @Override
    public String toString() {
        return String.format(
                "AsyncScanResult{scanId='%s', rootDirectory=%s, scannedFiles=%d, errors=%d, "
                        + "duration=%dms, threadCount=%d, throughput=%.2f files/sec, peakMemory=%d bytes, "
                        + "directories=%d, symlinks=%d, sparseFiles=%d, backpressure=%d, cancelled=%b}",
                scanId, getRootDirectory(), getScannedFileCount(), getErrorCount(),
                getDurationMillis(), threadCount, throughput, peakMemoryUsage,
                directoriesScanned, symbolicLinksEncountered, sparseFilesDetected,
                backpressureEvents, wasCancelled);
    }
}