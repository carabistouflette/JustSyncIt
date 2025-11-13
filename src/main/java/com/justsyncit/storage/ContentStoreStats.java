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

package com.justsyncit.storage;

import java.time.Instant;
import java.util.Objects;

/**
 * Statistics about the content store storage usage and performance.
 */
public class ContentStoreStats {

    /** Total number of chunks stored. */
    private final long totalChunks;
    /** Total storage size in bytes. */
    private final long totalSizeBytes;
    /** Deduplication ratio (stored/unique). */
    private final long deduplicationRatio;
    /** Timestamp of the last garbage collection. */
    private final Instant lastGcTime;
    /** Number of orphaned chunks. */
    private final long orphanedChunks;

    /**
     * Creates a new ContentStoreStats instance.
     *
     * @param totalChunks the total number of chunks stored
     * @param totalSizeBytes the total storage size in bytes
     * @param deduplicationRatio the deduplication ratio (stored/unique)
     * @param lastGcTime the timestamp of the last garbage collection
     * @param orphanedChunks the number of orphaned chunks
     */
    public ContentStoreStats(long totalChunks, long totalSizeBytes, long deduplicationRatio,
                           Instant lastGcTime, long orphanedChunks) {
        this.totalChunks = totalChunks;
        this.totalSizeBytes = totalSizeBytes;
        this.deduplicationRatio = deduplicationRatio;
        this.lastGcTime = lastGcTime;
        this.orphanedChunks = orphanedChunks;
    }

    /**
     * Gets the total number of chunks stored.
     *
     * @return the total chunk count
     */
    public long getTotalChunks() {
        return totalChunks;
    }

    /**
     * Gets the total storage size in bytes.
     *
     * @return the total size in bytes
     */
    public long getTotalSizeBytes() {
        return totalSizeBytes;
    }

    /**
     * Gets the deduplication ratio.
     * A value of 1.0 means no deduplication, higher values indicate more deduplication.
     *
     * @return the deduplication ratio
     */
    public long getDeduplicationRatio() {
        return deduplicationRatio;
    }

    /**
     * Gets the timestamp of the last garbage collection.
     *
     * @return the last GC time, or null if no GC has been performed
     */
    public Instant getLastGcTime() {
        return lastGcTime;
    }

    /**
     * Gets the number of orphaned chunks.
     *
     * @return the orphaned chunk count
     */
    public long getOrphanedChunks() {
        return orphanedChunks;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ContentStoreStats that = (ContentStoreStats) o;
        return totalChunks == that.totalChunks
                && totalSizeBytes == that.totalSizeBytes
                && deduplicationRatio == that.deduplicationRatio
                && orphanedChunks == that.orphanedChunks
                && Objects.equals(lastGcTime, that.lastGcTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(totalChunks, totalSizeBytes, deduplicationRatio, lastGcTime, orphanedChunks);
    }

    @Override
    public String toString() {
        return "ContentStoreStats{"
                + "totalChunks=" + totalChunks
                + ", totalSizeBytes=" + totalSizeBytes
                + ", deduplicationRatio=" + deduplicationRatio
                + ", lastGcTime=" + lastGcTime
                + ", orphanedChunks=" + orphanedChunks
                + '}';
    }
}