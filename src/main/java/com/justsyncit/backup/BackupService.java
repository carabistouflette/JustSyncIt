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
        // 1. Validation & Initialization (Fast, Synchronous)
        try {
            if (sourceDir == null)
                throw new IllegalArgumentException("Source directory cannot be null");
            if (!java.nio.file.Files.exists(sourceDir) || !java.nio.file.Files.isDirectory(sourceDir)) {
                throw new IllegalArgumentException("Directory must exist and be a directory: " + sourceDir);
            }
        } catch (IllegalArgumentException e) {
            return CompletableFuture.failedFuture(e);
        }

        return CompletableFuture.runAsync(() -> LOGGER.info("Starting backup of {}", sourceDir))
                .thenCompose(v -> {
                    // 2. Snapshot Creation (Blocking I/O - Metadata)
                    // We keep this in a separate stage.
                    return CompletableFuture.supplyAsync(() -> {
                        try {
                            ScanOptions scanOptions = new ScanOptions()
                                    .withSymlinkStrategy(options.getSymlinkStrategy())
                                    .withIncludeHiddenFiles(options.isIncludeHiddenFiles())
                                    .withMaxDepth(options.getMaxDepth());

                            String snapshotId = options.getSnapshotName() != null ? options.getSnapshotName()
                                    : "backup-" + Instant.now().toString();

                            String baseDescription = options.getDescription() != null ? options.getDescription()
                                    : "Backup created on " + Instant.now();
                            String description = "Processing session for directory: "
                                    + sourceDir.toAbsolutePath().toString()
                                    + " | " + baseDescription;

                            metadataService.createSnapshot(snapshotId, description);
                            LOGGER.info("Created snapshot: {}", snapshotId);

                            ChunkingOptions chunkingOptions = new ChunkingOptions()
                                    .withChunkSize(options.getChunkSize())
                                    .withDetectSparseFiles(true);

                            FileProcessor processor = FileProcessor.create(scanner, chunker, contentStore,
                                    metadataService);
                            processor.setSnapshotId(snapshotId);

                            // Pass context to next stage
                            return new BackupContext(snapshotId, processor, scanOptions, chunkingOptions);
                        } catch (Exception e) {
                            throw new RuntimeException("Failed during initialization", e);
                        }
                    });
                })
                .thenCompose(ctx -> {
                    // 3. Main Processing (Async)
                    return ctx.processor.processDirectory(sourceDir, ctx.scanOptions, ctx.chunkingOptions)
                            .thenApply(result -> new ProcessingContext(ctx, result));
                })
                .thenApply(pCtx -> {
                    // 4. Post-processing & Merkle Tree (Blocking I/O)
                    try {
                        String snapshotId = pCtx.backupCtx.snapshotId;
                        FileProcessor.ProcessingResult result = pCtx.result;

                        LOGGER.info("Backup completed successfully: {}", snapshotId);

                        int chunksCreated = (int) (result.getTotalBytes() / options.getChunkSize()) + 1;

                        if (blake3Service != null) {
                            LOGGER.info("Building Merkle Tree for snapshot: {}", snapshotId);
                            MerkleTree tree = new MerkleTree(blake3Service);
                            List<FileMetadata> allFiles = metadataService.getFilesInSnapshot(snapshotId, true);
                            MerkleNode root = tree.build(allFiles);
                            persistMerkleTree(root);
                            metadataService.setSnapshotRoot(snapshotId, root.getHash());
                            LOGGER.info("Merkle Tree built and persisted. Root: {}", root.getHash());
                        }

                        int errorFiles = result.getErrorFiles();
                        if (result.getProcessedFiles() == 0 && errorFiles > 0) {
                            LOGGER.error("Backup failed: processed 0 files with {} errors", errorFiles);
                            return BackupResult
                                    .failure("Backup processed 0 files with " + errorFiles + " errors. Check logs.");
                        }

                        return BackupResult.success(snapshotId, result.getProcessedFiles(),
                                result.getTotalBytes(), chunksCreated, errorFiles, options.isVerifyIntegrity());

                    } catch (Exception e) {
                        throw new RuntimeException("Post-processing failed", e);
                    }
                })
                .exceptionally(e -> {
                    // Unwrap CompletionException
                    Throwable cause = e instanceof java.util.concurrent.CompletionException ? e.getCause() : e;

                    if (cause instanceof InterruptedException) {
                        LOGGER.error("Backup interrupted", cause);
                        return BackupResult.failure("Backup was interrupted: " + cause.getMessage());
                    }

                    LOGGER.error("Backup failed: {}", cause.getMessage(), cause);
                    // Re-throw if it's a critical runtime exception that the caller expects,
                    // otherwise return failure result to be consistent with previous behavior?
                    // The previous behavior threw RuntimeException for interruption/IO.
                    // But the signature returns CompletableFuture<BackupResult>, so returning a
                    // failure object
                    // or failed future is cleaner. The previous implementation threw
                    // RuntimeException INSIDE the future,
                    // making the future complete exceptionally.

                    // Let's propagate the exception as a failed future for consistency with
                    // "throwing".
                    throw new java.util.concurrent.CompletionException(cause);
                });
    }

    // Helper context classes to pass data between stages
    private static class BackupContext {
        final String snapshotId;
        final FileProcessor processor;
        final ScanOptions scanOptions;
        final ChunkingOptions chunkingOptions;

        BackupContext(String snapshotId, FileProcessor processor, ScanOptions scanOptions,
                ChunkingOptions chunkingOptions) {
            this.snapshotId = snapshotId;
            this.processor = processor;
            this.scanOptions = scanOptions;
            this.chunkingOptions = chunkingOptions;
        }
    }

    private static class ProcessingContext {
        final BackupContext backupCtx;
        final FileProcessor.ProcessingResult result;

        ProcessingContext(BackupContext backupCtx, FileProcessor.ProcessingResult result) {
            this.backupCtx = backupCtx;
            this.result = result;
        }
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

                // Check for total failure: no files processed but errors exist
                int errorFiles = result.getErrorFiles();
                if (result.getProcessedFiles() == 0 && errorFiles > 0) {
                    LOGGER.error("Backup failed: processed 0 files with {} errors", errorFiles);
                    return BackupResult.failure("Backup processed 0 files with " + errorFiles + " errors. Check logs.");
                }

                return BackupResult.success(snapshotId, result.getProcessedFiles(),
                        result.getTotalBytes(), chunksCreated, errorFiles, options.isVerifyIntegrity());

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.error("Backup interrupted", e);
                throw new RuntimeException("Backup was interrupted", e);
            } catch (java.io.IOException e) {
                LOGGER.error("Backup failed due to IO error: {}", e.getMessage(), e);
                throw new RuntimeException("IO error during backup: " + e.getMessage(), e);
            } catch (RuntimeException e) {
                LOGGER.error("Backup failed: {}", e.getMessage(), e);
                throw e;
            } catch (Exception e) {
                LOGGER.error("Backup failed unexpectedly: {}", e.getMessage(), e);
                throw new RuntimeException("Unexpected error during backup", e);
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

        // 1. Fetch previous snapshot metadata (Async)
        return CompletableFuture.supplyAsync(() -> {
            try {
                return metadataService.getSnapshot(previousSnapshotId);
            } catch (java.io.IOException e) {
                throw new java.util.concurrent.CompletionException(new RuntimeException("Failed to get snapshot", e));
            }
        }).thenCompose(metadataOpt -> {
            if (metadataOpt.isEmpty()) {
                LOGGER.warn("Previous snapshot {} not found. Falling back to full backup.", previousSnapshotId);
                return backup(sourceDir, options);
            }
            com.justsyncit.storage.metadata.Snapshot snapshotMetadata = metadataOpt.get();
            Instant lastBackupTime = snapshotMetadata.getCreatedAt();

            return CompletableFuture.supplyAsync(() -> {
                LOGGER.info("Starting INCREMENTAL backup of {} using CBT", sourceDir);
                // Query CBT for changed files
                // Query CBT for changed files
                return cbtService.getChangedFiles(sourceDir, lastBackupTime);
            }).thenCompose(changedFiles -> {
                LOGGER.info("CBT detected {} changed files since {}", changedFiles.size(), lastBackupTime);

                // 2. Create new snapshot ID
                String snapshotId = options.getSnapshotName() != null
                        ? options.getSnapshotName()
                        : "backup-inc-" + Instant.now().toString();

                String description = "Incremental backup of " + sourceDir + " based on " + previousSnapshotId;
                try {
                    metadataService.createSnapshot(snapshotId, description);
                } catch (Exception e) {
                    throw new java.util.concurrent.CompletionException(
                            new RuntimeException("Failed to create snapshot", e));
                }

                // 3. Process ONLY changed files (Async)
                FileProcessor processor = FileProcessor.create(scanner, chunker, contentStore, metadataService);
                processor.setSnapshotId(snapshotId);

                if (eventListener != null)
                    processor.setEventListener(eventListener);

                com.justsyncit.scanner.ChunkingOptions chunkingOpts = new com.justsyncit.scanner.ChunkingOptions()
                        .withChunkSize(options.getChunkSize());

                List<CompletableFuture<FileProcessor.ProcessingResult>> futures = changedFiles.stream()
                        .filter(file -> java.nio.file.Files.exists(file) && java.nio.file.Files.isRegularFile(file))
                        .map(file -> processor.processFile(file, chunkingOpts))
                        .collect(Collectors.toList());

                // 4. Aggregate results (Non-blocking)
                return CompletableFuture.allOf(futures.toArray(new CompletableFuture<?>[0]))
                        .thenApply(v -> {
                            long totalBytes = 0;
                            int processedCount = 0;
                            int errorCount = 0;

                            for (CompletableFuture<FileProcessor.ProcessingResult> f : futures) {
                                try {
                                    FileProcessor.ProcessingResult res = f.join(); // Safe here as allOf guarantees
                                                                                   // completion
                                    if (res != null) {
                                        totalBytes += res.getTotalBytes();
                                        processedCount++;
                                    }
                                } catch (Exception e) {
                                    LOGGER.error("Failed to process file during incremental backup", e);
                                    errorCount++;
                                }
                            }
                            return FileProcessor.ProcessingResult.create(null, processedCount, 0, errorCount,
                                    totalBytes,
                                    totalBytes);
                        })
                        .thenCompose(result -> {
                            // 5. Copy unchanged files (Blocking but wrapped in supplyAsync via
                            // thenApply/Compose if we wanted, but let's stick to this flow)
                            // Since metadataService IO might be blocking, verify if we should wrap this
                            // too.
                            // For safety, let's wrap the copy IO in a separate async stage.
                            return CompletableFuture.supplyAsync(() -> {
                                try {
                                    LOGGER.info("Copying unchanged files from {} to {}", previousSnapshotId,
                                            snapshotId);
                                    List<String> changedPaths = changedFiles.stream().map(Path::toString)
                                            .collect(Collectors.toList());
                                    metadataService.copyUnchangedFiles(previousSnapshotId, snapshotId, changedPaths);
                                    return result;
                                } catch (java.io.IOException e) {
                                    throw new java.util.concurrent.CompletionException(
                                            new RuntimeException("Failed to copy unchanged files", e));
                                }
                            });
                        })
                        .thenApply(result -> {
                            // 6. Build Merkle Tree
                            if (blake3Service != null) {
                                try {
                                    LOGGER.info("Building Merkle Tree for incremental snapshot: {}", snapshotId);
                                    MerkleTree tree = new MerkleTree(blake3Service);
                                    List<FileMetadata> allFiles = metadataService.getFilesInSnapshot(snapshotId, false);
                                    MerkleNode root = tree.build(allFiles);
                                    persistMerkleTree(root);
                                    metadataService.setSnapshotRoot(snapshotId, root.getHash());
                                    LOGGER.info("Merkle Tree built and persisted. Root: {}", root.getHash());
                                } catch (Exception e) {
                                    // Make this critical?
                                    throw new java.util.concurrent.CompletionException(
                                            new RuntimeException("Failed to build Merkle Tree", e));
                                }
                            }

                            int chunksCreated = (int) (result.getTotalBytes() / options.getChunkSize()) + 1;

                            return BackupResult.success(snapshotId, result.getProcessedFiles(), result.getTotalBytes(),
                                    chunksCreated, result.getErrorFiles(), false);
                        });
            });
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
                int chunksCreated, int filesWithErrors, boolean integrityVerified) {
            return new BackupResult(snapshotId, filesProcessed, totalBytesProcessed, chunksCreated, filesWithErrors,
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

        // Iterative traversal to avoid StackOverflowError on deep directory structures
        java.util.Stack<MerkleNode> stack = new java.util.Stack<>();
        stack.push(root);

        while (!stack.isEmpty()) {
            MerkleNode node = stack.pop();
            metadataService.upsertMerkleNode(node);

            List<MerkleNode> children = node.getChildren();
            if (node.getType() == MerkleNode.Type.DIRECTORY && children != null) {
                for (MerkleNode child : children) {
                    stack.push(child);
                }
            }
        }
    }
}