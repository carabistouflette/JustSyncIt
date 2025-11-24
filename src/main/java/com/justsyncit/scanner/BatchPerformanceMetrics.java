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
 * Performance metrics for batch processing operations.
 * Provides detailed performance measurements for monitoring
 * and optimization of batch processing efficiency.
 */
public class BatchPerformanceMetrics {

    /** Overall throughput in MB/s. */
    private final double throughputMBps;
    
    /** Average processing time per file in milliseconds. */
    private final double averageProcessingTimePerFileMs;
    
    /** Average processing time per batch in milliseconds. */
    private final double averageProcessingTimePerBatchMs;
    
    /** Peak memory usage in MB. */
    private final double peakMemoryUsageMB;
    
    /** Average memory usage in MB. */
    private final double averageMemoryUsageMB;
    
    /** CPU utilization percentage. */
    private final double cpuUtilizationPercent;
    
    /** I/O wait time percentage. */
    private final double ioWaitTimePercent;
    
    /** Cache hit rate percentage. */
    private final double cacheHitRatePercent;
    
    /** Batch efficiency percentage (0.0 to 100.0). */
    private final double batchEfficiencyPercent;
    
    /** Resource utilization score (0.0 to 1.0). */
    private final double resourceUtilizationScore;

    /**
     * Creates new batch performance metrics.
     *
     * @param throughputMBps throughput in MB/s
     * @param averageProcessingTimePerFileMs average processing time per file in ms
     * @param averageProcessingTimePerBatchMs average processing time per batch in ms
     * @param peakMemoryUsageMB peak memory usage in MB
     * @param averageMemoryUsageMB average memory usage in MB
     * @param cpuUtilizationPercent CPU utilization percentage
     * @param ioWaitTimePercent I/O wait time percentage
     * @param cacheHitRatePercent cache hit rate percentage
     * @param batchEfficiencyPercent batch efficiency percentage
     * @param resourceUtilizationScore resource utilization score
     */
    public BatchPerformanceMetrics(double throughputMBps, double averageProcessingTimePerFileMs,
                               double averageProcessingTimePerBatchMs, double peakMemoryUsageMB,
                               double averageMemoryUsageMB, double cpuUtilizationPercent,
                               double ioWaitTimePercent, double cacheHitRatePercent,
                               double batchEfficiencyPercent, double resourceUtilizationScore) {
        this.throughputMBps = throughputMBps;
        this.averageProcessingTimePerFileMs = averageProcessingTimePerFileMs;
        this.averageProcessingTimePerBatchMs = averageProcessingTimePerBatchMs;
        this.peakMemoryUsageMB = peakMemoryUsageMB;
        this.averageMemoryUsageMB = averageMemoryUsageMB;
        this.cpuUtilizationPercent = cpuUtilizationPercent;
        this.ioWaitTimePercent = ioWaitTimePercent;
        this.cacheHitRatePercent = cacheHitRatePercent;
        this.batchEfficiencyPercent = batchEfficiencyPercent;
        this.resourceUtilizationScore = resourceUtilizationScore;
    }

    /**
     * Gets the throughput in MB/s.
     *
     * @return throughput in MB/s
     */
    public double getThroughputMBps() {
        return throughputMBps;
    }

    /**
     * Gets the average processing time per file in milliseconds.
     *
     * @return average processing time per file
     */
    public double getAverageProcessingTimePerFileMs() {
        return averageProcessingTimePerFileMs;
    }

    /**
     * Gets the average processing time per batch in milliseconds.
     *
     * @return average processing time per batch
     */
    public double getAverageProcessingTimePerBatchMs() {
        return averageProcessingTimePerBatchMs;
    }

    /**
     * Gets the peak memory usage in MB.
     *
     * @return peak memory usage in MB
     */
    public double getPeakMemoryUsageMB() {
        return peakMemoryUsageMB;
    }

    /**
     * Gets the average memory usage in MB.
     *
     * @return average memory usage in MB
     */
    public double getAverageMemoryUsageMB() {
        return averageMemoryUsageMB;
    }

