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

package com.justsyncit.performance.util;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility class for collecting and analyzing performance metrics.
 * Provides standardized measurement methods for various performance
 * characteristics.
 */
public class PerformanceMetrics {

    private final String benchmarkName;
    private final Instant startTime;
    private Instant endTime;
    private final Map<String, Object> metrics = new HashMap<>();
    private final List<Measurement> measurements = new ArrayList<>();
    private MemoryMeasurement memoryBefore;
    private MemoryMeasurement memoryAfter;
    private CpuMeasurement cpuBefore;
    private CpuMeasurement cpuAfter;

    /**
     * Creates a new performance metrics collector for a benchmark.
     *
     * @param benchmarkName name of the benchmark being measured
     */
    public PerformanceMetrics(String benchmarkName) {
        this.benchmarkName = benchmarkName;
        this.startTime = Instant.now();
        this.memoryBefore = measureMemory();
        this.cpuBefore = measureCpu();
    }

    /**
     * Records a custom metric value.
     *
     * @param key   metric name
     * @param value metric value
     */
    public void recordMetric(String key, Object value) {
        metrics.put(key, value);
    }

    /**
     * Records a measurement with timestamp.
     *
     * @param name  measurement name
     * @param value measurement value
     * @param unit  measurement unit
     */
    public void recordMeasurement(String name, double value, String unit) {
        measurements.add(new Measurement(name, value, unit, Instant.now()));
    }

    /**
     * Records throughput measurement.
     *
     * @param bytesProcessed number of bytes processed
     * @param durationMs     duration in milliseconds
     */
    public void recordThroughput(long bytesProcessed, long durationMs) {
        double throughputMBps = calculateThroughputMBps(bytesProcessed, durationMs);
        recordMetric("throughput_mbps", throughputMBps);
        recordMetric("bytes_processed", bytesProcessed);
        recordMetric("duration_ms", durationMs);
        recordMeasurement("throughput", throughputMBps, "MB/s");
    }

    /**
     * Records operation rate measurement.
     *
     * @param operations    number of operations completed
     * @param durationMs    duration in milliseconds
     * @param operationType type of operation (e.g., "files", "chunks")
     */
    public void recordOperationRate(long operations, long durationMs, String operationType) {
        double opsPerSecond = calculateOperationsPerSecond(operations, durationMs);
        String metricKey = operationType + "_per_second";
        recordMetric(metricKey, opsPerSecond);
        recordMetric("total_" + operationType, operations);
        recordMeasurement(operationType + "_rate", opsPerSecond, "ops/s");
    }

    /**
     * Records deduplication efficiency metrics.
     *
     * @param totalFiles   total number of files
     * @param totalChunks  total number of chunks created
     * @param originalSize original data size in bytes
     * @param storedSize   stored size after deduplication in bytes
     */
    public void recordDeduplicationEfficiency(int totalFiles, int totalChunks,
            long originalSize, long storedSize) {
        double deduplicationRatio = calculateDeduplicationRatio(originalSize, storedSize);
        double chunksPerFile = (double) totalChunks / totalFiles;
        double spaceSavingsPercent = calculateSpaceSavingsPercent(originalSize, storedSize);

        recordMetric("total_files", totalFiles);
        recordMetric("total_chunks", totalChunks);
        recordMetric("original_size_bytes", originalSize);
        recordMetric("stored_size_bytes", storedSize);
        recordMetric("deduplication_ratio", deduplicationRatio);
        recordMetric("chunks_per_file", chunksPerFile);
        recordMetric("space_savings_percent", spaceSavingsPercent);

        recordMeasurement("deduplication_ratio", deduplicationRatio, "x");
        recordMeasurement("space_savings", spaceSavingsPercent, "%");
    }

    /**
     * Records network performance metrics.
     *
     * @param bytesTransferred number of bytes transferred
     * @param durationMs       duration in milliseconds
     * @param packetsLost      number of packets lost (if applicable)
     * @param latencyMs        average latency in milliseconds
     */
    public void recordNetworkPerformance(long bytesTransferred, long durationMs,
            int packetsLost, double latencyMs) {
        double throughputMBps = calculateThroughputMBps(bytesTransferred, durationMs);
        double packetLossPercent = packetsLost > 0 ? (packetsLost * 100.0 / (packetsLost + 1000)) : 0.0;

        recordMetric("network_throughput_mbps", throughputMBps);
        recordMetric("network_latency_ms", latencyMs);
        recordMetric("packets_lost", packetsLost);
        recordMetric("packet_loss_percent", packetLossPercent);

        recordMeasurement("network_throughput", throughputMBps, "MB/s");
        recordMeasurement("network_latency", latencyMs, "ms");
        if (packetLossPercent > 0) {
            recordMeasurement("packet_loss", packetLossPercent, "%");
        }
    }

