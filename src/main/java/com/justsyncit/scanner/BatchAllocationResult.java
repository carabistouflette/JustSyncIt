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

import java.nio.ByteBuffer;
import java.util.List;

/**
 * Result of a batch buffer allocation operation.
 * Contains information about allocated buffers and operation statistics.
 */
public class BatchAllocationResult {
    
    /** List of allocated buffers. */
    private final List<ByteBuffer> buffers;
    
    /** Total size of all allocated buffers in bytes. */
    private final long totalSize;
    
    /** Number of buffers allocated. */
    private final int allocatedCount;
    
    /** Time taken for the allocation in milliseconds. */
    private final long allocationTimeMs;
    
    /** Error if allocation failed. */
    private final Exception error;

    /**
     * Creates a successful BatchAllocationResult.
     *
     * @param buffers list of allocated buffers
     * @param totalSize total size of all allocated buffers in bytes
     * @param allocatedCount number of buffers allocated
     */
    public BatchAllocationResult(List<ByteBuffer> buffers, long totalSize, int allocatedCount) {
        this(buffers, totalSize, allocatedCount, 0, null);
    }

    /**
     * Creates a BatchAllocationResult with timing information.
     *
     * @param buffers list of allocated buffers
     * @param totalSize total size of all allocated buffers in bytes
     * @param allocatedCount number of buffers allocated
     * @param allocationTimeMs time taken for allocation in milliseconds
     */
    public BatchAllocationResult(List<ByteBuffer> buffers, long totalSize, int allocatedCount, long allocationTimeMs) {
        this(buffers, totalSize, allocatedCount, allocationTimeMs, null);
    }

    /**
     * Creates a BatchAllocationResult with full information.
     *
     * @param buffers list of allocated buffers
     * @param totalSize total size of all allocated buffers in bytes
     * @param allocatedCount number of buffers allocated
     * @param allocationTimeMs time taken for allocation in milliseconds
     * @param error error if allocation failed
     */
    public BatchAllocationResult(List<ByteBuffer> buffers, long totalSize, int allocatedCount, 
                               long allocationTimeMs, Exception error) {
        this.buffers = buffers != null ? List.copyOf(buffers) : List.of();
        this.totalSize = totalSize;
        this.allocatedCount = allocatedCount;
        this.allocationTimeMs = allocationTimeMs;
        this.error = error;
    }

    /**
     * Creates a failed BatchAllocationResult.
     *
     * @param error the error that occurred
     * @return a failed BatchAllocationResult
     */
    public static BatchAllocationResult failed(Exception error) {
        return new BatchAllocationResult(List.of(), 0, 0, 0, error);
    }

    /**
     * Gets the list of allocated buffers.
     *
     * @return immutable list of allocated buffers
     */
    public List<ByteBuffer> getBuffers() {
        return buffers;
    }

    /**
     * Gets the total size of all allocated buffers in bytes.
     *
     * @return total size in bytes
     */
    public long getTotalSize() {
        return totalSize;
    }

    /**
     * Gets the number of buffers allocated.
     *
     * @return number of allocated buffers
     */
    public int getAllocatedCount() {
        return allocatedCount;
    }

    /**
     * Gets the time taken for the allocation in milliseconds.
     *
     * @return allocation time in milliseconds
     */
    public long getAllocationTimeMs() {
        return allocationTimeMs;
    }

    /**
     * Gets the error if allocation failed.
     *
     * @return error, or null if successful
     */
    public Exception getError() {
        return error;
    }

    /**
     * Checks if the allocation was successful.
     *
     * @return true if successful, false otherwise
     */
    public boolean isSuccess() {
        return error == null;
    }

    /**
     * Gets the average buffer size.
     *
     * @return average buffer size in bytes, or 0 if no buffers were allocated
     */
    public long getAverageBufferSize() {
        return allocatedCount > 0 ? totalSize / allocatedCount : 0;
    }

    /**
     * Creates a string representation of this result.
     *
     * @return string representation
     */
    @Override
    public String toString() {
        return String.format(
                "BatchAllocationResult{buffers=%d, totalSize=%dMB, allocatedCount=%d, avgSize=%dKB, time=%dms, success=%s}",
                buffers.size(), totalSize / (1024 * 1024), allocatedCount, getAverageBufferSize() / 1024,
                allocationTimeMs, isSuccess()
        );
    }
}