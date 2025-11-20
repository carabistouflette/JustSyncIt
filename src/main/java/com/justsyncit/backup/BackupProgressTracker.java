package com.justsyncit.backup;

import java.nio.file.Path;

/**
 * Interface for tracking backup progress.
 */
public interface BackupProgressTracker {

    /**
     * Called when backup starts.
     *
     * @param sourceDir source directory being backed up
     * @param snapshotName name of the snapshot being created
     */
    void startBackup(Path sourceDir, String snapshotName);

    /**
     * Called to update progress.
     *
     * @param filesProcessed number of files processed so far
     * @param totalFiles total number of files to process
     * @param bytesProcessed number of bytes processed so far
     * @param totalBytes total number of bytes to process
     */
    void updateProgress(long filesProcessed, long totalFiles, long bytesProcessed, long totalBytes);

    /**
     * Called when backup completes successfully.
     */
    void completeBackup();

    /**
     * Called when backup fails.
     *
     * @param error the error that caused the failure
     */
    void errorBackup(Throwable error);

    /**
     * Called when a file is processed successfully.
     *
     * @param file the file that was processed
     */
    default void fileProcessed(Path file) {
        // Default implementation does nothing
    }

    /**
     * Called when a file processing fails.
     *
     * @param file the file that failed to process
     * @param reason the reason for failure
     */
    default void fileError(Path file, String reason) {
        // Default implementation does nothing
    }

    /**
     * Called when a file is skipped.
     *
     * @param file the file that was skipped
     * @param reason the reason for skipping
     */
    default void fileSkipped(Path file, String reason) {
        // Default implementation does nothing
    }
}