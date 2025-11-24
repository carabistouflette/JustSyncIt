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

package com.justsyncit.scanner;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Interface for asynchronous filesystem scanning operations with WatchService integration.
 * Extends FilesystemScanner to maintain compatibility while adding advanced async capabilities.
 * 
 * Features:
 * - Non-blocking directory traversal using CompletableFuture
 * - Real-time file change monitoring through WatchService
 * - Event-driven file processing with async callbacks
 * - Parallel directory scanning with configurable concurrency
 * - Backpressure control and flow management
 * - Progress monitoring and cancellation support
 * - Integration with async buffer pools and thread management
 */
public interface AsyncFilesystemScanner extends FilesystemScanner {

    /**
     * Asynchronously scans a directory with enhanced async capabilities.
     * This method provides non-blocking directory traversal with real-time progress updates.
     *
     * @param directory the directory to scan
     * @param options the scanning options
     * @return a CompletableFuture that completes with the enhanced scan result
     * @throws IllegalArgumentException if directory is null or invalid
     */
    CompletableFuture<AsyncScanResult> scanDirectoryAsync(Path directory, ScanOptions options);

    /**
     * Starts real-time monitoring of a directory for file changes.
     * Uses WatchService to detect file creation, modification, and deletion events.
     *
     * @param directory the directory to monitor
     * @param options the monitoring options
     * @param eventHandler the handler for file change events
     * @return a CompletableFuture that completes with the watch service registration result
     * @throws IllegalArgumentException if directory is null or invalid
     */
    CompletableFuture<WatchServiceRegistration> startDirectoryMonitoring(
            Path directory, 
            AsyncScanOptions options, 
            Consumer<FileChangeEvent> eventHandler);

    /**
     * Stops monitoring a directory for file changes.
     *
     * @param registration the watch service registration to cancel
     * @return a CompletableFuture that completes when monitoring is stopped
     */
    CompletableFuture<Void> stopDirectoryMonitoring(WatchServiceRegistration registration);

    /**
     * Scans a directory in parallel using multiple threads for enhanced performance.
     *
     * @param directory the directory to scan
     * @param options the scanning options
     * @param concurrency the level of parallelism to use
     * @return a CompletableFuture that completes with the parallel scan result
     */
    CompletableFuture<AsyncScanResult> scanDirectoryParallel(
            Path directory, 
            ScanOptions options, 
            int concurrency);

    /**
     * Scans a directory with streaming results for large directory structures.
     * Results are delivered incrementally through the provided consumer.
     *
     * @param directory the directory to scan
     * @param options the scanning options
     * @param resultConsumer consumer for incremental scan results
     * @return a CompletableFuture that completes when scanning is finished
     */
    CompletableFuture<Void> scanDirectoryStreaming(
            Path directory, 
            ScanOptions options, 
            Consumer<AsyncScanResult> resultConsumer);

    /**
     * Sets the async file visitor for non-blocking file processing.
     *
     * @param asyncVisitor the async file visitor to use
     * @throws IllegalArgumentException if asyncVisitor is null
     */
    void setAsyncFileVisitor(AsyncFileVisitor asyncVisitor);

    /**
     * Gets the current async file visitor.
     *
     * @return the current async file visitor
     */
    AsyncFileVisitor getAsyncFileVisitor();

    /**
     * Sets the async buffer pool for memory management during scanning.
     *
     * @param asyncBufferPool the async buffer pool to use
     * @throws IllegalArgumentException if asyncBufferPool is null
     */
    void setAsyncBufferPool(AsyncByteBufferPool asyncBufferPool);

    /**
     * Gets the current async buffer pool.
     *
     * @return the current async buffer pool
     */
    AsyncByteBufferPool getAsyncBufferPool();

    /**
     * Sets the async progress listener for enhanced progress monitoring.
     *
     * @param asyncProgressListener the async progress listener to use
     */
    void setAsyncProgressListener(AsyncProgressListener asyncProgressListener);

    /**
     * Gets the current async progress listener.
     *
     * @return the current async progress listener
     */
    AsyncProgressListener getAsyncProgressListener();

