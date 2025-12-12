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
 * Configuration for batch processing operations.
 * Provides comprehensive settings for adaptive sizing,
 * resource management, and performance optimization.
 */
public class BatchConfiguration {

    /** Default maximum batch size. */
    private static final int DEFAULT_MAX_BATCH_SIZE = 1000;
    /** Default minimum batch size. */
    private static final int DEFAULT_MIN_BATCH_SIZE = 1;
    /** Default adaptive sizing enabled. */
    private static final boolean DEFAULT_ADAPTIVE_SIZING = true;
    /** Default priority scheduling enabled. */
    private static final boolean DEFAULT_PRIORITY_SCHEDULING = true;
    /** Default backpressure control enabled. */
    private static final boolean DEFAULT_BACKPRESSURE_CONTROL = true;
    /** Default resource monitoring enabled. */
    private static final boolean DEFAULT_RESOURCE_MONITORING = true;
    /** Default performance optimization enabled. */
    private static final boolean DEFAULT_PERFORMANCE_OPTIMIZATION = true;
    /** Default maximum concurrent batches. */
    private static final int DEFAULT_MAX_CONCURRENT_BATCHES = 10;
    /** Default batch timeout in seconds. */
    private static final int DEFAULT_BATCH_TIMEOUT_SECONDS = 300;
    /** Default small buffer threshold in bytes. */
    private static final int DEFAULT_SMALL_BUFFER_THRESHOLD = 64 * 1024; // 64KB
    /** Default memory pressure threshold. */
    private static final double DEFAULT_MEMORY_PRESSURE_THRESHOLD = 0.8;
    /** Default CPU utilization threshold. */
    private static final double DEFAULT_CPU_UTILIZATION_THRESHOLD = 0.9;

    /** Maximum batch size allowed. */
    private int maxBatchSize = DEFAULT_MAX_BATCH_SIZE;

    /** Minimum batch size allowed. */
    private int minBatchSize = DEFAULT_MIN_BATCH_SIZE;

    /** Whether adaptive sizing is enabled. */
    private boolean adaptiveSizing = DEFAULT_ADAPTIVE_SIZING;

    /** Whether priority scheduling is enabled. */
    private boolean priorityScheduling = DEFAULT_PRIORITY_SCHEDULING;

    /** Whether backpressure control is enabled. */
    private boolean backpressureControl = DEFAULT_BACKPRESSURE_CONTROL;

    /** Whether resource monitoring is enabled. */
    private boolean resourceMonitoring = DEFAULT_RESOURCE_MONITORING;

    /** Whether performance optimization is enabled. */
    private boolean performanceOptimization = DEFAULT_PERFORMANCE_OPTIMIZATION;

    /** Maximum number of concurrent batches allowed. */
    private int maxConcurrentBatches = DEFAULT_MAX_CONCURRENT_BATCHES;

    /** Batch timeout in seconds. */
    private int batchTimeoutSeconds = DEFAULT_BATCH_TIMEOUT_SECONDS;

    /** Small buffer threshold in bytes. */
    private int smallBufferThreshold = DEFAULT_SMALL_BUFFER_THRESHOLD;

    /** Memory pressure threshold (0.0 to 1.0). */
    private double memoryPressureThreshold = DEFAULT_MEMORY_PRESSURE_THRESHOLD;

    /** CPU utilization threshold (0.0 to 1.0). */
    private double cpuUtilizationThreshold = DEFAULT_CPU_UTILIZATION_THRESHOLD;

    /** Default batch processing strategy. */
    private BatchStrategy defaultStrategy = BatchStrategy.SIZE_BASED;

    /** Performance target throughput in MB/s. */
    private double targetThroughputMBps = 100.0;

    /** Performance target latency in milliseconds. */
    private double targetLatencyMs = 100.0;

    /** Performance target efficiency percentage. */
    private double targetEfficiencyPercent = 80.0;

    /**
     * Creates a new BatchConfiguration with default settings.
     */
    public BatchConfiguration() {
        // Default constructor with sensible defaults
    }

    /**
     * Creates a new BatchConfiguration as a copy of existing configuration.
     *
     * @param other configuration to copy
     */
    public BatchConfiguration(BatchConfiguration other) {
        this.maxBatchSize = other.maxBatchSize;
        this.minBatchSize = other.minBatchSize;
        this.adaptiveSizing = other.adaptiveSizing;
        this.priorityScheduling = other.priorityScheduling;
        this.backpressureControl = other.backpressureControl;
        this.resourceMonitoring = other.resourceMonitoring;
        this.performanceOptimization = other.performanceOptimization;
        this.maxConcurrentBatches = other.maxConcurrentBatches;
        this.batchTimeoutSeconds = other.batchTimeoutSeconds;
        this.smallBufferThreshold = other.smallBufferThreshold;
        this.memoryPressureThreshold = other.memoryPressureThreshold;
        this.cpuUtilizationThreshold = other.cpuUtilizationThreshold;
        this.defaultStrategy = other.defaultStrategy;
        this.targetThroughputMBps = other.targetThroughputMBps;
        this.targetLatencyMs = other.targetLatencyMs;
        this.targetEfficiencyPercent = other.targetEfficiencyPercent;
    }

