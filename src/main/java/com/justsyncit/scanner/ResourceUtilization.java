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
 * Resource utilization information for batch operations.
 * Provides detailed metrics about CPU, memory, and I/O usage
 * during batch processing operations.
 */
public class ResourceUtilization {

    /** CPU utilization percentage. */
    public final double cpuUtilizationPercent;
    
    /** Memory utilization percentage. */
    public final double memoryUtilizationPercent;
    
    /** I/O utilization percentage. */
    public final double ioUtilizationPercent;
    
    /** Maximum concurrent operations. */
    public final int maxConcurrentOperations;
    
    /** Peak memory usage in MB. */
    public final long peakMemoryUsageMB;
    
    /** Total bytes read. */
    public final long totalBytesRead;
    
    /** Total bytes written. */
    public final long totalBytesWritten;

    /**
     * Creates resource utilization information.
     *
     * @param cpuUtilizationPercent CPU utilization percentage
     * @param memoryUtilizationPercent memory utilization percentage
     * @param ioUtilizationPercent I/O utilization percentage
     * @param maxConcurrentOperations maximum concurrent operations
     * @param peakMemoryUsageMB peak memory usage in MB
     * @param totalBytesRead total bytes read
     * @param totalBytesWritten total bytes written
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

    /**
     * Gets the CPU utilization percentage.
     *
     * @return CPU utilization percentage
     */
    public double getCpuUtilizationPercent() {
        return cpuUtilizationPercent;
    }

    /**
     * Gets the memory utilization percentage.
     *
     * @return memory utilization percentage
     */
    public double getMemoryUtilizationPercent() {
        return memoryUtilizationPercent;
    }

    /**
     * Gets the I/O utilization percentage.
     *
     * @return I/O utilization percentage
     */
    public double getIoUtilizationPercent() {
        return ioUtilizationPercent;
    }

    /**
     * Gets the maximum concurrent operations.
     *
     * @return maximum concurrent operations
     */
    public int getMaxConcurrentOperations() {
        return maxConcurrentOperations;
    }

    /**
     * Gets the peak memory usage in MB.
     *
     * @return peak memory usage in MB
     */
    public long getPeakMemoryUsageMB() {
        return peakMemoryUsageMB;
    }

    /**
     * Gets the total bytes read.
     *
     * @return total bytes read
     */
    public long getTotalBytesRead() {
        return totalBytesRead;
    }

    /**
     * Gets the total bytes written.
     *
     * @return total bytes written
     */
    public long getTotalBytesWritten() {
        return totalBytesWritten;
    }

    @Override
    public String toString() {
        return String.format(
                "ResourceUtilization{cpu=%.1f%%, memory=%.1f%%, io=%.1f%%, " +
                "maxConcurrent=%d, peakMemory=%dMB, read=%dMB, written=%dMB}",
                cpuUtilizationPercent, memoryUtilizationPercent, ioUtilizationPercent,
                maxConcurrentOperations, peakMemoryUsageMB,
                totalBytesRead / (1024 * 1024), totalBytesWritten / (1024 * 1024)
        );
    }
}