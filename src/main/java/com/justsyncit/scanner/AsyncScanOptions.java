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
import java.nio.file.PathMatcher;
import java.nio.file.WatchEvent;
import java.util.Set;
import java.util.EnumSet;
import java.util.concurrent.TimeUnit;

/**
 * Enhanced configuration options for asynchronous filesystem scanning operations.
 * Extends ScanOptions with async-specific settings for WatchService and performance tuning.
 */
public class AsyncScanOptions extends ScanOptions {

    /** Default parallelism level for directory scanning. */
    private static final int DEFAULT_PARALLELISM = Runtime.getRuntime().availableProcessors();
    
    /** Default batch size for file processing. */
    private static final int DEFAULT_BATCH_SIZE = 100;
    
    /** Default backpressure threshold. */
    private static final double DEFAULT_BACKPRESSURE_THRESHOLD = 0.8;

    /** Level of parallelism for directory scanning. */
    private int parallelism = DEFAULT_PARALLELISM;
    
    /** Batch size for file processing. */
    private int batchSize = DEFAULT_BATCH_SIZE;
    
    /** Whether to enable streaming mode for large directories. */
    private boolean streamingEnabled = false;
    
    /** Whether to enable real-time monitoring with WatchService. */
    private boolean watchServiceEnabled = false;
    
    /** Types of file events to watch for. */
    private Set<String> watchEventKinds = java.util.Set.of(
        "ENTRY_CREATE",
        "ENTRY_MODIFY",
        "ENTRY_DELETE"
    );
    
    /** Whether to watch recursively (subdirectories). */
    private boolean recursiveWatching = true;
    
    /** Event batching timeout in milliseconds. */
    private long eventBatchTimeoutMs = 100;
    
    /** Maximum number of events to batch together. */
    private int maxEventBatchSize = 50;
    
    /** Whether to enable event debouncing. */
    private boolean eventDebouncingEnabled = true;
    
    /** Debounce timeout in milliseconds. */
    private long debounceTimeoutMs = 500;
    
    /** Backpressure threshold (0.0 to 1.0). */
    private double backpressureThreshold = DEFAULT_BACKPRESSURE_THRESHOLD;
    
    /** Whether to enable adaptive sizing. */
    private boolean adaptiveSizingEnabled = true;
    
    /** Memory limit for scanning operations in bytes. */
    private long memoryLimitBytes = Runtime.getRuntime().maxMemory() / 4; // 25% of max memory
    
    /** Maximum number of concurrent file operations. */
    private int maxConcurrentFileOps = DEFAULT_PARALLELISM * 2;
    
    /** Timeout for individual file operations. */
    private long fileOperationTimeoutMs = 30000; // 30 seconds
    
    /** Whether to enable prefetching. */
    private boolean prefetchingEnabled = true;
    
    /** Prefetch depth for directory structures. */
    private int prefetchDepth = 2;
    
    /** Whether to enable NUMA awareness. */
    private boolean numaAwareEnabled = false;
    
    /** Whether to enable zero-copy operations. */
    private boolean zeroCopyEnabled = true;
    
    /** Whether to enable progress monitoring. */
    private boolean progressMonitoringEnabled = true;
    
    /** Progress update interval in milliseconds. */
    private long progressUpdateIntervalMs = 1000;

    /**
     * Creates a new AsyncScanOptions with default values.
     */
    public AsyncScanOptions() {
        super();
    }

