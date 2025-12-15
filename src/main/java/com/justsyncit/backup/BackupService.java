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

package com.justsyncit.backup;

import com.justsyncit.scanner.FileChunker;
import com.justsyncit.scanner.FileProcessor;
import com.justsyncit.scanner.FilesystemScanner;
import com.justsyncit.scanner.ScanOptions;
import com.justsyncit.storage.ContentStore;
import com.justsyncit.storage.metadata.MetadataService;

import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Service for backing up directories to content store.
 * Orchestrates complete backup workflow: scan → chunk → hash → store.
 */

public class BackupService {

    private static final Logger LOGGER = Logger.getLogger(BackupService.class.getName());

    private final ContentStore contentStore;
    private final MetadataService metadataService;
    private final FilesystemScanner scanner;
    private final FileChunker chunker;
    private volatile FileProcessor.EventListener eventListener;

    /**
     * Creates a new backup service.
     *
     * @param contentStore    content store for storing chunks
     * @param metadataService metadata service for snapshot management
     * @param scanner         filesystem scanner for discovering files
     * @param chunker         file chunker for processing files
     */
    public BackupService(ContentStore contentStore, MetadataService metadataService,
            FilesystemScanner scanner, FileChunker chunker) {
        this.contentStore = contentStore;
        this.metadataService = metadataService;
        this.scanner = scanner;
        this.chunker = chunker;
    }

    public void setEventListener(FileProcessor.EventListener eventListener) {
        this.eventListener = eventListener;
    }

    /**
     * Backs up a directory to content store.
     *
     * @param sourceDir directory to backup
     * @param options   backup options
     * @return future that completes with snapshot ID
     */
    public CompletableFuture<BackupResult> backup(Path sourceDir, BackupOptions options) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Validate source directory
                if (sourceDir == null) {
                    throw new IllegalArgumentException("Source directory cannot be null");
                }
                if (!java.nio.file.Files.exists(sourceDir)) {
                    throw new IllegalArgumentException("Directory must exist and be a directory: " + sourceDir);
                }
                if (!java.nio.file.Files.isDirectory(sourceDir)) {
                    throw new IllegalArgumentException("Directory must exist and be a directory: " + sourceDir);
                }

                LOGGER.info(String.format("Starting backup of %s", sourceDir));

                // Configure scan options
                ScanOptions scanOptions = new ScanOptions()
                        .withSymlinkStrategy(options.getSymlinkStrategy())
                        .withIncludeHiddenFiles(options.isIncludeHiddenFiles())
                        .withMaxDepth(options.getMaxDepth());

                // Create snapshot details
                String snapshotId = options.getSnapshotName() != null
                        ? options.getSnapshotName()
                        : "backup-" + Instant.now().toString();

                String description = options.getDescription() != null
                        ? options.getDescription()
                        : "Backup created on " + Instant.now();

                // Create snapshot in DB before processing
                metadataService.createSnapshot(snapshotId, description);
                LOGGER.info("Created snapshot: " + snapshotId);

                // Configure chunking options
                FileChunker.ChunkingOptions chunkingOptions = new FileChunker.ChunkingOptions()
                        .withChunkSize(options.getChunkSize())
                        .withDetectSparseFiles(true);

                // Create file processor and set snapshot ID
                FileProcessor processor = FileProcessor.create(scanner, chunker, contentStore, metadataService);
                processor.setSnapshotId(snapshotId);

                // Process directory
                FileProcessor.ProcessingResult result = processor
                        .processDirectory(sourceDir, scanOptions, chunkingOptions).get();

                LOGGER.info(String.format("Backup completed successfully: %s", snapshotId));

                // Calculate chunks created (approximate based on total bytes and chunk size)
                int chunksCreated = (int) (result.getTotalBytes() / options.getChunkSize()) + 1;

