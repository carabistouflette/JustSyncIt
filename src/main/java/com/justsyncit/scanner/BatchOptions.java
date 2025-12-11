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

/**
 * Configuration options for batch processing operations.
 * Follows Builder pattern for flexible configuration with adaptive sizing,
 * priority scheduling, and resource management settings.
 */
public class BatchOptions {

    /** Default batch size. */
    private static final int DEFAULT_BATCH_SIZE = 50;
    /** Default maximum batch size. */
    private static final int DEFAULT_MAX_BATCH_SIZE = 1000;
    /** Default batch timeout in seconds. */
    private static final int DEFAULT_BATCH_TIMEOUT_SECONDS = 300;
    /** Default adaptive sizing enabled. */
    private static final boolean DEFAULT_ADAPTIVE_SIZING = true;
    /** Default priority scheduling enabled. */
    private static final boolean DEFAULT_PRIORITY_SCHEDULING = true;
    /** Default backpressure control enabled. */
    private static final boolean DEFAULT_BACKPRESSURE_CONTROL = true;
    /** Default chunk size for batch operations. */
    private static final int DEFAULT_CHUNK_SIZE = 64 * 1024;

    /** Number of files in batch. */
    private int batchSize = DEFAULT_BATCH_SIZE;
    
    /** Maximum number of files allowed in batch. */
    private int maxBatchSize = DEFAULT_MAX_BATCH_SIZE;
    
    /** Batch timeout in seconds. */
    private int batchTimeoutSeconds = DEFAULT_BATCH_TIMEOUT_SECONDS;
    
    /** Whether adaptive sizing is enabled. */
    private boolean adaptiveSizing = DEFAULT_ADAPTIVE_SIZING;
    
    /** Whether priority scheduling is enabled. */
    private boolean priorityScheduling = DEFAULT_PRIORITY_SCHEDULING;
    
    /** Whether backpressure control is enabled. */
    private boolean backpressureControl = DEFAULT_BACKPRESSURE_CONTROL;
    
    /** Chunk size for batch operations. */
    private int chunkSize = DEFAULT_CHUNK_SIZE;
    
    /** Maximum concurrent chunks per batch. */
    private int maxConcurrentChunks = 4;
    
    /** Batch processing strategy. */
    private BatchStrategy strategy = BatchStrategy.SIZE_BASED;
    
    /** Memory pressure threshold for batch operations. */
    private double memoryPressureThreshold = 0.8;
    
    /** CPU utilization threshold for batch operations. */
    private double cpuUtilizationThreshold = 0.9;

    /**
     * Creates a new BatchOptions with default settings.
     */
    public BatchOptions() {
        // Default constructor with sensible defaults
    }

    /**
     * Creates a new BatchOptions as a copy of existing options.
     *
     * @param other options to copy
     */
    public BatchOptions(BatchOptions other) {
        this.batchSize = other.batchSize;
        this.maxBatchSize = other.maxBatchSize;
        this.batchTimeoutSeconds = other.batchTimeoutSeconds;
        this.adaptiveSizing = other.adaptiveSizing;
        this.priorityScheduling = other.priorityScheduling;
        this.backpressureControl = other.backpressureControl;
        this.chunkSize = other.chunkSize;
        this.maxConcurrentChunks = other.maxConcurrentChunks;
        this.strategy = other.strategy;
        this.memoryPressureThreshold = other.memoryPressureThreshold;
        this.cpuUtilizationThreshold = other.cpuUtilizationThreshold;
    }

