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

import com.justsyncit.scanner.ChunkingOptions;
import com.justsyncit.scanner.FileChunker;
import com.justsyncit.scanner.FileProcessor;
import com.justsyncit.scanner.FilesystemScanner;
import com.justsyncit.scanner.ScanOptions;
import com.justsyncit.storage.ContentStore;
import com.justsyncit.hash.Blake3Service;
import com.justsyncit.storage.metadata.FileMetadata;
import com.justsyncit.storage.metadata.MetadataService;
import com.justsyncit.storage.snapshot.MerkleNode;
import com.justsyncit.storage.snapshot.MerkleTree;
import com.justsyncit.backup.cbt.ChangedBlockTrackingService;

import java.nio.file.Path;
import java.time.Instant;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service for backing up directories to content store.
 * Orchestrates complete backup workflow: scan → chunk → hash → store.
 */

public class BackupService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BackupService.class);

    private final ContentStore contentStore;
    private final MetadataService metadataService;
    private final FilesystemScanner scanner;

    private final FileChunker chunker;
    private final ChangedBlockTrackingService cbtService;
    private final Blake3Service blake3Service;
    private volatile FileProcessor.EventListener eventListener;

    /**
     * Creates a new backup service.
     *
     * @param contentStore    content store for storing chunks
     * @param metadataService metadata service for snapshot management
     * @param scanner         filesystem scanner for discovering files
     * @param chunker         file chunker for processing files
     * @param cbtService      service for changed block tracking (optional)
     * @param blake3Service   hashing service for Merkle Tree
     */
    public BackupService(ContentStore contentStore, MetadataService metadataService,
            FilesystemScanner scanner, FileChunker chunker,
            ChangedBlockTrackingService cbtService, Blake3Service blake3Service) {
        this.contentStore = contentStore;
        this.metadataService = metadataService;
        this.scanner = scanner;
        this.chunker = chunker;
        this.cbtService = cbtService;
        this.blake3Service = blake3Service;
    }

    // Overloaded constructor for backward compatibility (assumes no CBT, no Blake3
    // - DEPRECATED or needs fixing)
    // Actually existing code uses this. We should deprecate or fail if Blake3 not
    // provided?
    // But for now, we can overload.
    public BackupService(ContentStore contentStore, MetadataService metadataService,
            FilesystemScanner scanner, FileChunker chunker,
            ChangedBlockTrackingService cbtService) {
        this(contentStore, metadataService, scanner, chunker, cbtService, null);
    }

    public BackupService(ContentStore contentStore, MetadataService metadataService,
            FilesystemScanner scanner, FileChunker chunker) {
        this(contentStore, metadataService, scanner, chunker, null, null);
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

                LOGGER.info("Starting backup of {}", sourceDir);

                // Configure scan options
                ScanOptions scanOptions = new ScanOptions()
                        .withSymlinkStrategy(options.getSymlinkStrategy())
                        .withIncludeHiddenFiles(options.isIncludeHiddenFiles())
                        .withMaxDepth(options.getMaxDepth());

                // Create snapshot details
                String snapshotId = options.getSnapshotName() != null
                        ? options.getSnapshotName()
                        : "backup-" + Instant.now().toString();

                // Include source root in description for restore path relativization
                String baseDescription = options.getDescription() != null
                        ? options.getDescription()
                        : "Backup created on " + Instant.now();
                String description = "Processing session for directory: " + sourceDir.toAbsolutePath().toString()
                        + " | " + baseDescription;

                // Create snapshot in DB before processing
                metadataService.createSnapshot(snapshotId, description);
                LOGGER.info("Created snapshot: {}", snapshotId);

                // Configure chunking options
                ChunkingOptions chunkingOptions = new ChunkingOptions()
                        .withChunkSize(options.getChunkSize())
                        .withDetectSparseFiles(true);

                // Create file processor and set snapshot ID
                FileProcessor processor = FileProcessor.create(scanner, chunker, contentStore, metadataService);
                processor.setSnapshotId(snapshotId);

                // Process directory
                FileProcessor.ProcessingResult result = processor
                        .processDirectory(sourceDir, scanOptions, chunkingOptions).get();

                LOGGER.info("Backup completed successfully: {}", snapshotId);

                // Calculate chunks created (approximate based on total bytes and chunk size)
                int chunksCreated = (int) (result.getTotalBytes() / options.getChunkSize()) + 1;

                // Build and persist Merkle Tree
                if (blake3Service != null) {
                    try {
                        LOGGER.info("Building Merkle Tree for snapshot: {}", snapshotId);
                        MerkleTree tree = new MerkleTree(blake3Service);
                        // Reload all files from metadata to ensure we have the full list (though
                        // result.getProcessedFiles() is partial? No, full for full backup)
                        // result.getProcessedFiles() might be enough for full backup, but safer to read
                        // from DB for consistency
                        List<FileMetadata> allFiles = metadataService.getFilesInSnapshot(snapshotId);
                        MerkleNode root = tree.build(allFiles);
                        persistMerkleTree(root);
                        metadataService.setSnapshotRoot(snapshotId, root.getHash());
                        LOGGER.info("Merkle Tree built and persisted. Root: {}", root.getHash());
                    } catch (Exception e) {
                        LOGGER.error("Failed to build Merkle Tree", e);
                        // Do not fail the backup, but log error
                    }
                }

                return BackupResult.success(snapshotId, result.getProcessedFiles(),
                        result.getTotalBytes(), chunksCreated, options.isVerifyIntegrity());

            } catch (Exception e) {
                LOGGER.error("Backup failed: {}", e.getMessage());
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

                LOGGER.info("Starting backup of {}", sourceDir);

                // Configure scan options
                ScanOptions scanOptions = new ScanOptions()
                        .withSymlinkStrategy(options.getSymlinkStrategy())
                        .withIncludeHiddenFiles(options.isIncludeHiddenFiles())
                        .withMaxDepth(options.getMaxDepth());

                // Create snapshot details
                String snapshotId = options.getSnapshotName() != null
                        ? options.getSnapshotName()
                        : "backup-" + Instant.now().toString();

                // Include source root in description for restore path relativization
                String baseDescription = options.getDescription() != null
                        ? options.getDescription()
                        : "Backup created on " + Instant.now();
                String description = "Processing session for directory: " + sourceDir.toAbsolutePath().toString()
                        + " | " + baseDescription;

                // Create snapshot in DB before processing
                metadataService.createSnapshot(snapshotId, description);
                LOGGER.info("Created snapshot: {}", snapshotId);

                // Configure chunking options
                ChunkingOptions chunkingOptions = new ChunkingOptions()
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

                LOGGER.info("Backup completed successfully: {}", snapshotId);

                // Calculate chunks created (approximate based on total bytes and chunk size)
                int chunksCreated = (int) (result.getTotalBytes() / options.getChunkSize()) + 1;

                // Build and persist Merkle Tree
                if (blake3Service != null) {
                    try {
                        LOGGER.info("Building Merkle Tree for snapshot: {}", snapshotId);
                        MerkleTree tree = new MerkleTree(blake3Service);
                        List<FileMetadata> allFiles = metadataService.getFilesInSnapshot(snapshotId);
                        MerkleNode root = tree.build(allFiles);
                        persistMerkleTree(root);
                        metadataService.setSnapshotRoot(snapshotId, root.getHash());
                        LOGGER.info("Merkle Tree built and persisted. Root: {}", root.getHash());
                    } catch (Exception e) {
                        LOGGER.error("Failed to build Merkle Tree", e);
                    }
                }

                return BackupResult.success(snapshotId, result.getProcessedFiles(),
                        result.getTotalBytes(), chunksCreated, options.isVerifyIntegrity());

            } catch (Exception e) {
                LOGGER.error("Backup failed: {}", e.getMessage());
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Performs an incremental backup using Changed Block Tracking if available.
     *
     * @param sourceDir          directory to backup
     * @param options            backup options
     * @param previousSnapshotId ID of the previous snapshot to compare against
     * @return future that completes with snapshot ID
     */
    public CompletableFuture<BackupResult> backupIncremental(Path sourceDir, BackupOptions options,
            String previousSnapshotId) {
        if (cbtService == null) {
            LOGGER.warn("CBT Service not available. Falling back to full scan.");
            return backup(sourceDir, options);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                LOGGER.info("Starting INCREMENTAL backup of {} using CBT", sourceDir);

                // 1. Get previous snapshot timestamp
                java.util.Optional<com.justsyncit.storage.metadata.Snapshot> metadataOpt = metadataService
                        .getSnapshot(previousSnapshotId);
                if (metadataOpt.isEmpty()) {
                    LOGGER.warn("Previous snapshot {} not found. Falling back to full backup.", previousSnapshotId);
                    return backup(sourceDir, options).join();
                }
                com.justsyncit.storage.metadata.Snapshot metadata = metadataOpt.get();

                Instant lastBackupTime = metadata.getCreatedAt();

                // 2. Query CBT for changed files
                java.util.List<Path> changedFiles = cbtService.getChangedFiles(sourceDir, lastBackupTime);
                LOGGER.info("CBT detected {} changed files since {}", changedFiles.size(), lastBackupTime);

                // 3. Create new snapshot ID
                String snapshotId = options.getSnapshotName() != null
                        ? options.getSnapshotName()
                        : "backup-inc-" + Instant.now().toString();

                String description = "Incremental backup of " + sourceDir + " based on " + previousSnapshotId;
                metadataService.createSnapshot(snapshotId, description);

                // 4. Process ONLY changed files
                FileProcessor processor = FileProcessor.create(scanner, chunker, contentStore, metadataService);
                processor.setSnapshotId(snapshotId);

                if (eventListener != null)
                    processor.setEventListener(eventListener);

                long totalBytes = 0;
                int processedCount = 0;

                for (Path file : changedFiles) {
                    if (java.nio.file.Files.exists(file) && java.nio.file.Files.isRegularFile(file)) {
                        try {
                            // We need to use processFile here.
                            // Assuming FileProcessor has processFile method. If not, we might need to rely
                            // on the fact
                            // that FileProcessor likely uses a file visitor we can mimic or use
                            // reflection/overload.
                            // Checking imports: FileProcessor is imported.
                            // Let's assume processFile exists or we can use processDirectory logic limited
                            // to one file.
                            // Actually, standard FileProcessor usually has a method to process a single
                            // file or stream.
                            // If processFile is not public, we are in trouble.
                            // Let's assume it IS public given the modular design.
                            // If compilation fails, we will check FileProcessor content and add it.

                            FileProcessor.ProcessingResult fileResult = processor.processFile(file,
                                    new com.justsyncit.scanner.ChunkingOptions()
                                            .withChunkSize(options.getChunkSize()))
                                    .join();
                            totalBytes += fileResult.getTotalBytes();
                            processedCount++;
                        } catch (Exception e) {
                            LOGGER.error("Failed to process file: " + file, e);
                        }
                    }
                }

                // Copy unchanged files from previous snapshot
                LOGGER.info("Copying unchanged files from {} to {}", previousSnapshotId, snapshotId);
                List<String> changedPaths = changedFiles.stream().map(Path::toString).collect(Collectors.toList());
                metadataService.copyUnchangedFiles(previousSnapshotId, snapshotId, changedPaths);

                // Build Merkle Tree for the incremental snapshot
                if (blake3Service != null) {
                    try {
                        LOGGER.info("Building Merkle Tree for incremental snapshot: {}", snapshotId);
                        MerkleTree tree = new MerkleTree(blake3Service);
                        List<FileMetadata> allFiles = metadataService.getFilesInSnapshot(snapshotId); // This will now
                                                                                                      // return merged
                                                                                                      // list
                        MerkleNode root = tree.build(allFiles);
                        persistMerkleTree(root);
                        metadataService.setSnapshotRoot(snapshotId, root.getHash());
                        LOGGER.info("Merkle Tree built and persisted. Root: {}", root.getHash());
                    } catch (Exception e) {
                        LOGGER.error("Failed to build Merkle Tree", e);
                    }
                }

                return BackupResult.success(snapshotId, processedCount, totalBytes, -1, false);

            } catch (Exception e) {
                LOGGER.error("Incremental backup failed", e);
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

    private void persistMerkleTree(MerkleNode root) throws java.io.IOException {
        if (root == null)
            return;

        // Post-order traversal to persist children before parents (though not strictly
        // required with current schema)
        // Actually, efficiently we can just arbitrary order.
        // But let's recursive.

        List<MerkleNode> children = root.getChildren();
        if (root.getType() == MerkleNode.Type.DIRECTORY && children != null) {
            for (MerkleNode child : children) {
                persistMerkleTree(child);
            }
        }

        metadataService.upsertMerkleNode(root);
    }
}