    /**
     * Creates a new AsyncScanOptions as a copy of another.
     *
     * @param other options to copy
     */
    public AsyncScanOptions(AsyncScanOptions other) {
        super(other);
        this.parallelism = other.parallelism;
        this.batchSize = other.batchSize;
        this.streamingEnabled = other.streamingEnabled;
        this.watchServiceEnabled = other.watchServiceEnabled;
        this.watchEventKinds = new java.util.HashSet<>(other.watchEventKinds);
        this.recursiveWatching = other.recursiveWatching;
        this.eventBatchTimeoutMs = other.eventBatchTimeoutMs;
        this.maxEventBatchSize = other.maxEventBatchSize;
        this.eventDebouncingEnabled = other.eventDebouncingEnabled;
        this.debounceTimeoutMs = other.debounceTimeoutMs;
        this.backpressureThreshold = other.backpressureThreshold;
        this.adaptiveSizingEnabled = other.adaptiveSizingEnabled;
        this.memoryLimitBytes = other.memoryLimitBytes;
        this.maxConcurrentFileOps = other.maxConcurrentFileOps;
        this.fileOperationTimeoutMs = other.fileOperationTimeoutMs;
        this.prefetchingEnabled = other.prefetchingEnabled;
        this.prefetchDepth = other.prefetchDepth;
        this.numaAwareEnabled = other.numaAwareEnabled;
        this.zeroCopyEnabled = other.zeroCopyEnabled;
        this.progressMonitoringEnabled = other.progressMonitoringEnabled;
        this.progressUpdateIntervalMs = other.progressUpdateIntervalMs;
    }

    /**
     * Creates a new AsyncScanOptions from existing ScanOptions.
     *
     * @param baseOptions the base ScanOptions to extend
     */
    public AsyncScanOptions(ScanOptions baseOptions) {
        super(baseOptions);
    }

    // Builder pattern methods

    /**
     * Sets the level of parallelism for directory scanning.
     *
     * @param parallelism the parallelism level
     * @return this builder for method chaining
     */
    public AsyncScanOptions withParallelism(int parallelism) {
        if (parallelism <= 0) {
            throw new IllegalArgumentException("Parallelism must be positive");
        }
        this.parallelism = parallelism;
        return this;
    }

