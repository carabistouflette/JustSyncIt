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
 * Result of a batch buffer release operation.
 * Contains information about released buffers and operation statistics.
 */

/**
 * Result of a batch buffer release operation.
 * Contains information about released buffers and operation statistics.
 */

public final class BatchReleaseResult {

    /** Number of buffers successfully released. */
    private final int releasedCount;

    /** Number of buffers that failed to release. */
    private final int failedCount;

    /** Total size of all released buffers in bytes. */
    private final long totalSize;

    /** Time taken for the release in milliseconds. */
    private final long releaseTimeMs;

    /** Error if release failed completely. */
    private final Exception error;

    /**
     * Creates a successful BatchReleaseResult.
     *
     * @param releasedCount number of buffers successfully released
     * @param totalSize     total size of all released buffers in bytes
     */
    public BatchReleaseResult(int releasedCount, long totalSize) {
        this(releasedCount, 0, totalSize, 0, null);
    }

    /**
     * Creates a BatchReleaseResult with failure information.
     *
     * @param releasedCount number of buffers successfully released
     * @param failedCount   number of buffers that failed to release
     * @param totalSize     total size of all released buffers in bytes
     */
    public BatchReleaseResult(int releasedCount, int failedCount, long totalSize) {
        this(releasedCount, failedCount, totalSize, 0, null);
    }

    /**
     * Creates a BatchReleaseResult with full information.
     *
     * @param releasedCount number of buffers successfully released
     * @param failedCount   number of buffers that failed to release
     * @param totalSize     total size of all released buffers in bytes
     * @param releaseTimeMs time taken for release in milliseconds
     * @param error         error if release failed completely
     */
    public BatchReleaseResult(int releasedCount, int failedCount, long totalSize,
            long releaseTimeMs, Exception error) {
        this.releasedCount = releasedCount;
        this.failedCount = failedCount;
        this.totalSize = totalSize;
        this.releaseTimeMs = releaseTimeMs;
        this.error = error != null ? createExceptionCopy(error) : null;
    }

    /**
     * Creates a failed BatchReleaseResult.
     *
     * @param error the error that occurred
     * @return a failed BatchReleaseResult
     */
    public static BatchReleaseResult failed(Exception error) {
        return new BatchReleaseResult(0, 0, 0, 0, error);
    }

    /**
     * Gets the number of buffers successfully released.
     *
     * @return number of released buffers
     */
    public int getReleasedCount() {
        return releasedCount;
    }

    /**
     * Gets the number of buffers that failed to release.
     *
     * @return number of failed releases
     */
    public int getFailedCount() {
        return failedCount;
    }

    /**
     * Gets the total number of buffers in the batch.
     *
     * @return total number of buffers (released + failed)
     */
    public int getTotalCount() {
        return releasedCount + failedCount;
    }

    /**
     * Gets the total size of all released buffers in bytes.
     *
     * @return total size in bytes
     */
    public long getTotalSize() {
        return totalSize;
    }

    /**
     * Gets the time taken for the release in milliseconds.
     *
     * @return release time in milliseconds
     */
    public long getReleaseTimeMs() {
        return releaseTimeMs;
    }

    /**
     * Gets the error if release failed completely.
     *
     * @return error, or null if successful or partially successful
     */
    public Exception getError() {
        return error != null ? createExceptionCopy(error) : null;
    }

    private static Exception createExceptionCopy(Exception original) {
        try {
            return (Exception) original.getClass()
                    .getConstructor(String.class)
                    .newInstance(original.getMessage());
        } catch (ReflectiveOperationException e) {
            return new RuntimeException(original.getMessage(), original.getCause());
        }
    }

    /**
     * Checks if the release was completely successful.
     *
     * @return true if all buffers were released successfully, false otherwise
     */
    public boolean isCompleteSuccess() {
        return error == null && failedCount == 0;
    }

    /**
     * Checks if the release was at least partially successful.
     *
     * @return true if at least one buffer was released, false otherwise
     */
    public boolean isPartialSuccess() {
        return error == null && releasedCount > 0;
    }

    /**
     * Checks if the release was completely successful.
     *
     * @return true if successful, false otherwise
     */
    public boolean isSuccess() {
        return isCompleteSuccess();
    }

    /**
     * Gets the success rate as a percentage.
     *
     * @return success rate (0.0 to 1.0), or 0.0 if no buffers were processed
     */
    public double getSuccessRate() {
        int total = getTotalCount();
        return total > 0 ? (double) releasedCount / total : 0.0;
    }

    /**
     * Gets the average buffer size.
     *
     * @return average buffer size in bytes, or 0 if no buffers were released
     */
    public long getAverageBufferSize() {
        return releasedCount > 0 ? totalSize / releasedCount : 0;
    }

    /**
     * Creates a string representation of this result.
     *
     * @return string representation
     */
    @Override
    public String toString() {
        return String.format(
                "BatchReleaseResult{released=%d, failed=%d, total=%d, totalSize=%dMB, avgSize=%dKB, time=%dms, success=%s}",
                releasedCount, failedCount, getTotalCount(), totalSize / (1024 * 1024),
                getAverageBufferSize() / 1024, releaseTimeMs, isCompleteSuccess());
    }
}