    /**
     * Cancels an ongoing scan operation.
     *
     * @param scanId the ID of the scan to cancel
     * @return true if the scan was cancelled, false if not found or already completed
     */
    boolean cancelScan(String scanId);

    /**
     * Gets the current number of active scanning operations.
     *
     * @return the number of active operations
     */
    int getActiveScanCount();

    /**
     * Gets the maximum number of concurrent scanning operations allowed.
     *
     * @return the maximum number of concurrent operations
     */
    int getMaxConcurrentScans();

    /**
     * Sets the maximum number of concurrent scanning operations allowed.
     *
     * @param maxConcurrentScans the maximum number of concurrent operations
     * @throws IllegalArgumentException if maxConcurrentScans is not positive
     */
    void setMaxConcurrentScans(int maxConcurrentScans);

    /**
     * Gets comprehensive statistics about the async scanner.
     *
     * @return a CompletableFuture that completes with scanner statistics
     */
    CompletableFuture<AsyncScannerStats> getStatsAsync();

    /**
     * Applies backpressure to control scanning throughput.
     *
     * @param pressureLevel the backpressure level (0.0 to 1.0)
     */
    void applyBackpressure(double pressureLevel);

    /**
     * Releases backpressure and resumes normal scanning throughput.
     */
    void releaseBackpressure();

    /**
     * Closes the scanner and releases all resources asynchronously.
     *
     * @return a CompletableFuture that completes when all resources have been released
     */
    CompletableFuture<Void> closeAsync();

    /**
     * Checks if the scanner has been closed.
     *
     * @return true if closed, false otherwise
     */
    boolean isClosed();

    /**
     * Enhanced interface for async file visiting operations.
     */
    interface AsyncFileVisitor {
        /**
         * Called asynchronously when a file is visited during scanning.
         *
         * @param file the file that was visited
         * @param attrs the file attributes
         * @return a CompletableFuture that completes with the visitation result
         */
        CompletableFuture<FileVisitor.FileVisitResult> visitFileAsync(Path file, java.nio.file.attribute.BasicFileAttributes attrs);

        /**
         * Called asynchronously when a directory is visited during scanning.
         *
         * @param dir the directory that was visited
         * @param attrs the directory attributes
         * @return a CompletableFuture that completes with the visitation result
         */
        CompletableFuture<FileVisitor.FileVisitResult> visitDirectoryAsync(Path dir, java.nio.file.attribute.BasicFileAttributes attrs);

        /**
         * Called asynchronously when a file or directory cannot be visited.
         *
         * @param file the file that could not be visited
         * @param exc the exception that prevented visitation
         * @return a CompletableFuture that completes with the visitation result
         */
        CompletableFuture<FileVisitor.FileVisitResult> visitFailedAsync(Path file, IOException exc);
    }

    /**
     * Enhanced progress listener for async operations.
     */
    interface AsyncProgressListener extends ProgressListener {
        /**
         * Called asynchronously when scan starts.
         *
         * @param scanId the unique ID of this scan
         * @param directory the directory being scanned
         */
        void onScanStartedAsync(String scanId, Path directory);

        /**
         * Called asynchronously when a file is processed.
         *
         * @param scanId the unique ID of this scan
         * @param file the file that was processed
         * @param filesProcessed the total number of files processed so far
         * @param totalFiles the estimated total number of files (may be -1 if unknown)
         */
        void onFileProcessedAsync(String scanId, Path file, long filesProcessed, long totalFiles);

        /**
         * Called asynchronously when scan completes.
         *
         * @param scanId the unique ID of this scan
         * @param result the final scan result
         */
        void onScanCompletedAsync(String scanId, AsyncScanResult result);

        /**
         * Called asynchronously when an error occurs during scanning.
         *
         * @param scanId the unique ID of this scan
         * @param path the path where the error occurred
         * @param error the error that occurred
         */
        void onScanErrorAsync(String scanId, Path path, Exception error);

        /**
         * Called when scan progress is updated.
         *
         * @param scanId the unique ID of this scan
         * @param progress the current progress percentage (0.0 to 1.0)
         * @param throughput the current throughput in files per second
         */
        void onProgressUpdated(String scanId, double progress, double throughput);
    }
}