    /**
     * Sets the batch size.
     *
     * @param batchSize number of files in batch
     * @return this builder for method chaining
     * @throws IllegalArgumentException if batchSize is not positive
     */
    public BatchOptions withBatchSize(int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("Batch size must be positive");
        }
        this.batchSize = batchSize;
        return this;
    }

    /**
     * Sets the maximum batch size.
     *
     * @param maxBatchSize maximum number of files in batch
     * @return this builder for method chaining
     * @throws IllegalArgumentException if maxBatchSize is not positive
     */
    public BatchOptions withMaxBatchSize(int maxBatchSize) {
        if (maxBatchSize <= 0) {
            throw new IllegalArgumentException("Maximum batch size must be positive");
        }
        this.maxBatchSize = maxBatchSize;
        return this;
    }

    /**
     * Sets the batch timeout.
     *
     * @param batchTimeoutSeconds timeout in seconds
     * @return this builder for method chaining
     * @throws IllegalArgumentException if batchTimeoutSeconds is not positive
     */
    public BatchOptions withBatchTimeoutSeconds(int batchTimeoutSeconds) {
        if (batchTimeoutSeconds <= 0) {
            throw new IllegalArgumentException("Batch timeout must be positive");
        }
        this.batchTimeoutSeconds = batchTimeoutSeconds;
        return this;
    }

    /**
     * Sets whether adaptive sizing is enabled.
     *
     * @param adaptiveSizing true to enable adaptive sizing
     * @return this builder for method chaining
     */
    public BatchOptions withAdaptiveSizing(boolean adaptiveSizing) {
        this.adaptiveSizing = adaptiveSizing;
        return this;
    }

    /**
     * Sets whether priority scheduling is enabled.
     *
     * @param priorityScheduling true to enable priority scheduling
     * @return this builder for method chaining
     */
    public BatchOptions withPriorityScheduling(boolean priorityScheduling) {
        this.priorityScheduling = priorityScheduling;
        return this;
    }

    /**
     * Sets whether backpressure control is enabled.
     *
     * @param backpressureControl true to enable backpressure control
     * @return this builder for method chaining
     */
    public BatchOptions withBackpressureControl(boolean backpressureControl) {
        this.backpressureControl = backpressureControl;
        return this;
    }

    /**
     * Sets the chunk size for batch operations.
     *
     * @param chunkSize chunk size in bytes
     * @return this builder for method chaining
     * @throws IllegalArgumentException if chunkSize is not positive
     */
    public BatchOptions withChunkSize(int chunkSize) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("Chunk size must be positive");
        }
        this.chunkSize = chunkSize;
        return this;
    }

    /**
     * Sets the maximum concurrent chunks per batch.
     *
     * @param maxConcurrentChunks maximum concurrent chunks
     * @return this builder for method chaining
     * @throws IllegalArgumentException if maxConcurrentChunks is not positive
     */
    public BatchOptions withMaxConcurrentChunks(int maxConcurrentChunks) {
        if (maxConcurrentChunks <= 0) {
            throw new IllegalArgumentException("Maximum concurrent chunks must be positive");
        }
        this.maxConcurrentChunks = maxConcurrentChunks;
        return this;
    }

    /**
     * Sets the batch processing strategy.
     *
     * @param strategy batch processing strategy
     * @return this builder for method chaining
     * @throws IllegalArgumentException if strategy is null
     */
    public BatchOptions withStrategy(BatchStrategy strategy) {
        if (strategy == null) {
            throw new IllegalArgumentException("Strategy cannot be null");
        }
        this.strategy = strategy;
        return this;
    }

    /**
     * Sets the memory pressure threshold.
     *
     * @param memoryPressureThreshold memory pressure threshold (0.0 to 1.0)
     * @return this builder for method chaining
     * @throws IllegalArgumentException if threshold is outside valid range
     */
    public BatchOptions withMemoryPressureThreshold(double memoryPressureThreshold) {
        if (memoryPressureThreshold < 0.0 || memoryPressureThreshold > 1.0) {
            throw new IllegalArgumentException("Memory pressure threshold must be between 0.0 and 1.0");
        }
        this.memoryPressureThreshold = memoryPressureThreshold;
        return this;
    }

    /**
     * Sets the CPU utilization threshold.
     *
     * @param cpuUtilizationThreshold CPU utilization threshold (0.0 to 1.0)
     * @return this builder for method chaining
     * @throws IllegalArgumentException if threshold is outside valid range
     */
    public BatchOptions withCpuUtilizationThreshold(double cpuUtilizationThreshold) {
        if (cpuUtilizationThreshold < 0.0 || cpuUtilizationThreshold > 1.0) {
            throw new IllegalArgumentException("CPU utilization threshold must be between 0.0 and 1.0");
        }
        this.cpuUtilizationThreshold = cpuUtilizationThreshold;
        return this;
    }

    // Getters

    public int getBatchSize() {
        return batchSize;
    }

    public int getMaxBatchSize() {
        return maxBatchSize;
    }

    public int getBatchTimeoutSeconds() {
        return batchTimeoutSeconds;
    }

    public boolean isAdaptiveSizing() {
        return adaptiveSizing;
    }

    public boolean isPriorityScheduling() {
        return priorityScheduling;
    }

    public boolean isBackpressureControl() {
        return backpressureControl;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public int getMaxConcurrentChunks() {
        return maxConcurrentChunks;
    }

    public BatchStrategy getStrategy() {
        return strategy;
    }

    public double getMemoryPressureThreshold() {
        return memoryPressureThreshold;
    }

    public double getCpuUtilizationThreshold() {
        return cpuUtilizationThreshold;
    }

    @Override
    public String toString() {
        return "BatchOptions{" +
                "batchSize=" + batchSize +
                ", maxBatchSize=" + maxBatchSize +
                ", batchTimeoutSeconds=" + batchTimeoutSeconds +
                ", adaptiveSizing=" + adaptiveSizing +
                ", priorityScheduling=" + priorityScheduling +
                ", backpressureControl=" + backpressureControl +
                ", chunkSize=" + chunkSize +
                ", maxConcurrentChunks=" + maxConcurrentChunks +
                ", strategy=" + strategy +
                ", memoryPressureThreshold=" + memoryPressureThreshold +
                ", cpuUtilizationThreshold=" + cpuUtilizationThreshold +
                '}';
    }
}