    /**
     * Sets the maximum batch size.
     *
     * @param maxBatchSize maximum batch size
     * @return this builder for method chaining
     * @throws IllegalArgumentException if maxBatchSize is not positive
     */
    public BatchConfiguration withMaxBatchSize(int maxBatchSize) {
        if (maxBatchSize <= 0) {
            throw new IllegalArgumentException("Maximum batch size must be positive");
        }
        this.maxBatchSize = maxBatchSize;
        return this;
    }

    /**
     * Sets the minimum batch size.
     *
     * @param minBatchSize minimum batch size
     * @return this builder for method chaining
     * @throws IllegalArgumentException if minBatchSize is not positive
     */
    public BatchConfiguration withMinBatchSize(int minBatchSize) {
        if (minBatchSize <= 0) {
            throw new IllegalArgumentException("Minimum batch size must be positive");
        }
        this.minBatchSize = minBatchSize;
        return this;
    }

    /**
     * Sets whether adaptive sizing is enabled.
     *
     * @param adaptiveSizing true to enable adaptive sizing
     * @return this builder for method chaining
     */
    public BatchConfiguration withAdaptiveSizing(boolean adaptiveSizing) {
        this.adaptiveSizing = adaptiveSizing;
        return this;
    }

    /**
     * Sets whether priority scheduling is enabled.
     *
     * @param priorityScheduling true to enable priority scheduling
     * @return this builder for method chaining
     */
    public BatchConfiguration withPriorityScheduling(boolean priorityScheduling) {
        this.priorityScheduling = priorityScheduling;
        return this;
    }

    /**
     * Sets whether backpressure control is enabled.
     *
     * @param backpressureControl true to enable backpressure control
     * @return this builder for method chaining
     */
    public BatchConfiguration withBackpressureControl(boolean backpressureControl) {
        this.backpressureControl = backpressureControl;
        return this;
    }

    /**
     * Sets whether resource monitoring is enabled.
     *
     * @param resourceMonitoring true to enable resource monitoring
     * @return this builder for method chaining
     */
    public BatchConfiguration withResourceMonitoring(boolean resourceMonitoring) {
        this.resourceMonitoring = resourceMonitoring;
        return this;
    }

    /**
     * Sets whether performance optimization is enabled.
     *
     * @param performanceOptimization true to enable performance optimization
     * @return this builder for method chaining
     */
    public BatchConfiguration withPerformanceOptimization(boolean performanceOptimization) {
        this.performanceOptimization = performanceOptimization;
        return this;
    }

    /**
     * Sets the maximum number of concurrent batches.
     *
     * @param maxConcurrentBatches maximum concurrent batches
     * @return this builder for method chaining
     * @throws IllegalArgumentException if maxConcurrentBatches is not positive
     */
    public BatchConfiguration withMaxConcurrentBatches(int maxConcurrentBatches) {
        if (maxConcurrentBatches <= 0) {
            throw new IllegalArgumentException("Maximum concurrent batches must be positive");
        }
        this.maxConcurrentBatches = maxConcurrentBatches;
        return this;
    }

    /**
     * Sets the batch timeout in seconds.
     *
     * @param batchTimeoutSeconds timeout in seconds
     * @return this builder for method chaining
     * @throws IllegalArgumentException if batchTimeoutSeconds is not positive
     */
    public BatchConfiguration withBatchTimeoutSeconds(int batchTimeoutSeconds) {
        if (batchTimeoutSeconds <= 0) {
            throw new IllegalArgumentException("Batch timeout must be positive");
        }
        this.batchTimeoutSeconds = batchTimeoutSeconds;
        return this;
    }

    /**
     * Sets the small buffer threshold.
     *
     * @param smallBufferThreshold small buffer threshold in bytes
     * @return this builder for method chaining
     * @throws IllegalArgumentException if smallBufferThreshold is not positive
     */
    public BatchConfiguration withSmallBufferThreshold(int smallBufferThreshold) {
        if (smallBufferThreshold <= 0) {
            throw new IllegalArgumentException("Small buffer threshold must be positive");
        }
        this.smallBufferThreshold = smallBufferThreshold;
        return this;
    }

