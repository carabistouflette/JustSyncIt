package com.justsyncit.scanner;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for filesystem scanning operations.
 * Follows Interface Segregation Principle by providing focused scanning
 * operations.
 */
public interface FilesystemScanner {

    /**
     * Scans a directory recursively according to the specified options.
     *
     * @param directory the directory to scan
     * @param options   the scanning options
     * @return a CompletableFuture that completes with the scan result
     * @throws IllegalArgumentException if directory is null or invalid
     */
    CompletableFuture<ScanResult> scanDirectory(Path directory, ScanOptions options);

    /**
     * Sets the file visitor for custom file processing.
     * If no visitor is set, default scanning behavior will be used.
     *
     * @param visitor the file visitor to use
     */
    void setFileVisitor(FileVisitor visitor);

    /**
     * Sets the progress listener for monitoring scan progress.
     *
     * @param listener the progress listener
     */
    void setProgressListener(ProgressListener listener);

    /**
     * Interface for monitoring scan progress.
     */
    interface ProgressListener {
        /**
         * Called when scan starts.
         *
         * @param directory the directory being scanned
         */
        void onScanStarted(Path directory);

        /**
         * Called when a file is processed.
         *
         * @param file           the file that was processed
         * @param filesProcessed the total number of files processed so far
         * @param totalFiles     the estimated total number of files (may be -1 if
         *                       unknown)
         */
        void onFileProcessed(Path file, long filesProcessed, long totalFiles);

        /**
         * Called when scan completes.
         *
         * @param result the final scan result
         */
        void onScanCompleted(ScanResult result);

        /**
         * Called when an error occurs during scanning.
         *
         * @param path  the path where the error occurred
         * @param error the error that occurred
         */
        void onScanError(Path path, Exception error);
    }
}