    /**
     * Finalizes the metrics collection and calculates resource usage.
     */
    public void finalizeMetrics() {
        this.endTime = Instant.now();
        this.memoryAfter = measureMemory();
        this.cpuAfter = measureCpu();

        // Calculate resource usage
        long memoryUsed = memoryAfter.usedBytes - memoryBefore.usedBytes;
        long maxMemoryUsed = memoryAfter.maxBytes - memoryBefore.maxBytes;
        double cpuUsage = cpuAfter.usagePercent - cpuBefore.usagePercent;

        recordMetric("total_duration_ms", getDurationMs());
        recordMetric("memory_used_mb", bytesToMB(memoryUsed));
        recordMetric("max_memory_used_mb", bytesToMB(maxMemoryUsed));
        recordMetric("cpu_usage_percent", cpuUsage);

        recordMeasurement("memory_used", bytesToMB(memoryUsed), "MB");
        recordMeasurement("cpu_usage", cpuUsage, "%");
    }

    /**
     * Gets the total duration of the benchmark in milliseconds.
     */
    public long getDurationMs() {
        Instant end = endTime != null ? endTime : Instant.now();
        return Duration.between(startTime, end).toMillis();
    }

    /**
     * Gets the benchmark name.
     */
    public String getBenchmarkName() {
        return benchmarkName;
    }

    /**
     * Gets all recorded metrics.
     */
    public Map<String, Object> getMetrics() {
        return new HashMap<>(metrics);
    }

    /**
     * Gets all measurements with timestamps.
     */
    public List<Measurement> getMeasurements() {
        return new ArrayList<>(measurements);
    }

    /**
     * Gets memory usage information.
     */
    public MemoryUsage getMemoryUsage() {
        return new MemoryUsage(memoryBefore, memoryAfter);
    }

    /**
     * Gets CPU usage information.
     */
    public CpuUsage getCpuUsage() {
        return new CpuUsage(cpuBefore, cpuAfter);
    }

    /**
     * Generates a summary report of the metrics.
     */
    public String generateSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== ").append(benchmarkName).append(" Performance Summary ===\n");
        sb.append("Duration: ").append(getDurationMs()).append(" ms\n");

        if (metrics.containsKey("throughput_mbps")) {
            sb.append("Throughput: ").append(String.format("%.2f", metrics.get("throughput_mbps"))).append(" MB/s\n");
        }

        if (metrics.containsKey("memory_used_mb")) {
            sb.append("Memory Used: ").append(String.format("%.2f", metrics.get("memory_used_mb"))).append(" MB\n");
        }

        if (metrics.containsKey("deduplication_ratio")) {
            sb.append("Deduplication Ratio: ").append(String.format("%.2f", metrics.get("deduplication_ratio")))
                    .append("x\n");
        }

        if (metrics.containsKey("space_savings_percent")) {
            sb.append("Space Savings: ").append(String.format("%.1f", metrics.get("space_savings_percent")))
                    .append("%\n");
        }

