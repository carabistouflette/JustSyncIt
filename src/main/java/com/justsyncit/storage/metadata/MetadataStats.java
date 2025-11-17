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

package com.justsyncit.storage.metadata;

import java.util.Objects;

/**
 * Statistics about the metadata database.
 */
public final class MetadataStats {

    /** Total number of snapshots. */
    private final long totalSnapshots;
    /** Total number of files across all snapshots. */
    private final long totalFiles;
    /** Total number of unique chunks. */
    private final long totalChunks;
    /** Total size of all chunks in bytes. */
    private final long totalChunkSize;
    /** Average number of chunks per file. */
    private final double avgChunksPerFile;
    /** Average chunk size in bytes. */
    private final double avgChunkSize;
    /** Deduplication ratio (total chunk size / unique chunk size). */
    private final double deduplicationRatio;

    /**
     * Creates a new MetadataStats instance.
     *
     * @param totalSnapshots total number of snapshots
     * @param totalFiles total number of files across all snapshots
     * @param totalChunks total number of unique chunks
     * @param totalChunkSize total size of all chunks in bytes
     * @param avgChunksPerFile average number of chunks per file
     * @param avgChunkSize average chunk size in bytes
     * @param deduplicationRatio deduplication ratio
     * @throws IllegalArgumentException if any parameter is negative
     */
    public MetadataStats(long totalSnapshots, long totalFiles, long totalChunks,
                       long totalChunkSize, double avgChunksPerFile,
                       double avgChunkSize, double deduplicationRatio) {
        if (totalSnapshots < 0) {
            throw new IllegalArgumentException("Total snapshots cannot be negative");
        }
        if (totalFiles < 0) {
            throw new IllegalArgumentException("Total files cannot be negative");
        }
        if (totalChunks < 0) {
            throw new IllegalArgumentException("Total chunks cannot be negative");
        }
        if (totalChunkSize < 0) {
            throw new IllegalArgumentException("Total chunk size cannot be negative");
        }
        if (avgChunksPerFile < 0) {
            throw new IllegalArgumentException("Average chunks per file cannot be negative");
        }
        if (avgChunkSize < 0) {
            throw new IllegalArgumentException("Average chunk size cannot be negative");
        }
        if (deduplicationRatio < 0) {
            throw new IllegalArgumentException("Deduplication ratio cannot be negative");
        }

        this.totalSnapshots = totalSnapshots;
        this.totalFiles = totalFiles;
        this.totalChunks = totalChunks;
        this.totalChunkSize = totalChunkSize;
        this.avgChunksPerFile = avgChunksPerFile;
        this.avgChunkSize = avgChunkSize;
        this.deduplicationRatio = deduplicationRatio;
    }

    /**
     * Gets the total number of snapshots.
     *
     * @return total snapshot count
     */
    public long getTotalSnapshots() {
        return totalSnapshots;
    }

    /**
     * Gets the total number of files across all snapshots.
     *
     * @return total file count
     */
    public long getTotalFiles() {
        return totalFiles;
    }

    /**
     * Gets the total number of unique chunks.
     *
     * @return total chunk count
     */
    public long getTotalChunks() {
        return totalChunks;
    }

    /**
     * Gets the total size of all chunks in bytes.
     *
     * @return total chunk size in bytes
     */
    public long getTotalChunkSize() {
        return totalChunkSize;
    }

    /**
     * Gets the average number of chunks per file.
     *
     * @return average chunks per file
     */
    public double getAvgChunksPerFile() {
        return avgChunksPerFile;
    }

    /**
     * Gets the average chunk size in bytes.
     *
     * @return average chunk size in bytes
     */
    public double getAvgChunkSize() {
        return avgChunkSize;
    }

    /**
     * Gets the deduplication ratio.
     * A value of 1.0 means no deduplication, higher values indicate more deduplication.
     *
     * @return deduplication ratio
     */
    public double getDeduplicationRatio() {
        return deduplicationRatio;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        MetadataStats that = (MetadataStats) o;
        return totalSnapshots == that.totalSnapshots
                && totalFiles == that.totalFiles
                && totalChunks == that.totalChunks
                && totalChunkSize == that.totalChunkSize
                && Double.compare(that.avgChunksPerFile, avgChunksPerFile) == 0
                && Double.compare(that.avgChunkSize, avgChunkSize) == 0
                && Double.compare(that.deduplicationRatio, deduplicationRatio) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(totalSnapshots, totalFiles, totalChunks, totalChunkSize,
                avgChunksPerFile, avgChunkSize, deduplicationRatio);
    }

    @Override
    public String toString() {
        return "MetadataStats{"
                + "totalSnapshots=" + totalSnapshots
                + ", totalFiles=" + totalFiles
                + ", totalChunks=" + totalChunks
                + ", totalChunkSize=" + totalChunkSize
                + ", avgChunksPerFile=" + avgChunksPerFile
                + ", avgChunkSize=" + avgChunkSize
                + ", deduplicationRatio=" + deduplicationRatio
                + '}';
    }
}