    /**
     * Gets the CPU utilization percentage.
     *
     * @return CPU utilization percentage
     */
    public double getCpuUtilizationPercent() {
        return cpuUtilizationPercent;
    }

    /**
     * Gets the I/O wait time percentage.
     *
     * @return I/O wait time percentage
     */
    public double getIoWaitTimePercent() {
        return ioWaitTimePercent;
    }

    /**
     * Gets the cache hit rate percentage.
     *
     * @return cache hit rate percentage
     */
    public double getCacheHitRatePercent() {
        return cacheHitRatePercent;
    }

    /**
     * Gets the batch efficiency percentage.
     *
     * @return batch efficiency percentage
     */
    public double getBatchEfficiencyPercent() {
        return batchEfficiencyPercent;
    }

    /**
     * Gets the resource utilization score.
     *
     * @return resource utilization score
     */
    public double getResourceUtilizationScore() {
        return resourceUtilizationScore;
    }

    /**
     * Checks if the performance is optimal.
     *
     * @return true if performance is optimal, false otherwise
     */
    public boolean isOptimal() {
        return throughputMBps >= 100.0 && // >100MB/s throughput
               averageProcessingTimePerFileMs <= 100.0 && // <100ms per file
               cpuUtilizationPercent <= 80.0 && // <=80% CPU usage
               batchEfficiencyPercent >= 80.0; // >=80% efficiency
    }

    /**
     * Checks if the performance is acceptable.
     *
     * @return true if performance is acceptable, false otherwise
     */
    public boolean isAcceptable() {
        return throughputMBps >= 50.0 && // >50MB/s throughput
               averageProcessingTimePerFileMs <= 500.0 && // <500ms per file
               cpuUtilizationPercent <= 90.0 && // <=90% CPU usage
               batchEfficiencyPercent >= 60.0; // >=60% efficiency
    }

    /**
     * Gets a performance grade.
     *
     * @return performance grade (A-F)
     */
    public PerformanceGrade getPerformanceGrade() {
        if (isOptimal()) {
            return PerformanceGrade.A;
        } else if (isAcceptable()) {
            return PerformanceGrade.B;
        } else if (throughputMBps >= 25.0 && batchEfficiencyPercent >= 40.0) {
            return PerformanceGrade.C;
        } else if (throughputMBps >= 10.0 && batchEfficiencyPercent >= 20.0) {
            return PerformanceGrade.D;
        } else {
            return PerformanceGrade.F;
        }
    }

    @Override
    public String toString() {
        return String.format(
                "BatchPerformanceMetrics{throughput=%.2fMB/s, avgFileTime=%.1fms, " +
                "avgBatchTime=%.1fms, peakMemory=%.1fMB, avgMemory=%.1fMB, " +
                "cpu=%.1f%%, ioWait=%.1f%%, cacheHit=%.1f%%, efficiency=%.1f%%, " +
                "resourceScore=%.3f, grade=%s}",
                throughputMBps, averageProcessingTimePerFileMs, averageProcessingTimePerBatchMs,
                peakMemoryUsageMB, averageMemoryUsageMB, cpuUtilizationPercent,
                ioWaitTimePercent, cacheHitRatePercent, batchEfficiencyPercent,
                resourceUtilizationScore, getPerformanceGrade()
        );
    }

    /**
     * Performance grade enumeration.
     */
    public enum PerformanceGrade {
        A("Excellent", "Optimal performance with high efficiency"),
        B("Good", "Acceptable performance with good efficiency"),
        C("Fair", "Moderate performance with some inefficiencies"),
        D("Poor", "Low performance with significant inefficiencies"),
        F("Failing", "Unacceptable performance requiring optimization");

        private final String grade;
        private final String description;

        PerformanceGrade(String grade, String description) {
            this.grade = grade;
            this.description = description;
        }

        public String getGrade() {
            return grade;
        }

        public String getDescription() {
            return description;
        }

        @Override
        public String toString() {
            return grade;
        }
    }
}