                return BackupResult.success(snapshotId, result.getProcessedFiles(),
                        result.getTotalBytes(), chunksCreated, options.isVerifyIntegrity());

            } catch (Exception e) {
                LOGGER.severe("Backup failed: " + e.getMessage());
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Backs up a directory to content store with progress tracking.
     *
     * @param sourceDir        directory to backup
     * @param options          backup options
     * @param progressListener listener for progress updates
     * @return future that completes with snapshot ID
     */
    public CompletableFuture<BackupResult> backup(Path sourceDir, BackupOptions options,
            java.util.function.Consumer<FileProcessor> progressListener) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Validate source directory
                if (sourceDir == null) {
                    throw new IllegalArgumentException("Source directory cannot be null");
                }
                if (!java.nio.file.Files.exists(sourceDir)) {
                    throw new IllegalArgumentException("Directory must exist and be a directory: " + sourceDir);
                }
                if (!java.nio.file.Files.isDirectory(sourceDir)) {
                    throw new IllegalArgumentException("Directory must exist and be a directory: " + sourceDir);
                }

                LOGGER.info(String.format("Starting backup of %s", sourceDir));

                // Configure scan options
                ScanOptions scanOptions = new ScanOptions()
                        .withSymlinkStrategy(options.getSymlinkStrategy())
                        .withIncludeHiddenFiles(options.isIncludeHiddenFiles())
                        .withMaxDepth(options.getMaxDepth());

                // Create snapshot details
                String snapshotId = options.getSnapshotName() != null
                        ? options.getSnapshotName()
                        : "backup-" + Instant.now().toString();

                String description = options.getDescription() != null
                        ? options.getDescription()
                        : "Backup created on " + Instant.now();

                // Create snapshot in DB before processing
                metadataService.createSnapshot(snapshotId, description);
                LOGGER.info("Created snapshot: " + snapshotId);

                // Configure chunking options
                FileChunker.ChunkingOptions chunkingOptions = new FileChunker.ChunkingOptions()
                        .withChunkSize(options.getChunkSize())
                        .withDetectSparseFiles(true);

                // Create file processor and set snapshot ID
                FileProcessor processor = FileProcessor.create(scanner, chunker, contentStore, metadataService);
                processor.setSnapshotId(snapshotId);

                if (progressListener != null) {
                    processor.setProgressListener(progressListener);
                }

                if (eventListener != null) {
                    processor.setEventListener(eventListener);
                }

                // Process directory
                FileProcessor.ProcessingResult result = processor
                        .processDirectory(sourceDir, scanOptions, chunkingOptions).get();

                LOGGER.info(String.format("Backup completed successfully: %s", snapshotId));

                // Calculate chunks created (approximate based on total bytes and chunk size)
                int chunksCreated = (int) (result.getTotalBytes() / options.getChunkSize()) + 1;

                return BackupResult.success(snapshotId, result.getProcessedFiles(),
                        result.getTotalBytes(), chunksCreated, options.isVerifyIntegrity());

            } catch (Exception e) {
                LOGGER.severe("Backup failed: " + e.getMessage());
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Result of a backup operation.
     */
    public static class BackupResult {
        private final String snapshotId;
        private final int filesProcessed;
        private final long totalBytesProcessed;
        private final int chunksCreated;
        private final int filesWithErrors;
        private final boolean integrityVerified;
        private final boolean success;
        private final String error;

        private BackupResult(String snapshotId, int filesProcessed, long totalBytesProcessed,
                int chunksCreated, int filesWithErrors, boolean integrityVerified,
                boolean success, String error) {
            this.snapshotId = snapshotId;
            this.filesProcessed = filesProcessed;
            this.totalBytesProcessed = totalBytesProcessed;
            this.chunksCreated = chunksCreated;
            this.filesWithErrors = filesWithErrors;
            this.integrityVerified = integrityVerified;
            this.success = success;
            this.error = error;
        }

        public static BackupResult success(String snapshotId, int filesProcessed, long totalBytesProcessed,
                int chunksCreated, boolean integrityVerified) {
            return new BackupResult(snapshotId, filesProcessed, totalBytesProcessed, chunksCreated, 0,
                    integrityVerified, true, null);
        }

        public static BackupResult failure(String error) {
            return new BackupResult(null, 0, 0, 0, 0, false, false, error);
        }

        public String getSnapshotId() {
            return snapshotId;
        }

        public int getFilesProcessed() {
            return filesProcessed;
        }

        public long getTotalBytesProcessed() {
            return totalBytesProcessed;
        }

        public int getChunksCreated() {
            return chunksCreated;
        }

        public int getFilesWithErrors() {
            return filesWithErrors;
        }

        public boolean isIntegrityVerified() {
            return integrityVerified;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getError() {
            return error;
        }
    }
}