    /**
     * Sets the batch size for file processing.
     *
     * @param batchSize the batch size
     * @return this builder for method chaining
     */
    public AsyncScanOptions withBatchSize(int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("Batch size must be positive");
        }
        this.batchSize = batchSize;
        return this;
    }

    /**
     * Sets whether to enable streaming mode for large directories.
     *
     * @param streamingEnabled whether to enable streaming
     * @return this builder for method chaining
     */
    public AsyncScanOptions withStreamingEnabled(boolean streamingEnabled) {
        this.streamingEnabled = streamingEnabled;
        return this;
    }

    /**
     * Sets whether to enable real-time monitoring with WatchService.
     *
     * @param watchServiceEnabled whether to enable WatchService
     * @return this builder for method chaining
     */
    public AsyncScanOptions withWatchServiceEnabled(boolean watchServiceEnabled) {
        this.watchServiceEnabled = watchServiceEnabled;
        return this;
    }

    /**
     * Sets the types of file events to watch for.
     *
     * @param watchEventKinds the event kinds to watch
     * @return this builder for method chaining
     */
    public AsyncScanOptions withWatchEventKinds(Set<String> watchEventKinds) {
        this.watchEventKinds = watchEventKinds != null ? new java.util.HashSet<>(watchEventKinds) : new java.util.HashSet<>();
        return this;
    }

    /**
     * Sets whether to watch recursively (subdirectories).
     *
     * @param recursiveWatching whether to watch recursively
     * @return this builder for method chaining
     */
    public AsyncScanOptions withRecursiveWatching(boolean recursiveWatching) {
        this.recursiveWatching = recursiveWatching;
        return this;
    }

    /**
     * Sets the event batching timeout.
     *
     * @param eventBatchTimeoutMs the timeout in milliseconds
     * @return this builder for method chaining
     */
    public AsyncScanOptions withEventBatchTimeoutMs(long eventBatchTimeoutMs) {
        if (eventBatchTimeoutMs < 0) {
            throw new IllegalArgumentException("Event batch timeout cannot be negative");
        }
        this.eventBatchTimeoutMs = eventBatchTimeoutMs;
        return this;
    }

    /**
     * Sets the maximum number of events to batch together.
     *
     * @param maxEventBatchSize the maximum batch size
     * @return this builder for method chaining
     */
    public AsyncScanOptions withMaxEventBatchSize(int maxEventBatchSize) {
        if (maxEventBatchSize <= 0) {
            throw new IllegalArgumentException("Max event batch size must be positive");
        }
        this.maxEventBatchSize = maxEventBatchSize;
        return this;
    }

    /**
     * Sets whether to enable event debouncing.
     *
     * @param eventDebouncingEnabled whether to enable debouncing
     * @return this builder for method chaining
     */
    public AsyncScanOptions withEventDebouncingEnabled(boolean eventDebouncingEnabled) {
        this.eventDebouncingEnabled = eventDebouncingEnabled;
        return this;
    }

    /**
     * Sets the debounce timeout.
     *
     * @param debounceTimeoutMs the timeout in milliseconds
     * @return this builder for method chaining
     */
    public AsyncScanOptions withDebounceTimeoutMs(long debounceTimeoutMs) {
        if (debounceTimeoutMs < 0) {
            throw new IllegalArgumentException("Debounce timeout cannot be negative");
        }
        this.debounceTimeoutMs = debounceTimeoutMs;
        return this;
    }

    /**
     * Sets the backpressure threshold.
     *
     * @param backpressureThreshold the threshold (0.0 to 1.0)
     * @return this builder for method chaining
     */
    public AsyncScanOptions withBackpressureThreshold(double backpressureThreshold) {
        if (backpressureThreshold < 0.0 || backpressureThreshold > 1.0) {
            throw new IllegalArgumentException("Backpressure threshold must be between 0.0 and 1.0");
        }
        this.backpressureThreshold = backpressureThreshold;
        return this;
    }

    /**
     * Sets whether to enable adaptive sizing.
     *
     * @param adaptiveSizingEnabled whether to enable adaptive sizing
     * @return this builder for method chaining
     */
    public AsyncScanOptions withAdaptiveSizingEnabled(boolean adaptiveSizingEnabled) {
        this.adaptiveSizingEnabled = adaptiveSizingEnabled;
        return this;
    }

    /**
     * Sets the memory limit for scanning operations.
     *
     * @param memoryLimitBytes the memory limit in bytes
     * @return this builder for method chaining
     */
    public AsyncScanOptions withMemoryLimitBytes(long memoryLimitBytes) {
        if (memoryLimitBytes <= 0) {
            throw new IllegalArgumentException("Memory limit must be positive");
        }
        this.memoryLimitBytes = memoryLimitBytes;
        return this;
    }

    /**
     * Sets the maximum number of concurrent file operations.
     *
     * @param maxConcurrentFileOps the maximum concurrent operations
     * @return this builder for method chaining
     */
    public AsyncScanOptions withMaxConcurrentFileOps(int maxConcurrentFileOps) {
        if (maxConcurrentFileOps <= 0) {
            throw new IllegalArgumentException("Max concurrent file ops must be positive");
        }
        this.maxConcurrentFileOps = maxConcurrentFileOps;
        return this;
    }

    /**
     * Sets the timeout for individual file operations.
     *
     * @param fileOperationTimeoutMs the timeout in milliseconds
     * @return this builder for method chaining
     */
    public AsyncScanOptions withFileOperationTimeoutMs(long fileOperationTimeoutMs) {
        if (fileOperationTimeoutMs <= 0) {
            throw new IllegalArgumentException("File operation timeout must be positive");
        }
        this.fileOperationTimeoutMs = fileOperationTimeoutMs;
        return this;
    }

    /**
     * Sets whether to enable prefetching.
     *
     * @param prefetchingEnabled whether to enable prefetching
     * @return this builder for method chaining
     */
    public AsyncScanOptions withPrefetchingEnabled(boolean prefetchingEnabled) {
        this.prefetchingEnabled = prefetchingEnabled;
        return this;
    }

    /**
     * Sets the prefetch depth for directory structures.
     *
     * @param prefetchDepth the prefetch depth
     * @return this builder for method chaining
     */
    public AsyncScanOptions withPrefetchDepth(int prefetchDepth) {
        if (prefetchDepth < 0) {
            throw new IllegalArgumentException("Prefetch depth cannot be negative");
        }
        this.prefetchDepth = prefetchDepth;
        return this;
    }

    /**
     * Sets whether to enable NUMA awareness.
     *
     * @param numaAwareEnabled whether to enable NUMA awareness
     * @return this builder for method chaining
     */
    public AsyncScanOptions withNumaAwareEnabled(boolean numaAwareEnabled) {
        this.numaAwareEnabled = numaAwareEnabled;
        return this;
    }

    /**
     * Sets whether to enable zero-copy operations.
     *
     * @param zeroCopyEnabled whether to enable zero-copy
     * @return this builder for method chaining
     */
    public AsyncScanOptions withZeroCopyEnabled(boolean zeroCopyEnabled) {
        this.zeroCopyEnabled = zeroCopyEnabled;
        return this;
    }

    /**
     * Sets whether to enable progress monitoring.
     *
     * @param progressMonitoringEnabled whether to enable progress monitoring
     * @return this builder for method chaining
     */
    public AsyncScanOptions withProgressMonitoringEnabled(boolean progressMonitoringEnabled) {
        this.progressMonitoringEnabled = progressMonitoringEnabled;
        return this;
    }

    /**
     * Sets the progress update interval.
     *
     * @param progressUpdateIntervalMs the interval in milliseconds
     * @return this builder for method chaining
     */
    public AsyncScanOptions withProgressUpdateIntervalMs(long progressUpdateIntervalMs) {
        if (progressUpdateIntervalMs <= 0) {
            throw new IllegalArgumentException("Progress update interval must be positive");
        }
        this.progressUpdateIntervalMs = progressUpdateIntervalMs;
        return this;
    }

    // Getters

    public int getParallelism() {
        return parallelism;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public boolean isStreamingEnabled() {
        return streamingEnabled;
    }

    public boolean isWatchServiceEnabled() {
        return watchServiceEnabled;
    }

    public Set<String> getWatchEventKinds() {
        return new java.util.HashSet<>(watchEventKinds);
    }

    public boolean isRecursiveWatching() {
        return recursiveWatching;
    }

    public long getEventBatchTimeoutMs() {
        return eventBatchTimeoutMs;
    }

    public int getMaxEventBatchSize() {
        return maxEventBatchSize;
    }

    public boolean isEventDebouncingEnabled() {
        return eventDebouncingEnabled;
    }

    public long getDebounceTimeoutMs() {
        return debounceTimeoutMs;
    }

    public double getBackpressureThreshold() {
        return backpressureThreshold;
    }

    public boolean isAdaptiveSizingEnabled() {
        return adaptiveSizingEnabled;
    }

    public long getMemoryLimitBytes() {
        return memoryLimitBytes;
    }

    public int getMaxConcurrentFileOps() {
        return maxConcurrentFileOps;
    }

    public long getFileOperationTimeoutMs() {
        return fileOperationTimeoutMs;
    }

    public boolean isPrefetchingEnabled() {
        return prefetchingEnabled;
    }

    public int getPrefetchDepth() {
        return prefetchDepth;
    }

    public boolean isNumaAwareEnabled() {
        return numaAwareEnabled;
    }

    public boolean isZeroCopyEnabled() {
        return zeroCopyEnabled;
    }

    public boolean isProgressMonitoringEnabled() {
        return progressMonitoringEnabled;
    }

    public long getProgressUpdateIntervalMs() {
        return progressUpdateIntervalMs;
    }

    @Override
    public String toString() {
        return String.format(
            "AsyncScanOptions{parallelism=%d, batchSize=%d, streaming=%b, watchService=%b, " +
            "recursiveWatching=%b, eventDebouncing=%b, backpressureThreshold=%.2f, " +
            "adaptiveSizing=%b, memoryLimit=%d bytes, maxConcurrentFileOps=%d, " +
            "prefetching=%b, numaAware=%b, zeroCopy=%b, progressMonitoring=%b}",
            parallelism, batchSize, streamingEnabled, watchServiceEnabled,
            recursiveWatching, eventDebouncingEnabled, backpressureThreshold,
            adaptiveSizingEnabled, memoryLimitBytes, maxConcurrentFileOps,
            prefetchingEnabled, numaAwareEnabled, zeroCopyEnabled, progressMonitoringEnabled
        );
    }
}