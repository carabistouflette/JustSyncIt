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

package com.justsyncit.restore;

import com.justsyncit.storage.metadata.Snapshot;

/**
 * Interface for tracking restore progress.
 * Follows Interface Segregation Principle by providing focused progress tracking.
 */
public interface RestoreProgressTracker {

    /**
     * Called when restore operation starts.
     *
     * @param snapshot being restored
     * @param targetDirectory where files are being restored
     */
    void startRestore(Snapshot snapshot, java.nio.file.Path targetDirectory);

    /**
     * Called when restore progress is updated.
     *
     * @param filesProcessed number of files processed
     * @param totalFiles total number of files
     * @param bytesProcessed number of bytes processed
     * @param totalBytes total number of bytes
     * @param currentFile file currently being processed
     */
    void updateProgress(long filesProcessed, long totalFiles, long bytesProcessed,
                       long totalBytes, String currentFile);

    /**
     * Called when restore completes successfully.
     *
     * @param result restore result
     */
    void completeRestore(RestoreService.RestoreResult result);

    /**
     * Called when restore encounters an error.
     *
     * @param error error that occurred
     */
    void errorRestore(Throwable error);

    /**
     * Called when a file is skipped.
     *
     * @param file skipped file
     * @param reason reason for skipping
     */
    void fileSkipped(String file, String reason);

    /**
     * Called when a file encounters an error.
     *
     * @param file file with error
     * @param error error that occurred
     */
    void fileError(String file, Throwable error);
}