    /**
     * Sets the memory pressure threshold.
     *
     * @param memoryPressureThreshold memory pressure threshold (0.0 to 1.0)
     * @return this builder for method chaining
     * @throws IllegalArgumentException if threshold is outside valid range
     */
    public BatchConfiguration withMemoryPressureThreshold(double memoryPressureThreshold) {
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
    public BatchConfiguration withCpuUtilizationThreshold(double cpuUtilizationThreshold) {
        if (cpuUtilizationThreshold < 0.0 || cpuUtilizationThreshold > 1.0) {
            throw new IllegalArgumentException("CPU utilization threshold must be between 0.0 and 1.0");
        }
        this.cpuUtilizationThreshold = cpuUtilizationThreshold;
        return this;
    }

    /**
     * Sets the default batch processing strategy.
     *
     * @param defaultStrategy default strategy
     * @return this builder for method chaining
     * @throws IllegalArgumentException if defaultStrategy is null
     */
    public BatchConfiguration withDefaultStrategy(BatchStrategy defaultStrategy) {
        if (defaultStrategy == null) {
            throw new IllegalArgumentException("Default strategy cannot be null");
        }
        this.defaultStrategy = defaultStrategy;
        return this;
    }

    /**
     * Sets the performance target throughput.
     *
     * @param targetThroughputMBps target throughput in MB/s
     * @return this builder for method chaining
     * @throws IllegalArgumentException if targetThroughputMBps is not positive
     */
    public BatchConfiguration withTargetThroughputMBps(double targetThroughputMBps) {
        if (targetThroughputMBps <= 0.0) {
            throw new IllegalArgumentException("Target throughput must be positive");
        }
        this.targetThroughputMBps = targetThroughputMBps;
        return this;
    }

    /**
     * Sets the performance target latency.
     *
     * @param targetLatencyMs target latency in milliseconds
     * @return this builder for method chaining
     * @throws IllegalArgumentException if targetLatencyMs is not positive
     */
    public BatchConfiguration withTargetLatencyMs(double targetLatencyMs) {
        if (targetLatencyMs <= 0.0) {
            throw new IllegalArgumentException("Target latency must be positive");
        }
        this.targetLatencyMs = targetLatencyMs;
        return this;
    }

    /**
     * Sets the performance target efficiency.
     *
     * @param targetEfficiencyPercent target efficiency percentage (0.0 to 100.0)
     * @return this builder for method chaining
     * @throws IllegalArgumentException if targetEfficiencyPercent is outside valid
     *                                  range
     */
    public BatchConfiguration withTargetEfficiencyPercent(double targetEfficiencyPercent) {
        if (targetEfficiencyPercent < 0.0 || targetEfficiencyPercent > 100.0) {
            throw new IllegalArgumentException("Target efficiency must be between 0.0 and 100.0");
        }
        this.targetEfficiencyPercent = targetEfficiencyPercent;
        return this;
    }

    // Getters

    public int getMaxBatchSize() {
        return maxBatchSize;
    }

    public int getMinBatchSize() {
        return minBatchSize;
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

    public boolean isResourceMonitoring() {
        return resourceMonitoring;
    }

    public boolean isPerformanceOptimization() {
        return performanceOptimization;
    }

    public int getMaxConcurrentBatches() {
        return maxConcurrentBatches;
    }

    public int getBatchTimeoutSeconds() {
        return batchTimeoutSeconds;
    }

    public int getSmallBufferThreshold() {
        return smallBufferThreshold;
    }

    public double getMemoryPressureThreshold() {
        return memoryPressureThreshold;
    }

    public double getCpuUtilizationThreshold() {
        return cpuUtilizationThreshold;
    }

    public BatchStrategy getDefaultStrategy() {
        return defaultStrategy;
    }

    public double getTargetThroughputMBps() {
        return targetThroughputMBps;
    }

    public double getTargetLatencyMs() {
        return targetLatencyMs;
    }

    public double getTargetEfficiencyPercent() {
        return targetEfficiencyPercent;
    }

    @Override
    public String toString() {
        return "BatchConfiguration{"
                + "maxBatchSize=" + maxBatchSize
                + ", minBatchSize=" + minBatchSize
                + ", adaptiveSizing=" + adaptiveSizing
                + ", priorityScheduling=" + priorityScheduling
                + ", backpressureControl=" + backpressureControl
                + ", resourceMonitoring=" + resourceMonitoring
                + ", performanceOptimization=" + performanceOptimization
                + ", maxConcurrentBatches=" + maxConcurrentBatches
                + ", batchTimeoutSeconds=" + batchTimeoutSeconds
                + ", smallBufferThreshold=" + smallBufferThreshold
                + ", memoryPressureThreshold=" + memoryPressureThreshold
                + ", cpuUtilizationThreshold=" + cpuUtilizationThreshold
                + ", defaultStrategy=" + defaultStrategy
                + ", targetThroughputMBps=" + targetThroughputMBps
                + ", targetLatencyMs=" + targetLatencyMs
                + ", targetEfficiencyPercent=" + targetEfficiencyPercent
                + '}';
    }
}