        return sb.toString();
    }

    // Utility methods for calculations
    private double calculateThroughputMBps(long bytes, long durationMs) {
        if (durationMs == 0) {
            return 0;
        }
        return (bytes / 1024.0 / 1024.0) / (durationMs / 1000.0);
    }

    private double calculateOperationsPerSecond(long operations, long durationMs) {
        if (durationMs == 0) {
            return 0;
        }
        return operations * 1000.0 / durationMs;
    }

    private double calculateDeduplicationRatio(long originalSize, long storedSize) {
        if (storedSize == 0) {
            return 0;
        }
        return (double) originalSize / storedSize;
    }

    private double calculateSpaceSavingsPercent(long originalSize, long storedSize) {
        if (originalSize == 0) {
            return 0;
        }
        return (1.0 - (double) storedSize / originalSize) * 100.0;
    }

    private double bytesToMB(long bytes) {
        return bytes / 1024.0 / 1024.0;
    }

    private MemoryMeasurement measureMemory() {
        Runtime runtime = Runtime.getRuntime();
        long totalBytes = runtime.totalMemory();
        long freeBytes = runtime.freeMemory();
        long usedBytes = totalBytes - freeBytes;
        long maxBytes = runtime.maxMemory();
        return new MemoryMeasurement(totalBytes, freeBytes, usedBytes, maxBytes);
    }

    private CpuMeasurement measureCpu() {
        // Simplified CPU measurement - in a real implementation,
        // you might use OS-specific APIs or libraries like OSHI
        try {
            OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
            double usagePercent = 0.0;

            // Try to get more detailed CPU metrics if available
            try {
                com.sun.management.OperatingSystemMXBean sunOsBean = (com.sun.management.OperatingSystemMXBean) osBean;
                usagePercent = sunOsBean.getProcessCpuLoad() * 100.0;
                if (usagePercent < 0) {
                    usagePercent = 0.0; // Can return -1 on some systems
                }
            } catch (ClassCastException e) {
                // Fallback to system CPU load
                usagePercent = osBean.getSystemLoadAverage();
                if (usagePercent < 0) {
                    usagePercent = 0.0;
                }
            }

            return new CpuMeasurement(usagePercent);
        } catch (Exception e) {
            // If CPU measurement fails, return 0
            return new CpuMeasurement(0.0);
        }
    }

    /**
     * Represents a single measurement with timestamp.
     */
    public static class Measurement {
        private final String name;
        private final double value;
        private final String unit;
        private final Instant timestamp;

        public Measurement(String name, double value, String unit, Instant timestamp) {
            this.name = name;
            this.value = value;
            this.unit = unit;
            this.timestamp = timestamp;
        }

        public String getName() {
            return name;
        }

        public double getValue() {
            return value;
        }

        public String getUnit() {
            return unit;
        }

        public Instant getTimestamp() {
            return timestamp;
        }

        @Override
        public String toString() {
            return String.format("%s: %.2f %s @ %s", name, value, unit, timestamp);
        }
    }

    /**
     * Memory usage measurement at a point in time.
     */
    public static class MemoryMeasurement {
        private final long totalBytes;
        private final long freeBytes;
        private final long usedBytes;
        private final long maxBytes;

        public MemoryMeasurement(long totalBytes, long freeBytes, long usedBytes, long maxBytes) {
            this.totalBytes = totalBytes;
            this.freeBytes = freeBytes;
            this.usedBytes = usedBytes;
            this.maxBytes = maxBytes;
        }

        public long getTotalBytes() {
            return totalBytes;
        }

        public long getFreeBytes() {
            return freeBytes;
        }

        public long getUsedBytes() {
            return usedBytes;
        }

        public long getMaxBytes() {
            return maxBytes;
        }
    }

    /**
     * CPU usage measurement at a point in time.
     */
    public static class CpuMeasurement {
        private final double usagePercent;

        public CpuMeasurement(double usagePercent) {
            this.usagePercent = usagePercent;
        }

        public double getUsagePercent() {
            return usagePercent;
        }
    }

    /**
     * Memory usage comparison between before and after.
     */
    public static class MemoryUsage {
        private final MemoryMeasurement before;
        private final MemoryMeasurement after;

        public MemoryUsage(MemoryMeasurement before, MemoryMeasurement after) {
            this.before = before;
            this.after = after;
        }

        public long getMemoryUsed() {
            return after.usedBytes - before.usedBytes;
        }

        public long getMaxMemoryUsed() {
            return after.maxBytes - before.maxBytes;
        }

        public MemoryMeasurement getBefore() {
            return before;
        }

        public MemoryMeasurement getAfter() {
            return after;
        }
    }

    /**
     * CPU usage comparison between before and after.
     */
    public static class CpuUsage {
        private final CpuMeasurement before;
        private final CpuMeasurement after;

        public CpuUsage(CpuMeasurement before, CpuMeasurement after) {
            this.before = before;
            this.after = after;
        }

        public double getCpuUsed() {
            return after.usagePercent - before.usagePercent;
        }

        public CpuMeasurement getBefore() {
            return before;
        }

        public CpuMeasurement getAfter() {
            return after;
        }
    }
}