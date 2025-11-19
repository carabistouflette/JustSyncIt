package com.justsyncit.scanner;

import com.justsyncit.storage.ContentStore;
import com.justsyncit.storage.metadata.ChunkMetadata;
import com.justsyncit.storage.metadata.FileMetadata;
import com.justsyncit.storage.metadata.MetadataService;
import com.justsyncit.storage.metadata.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service that orchestrates the workflow between filesystem scanning and chunking.
 * Integrates with ContentStore and MetadataService to provide complete file processing.
 */
public class FileProcessor {
    /** Logger instance for the FileProcessor class. */
    private static final Logger logger = LoggerFactory.getLogger(FileProcessor.class);
    /** Filesystem scanner for discovering files. */
    private final FilesystemScanner scanner;
    /** File chunker for processing files into chunks. */
    private final FileChunker chunker;
    /** Content store for storing chunks. */
    private final ContentStore contentStore;
    /** Metadata service for storing file and chunk metadata. */
    private final MetadataService metadataService;
    /** Executor service for async operations. */
    private volatile ExecutorService executorService;

    /** Flag indicating if processing is currently running. */
    private volatile boolean isRunning = false;
    /** Counter for processed files. */
    private final AtomicInteger processedFiles = new AtomicInteger(0);
    /** Counter for skipped files. */
    private final AtomicInteger skippedFiles = new AtomicInteger(0);
    /** Counter for files with errors. */
    private final AtomicInteger errorFiles = new AtomicInteger(0);
    /** Total bytes in all files. */
    private final AtomicLong totalBytes = new AtomicLong(0);
    /** Total bytes processed. */
    private final AtomicLong processedBytes = new AtomicLong(0);
    /** Current snapshot ID for this processing session. */
    private String currentSnapshotId;

    /**
     * Creates a new FileProcessor with specified dependencies.
     * @deprecated Use {@link #create(FilesystemScanner, FileChunker, ContentStore, MetadataService)} instead.
     */
    @Deprecated
    @SuppressWarnings("EI_EXPOSE_REP2")
    public FileProcessor(FilesystemScanner scanner, FileChunker chunker,
                        ContentStore contentStore, MetadataService metadataService) {
        // No validation in constructor - use static factory method instead
        // Note: We don't create defensive copies here as these are service interfaces
        // that are meant to be used directly. The static factory method handles validation.
        this.scanner = scanner;
        this.chunker = chunker;
        this.contentStore = contentStore;
        this.metadataService = metadataService;

        // Set content store on chunker if it supports it
        if (chunker instanceof FixedSizeFileChunker) {
            ((FixedSizeFileChunker) chunker).setContentStore(contentStore);
        }

        this.executorService = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors(),
            r -> {
                Thread t = new Thread(r, "FileProcessor-" + System.currentTimeMillis() + "-"
                        + UUID.randomUUID().toString().substring(0, 8));
                t.setDaemon(true);
                return t;
            });
    }

    /**
     * Creates a new FileProcessor with specified dependencies.
     *
     * @param scanner the filesystem scanner for discovering files
     * @param chunker the file chunker for processing files into chunks
     * @param contentStore the content store for storing chunks
     * @param metadataService the metadata service for storing file and chunk metadata
     * @return a new FileProcessor instance
     * @throws IllegalArgumentException if any parameter is null
     */
    public static FileProcessor create(FilesystemScanner scanner, FileChunker chunker,
                                      ContentStore contentStore, MetadataService metadataService) {
        if (scanner == null) {
            throw new IllegalArgumentException("Scanner cannot be null");
        }
        if (chunker == null) {
            throw new IllegalArgumentException("Chunker cannot be null");
        }
        if (contentStore == null) {
            throw new IllegalArgumentException("Content store cannot be null");
        }
        if (metadataService == null) {
            throw new IllegalArgumentException("Metadata service cannot be null");
        }
        return new FileProcessor(scanner, chunker, contentStore, metadataService);
    }

    /**
     * Processes files from the specified directory using the given options.
     */
    public CompletableFuture<ProcessingResult> processDirectory(Path directory, ScanOptions options) {
        // Check if executor has been shut down (stopped) and don't restart
        if (executorService.isShutdown()) {
            throw new IllegalStateException("FileProcessor has been stopped and cannot be restarted");
        }

        return CompletableFuture.supplyAsync(() -> {
            if (isRunning) {
                throw new IllegalStateException("FileProcessor is already running");
            }
            if (directory == null || !Files.exists(directory) || !Files.isDirectory(directory)) {
                throw new IllegalArgumentException("Directory must exist and be a directory: " + directory);
            }
            isRunning = true;
            resetCounters();

            // Create a single snapshot for this processing session
            currentSnapshotId = "processing-" + System.currentTimeMillis() + "-"
                    + UUID.randomUUID().toString().substring(0, 8);
            logger.info("Starting file processing for directory: {} with snapshot: {}", directory, currentSnapshotId);

            try {
                // Create snapshot first in a transaction to ensure visibility
                // Use a transaction to ensure the snapshot is properly committed and visible
                Transaction snapshotTransaction = null;
                try {
                    snapshotTransaction = metadataService.beginTransaction();
                    metadataService.createSnapshot(currentSnapshotId, "Processing session for directory: " + directory);
                    snapshotTransaction.commit();
                    logger.debug("Snapshot created and committed: {}", currentSnapshotId);
                    // Wait a moment to ensure the snapshot is visible to all connections
                    // This addresses SQLite's connection isolation issues with DELETE journal mode
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        // Don't fail the operation if interrupted
                    }
                    // Verify the snapshot exists before proceeding
                    if (!metadataService.getSnapshot(currentSnapshotId).isPresent()) {
                        throw new IOException("Snapshot was not committed properly: " + currentSnapshotId);
                    }
                    logger.debug("Snapshot verified to exist: {}", currentSnapshotId);
                } catch (IOException e) {
                    if (snapshotTransaction != null) {
                        try {
                            snapshotTransaction.rollback();
                        } catch (Exception rollbackEx) {
                            logger.warn("Failed to rollback snapshot transaction: {}", rollbackEx.getMessage());
                        }
                    }
                    logger.error("Failed to create snapshot: {}", e.getMessage());
                    // If snapshot creation fails, we can't proceed
                    throw new IOException("Failed to create snapshot for file processing", e);
                } finally {
                    if (snapshotTransaction != null) {
                        try {
                            snapshotTransaction.close();
                        } catch (Exception closeEx) {
                            logger.warn("Failed to close snapshot transaction: {}", closeEx.getMessage());
                        }
                    }
                }

                // Configure scanner with file visitor that handles chunking
                ChunkingFileVisitor fileVisitor = new ChunkingFileVisitor();
                scanner.setFileVisitor(fileVisitor);

                // Configure scanner with progress listener
                scanner.setProgressListener(new ProcessingProgressListener());
                // Perform scan
                CompletableFuture<ScanResult> scanFuture = scanner.scanDirectory(directory, options);
                ScanResult scanResult = scanFuture.get();

                // Wait for all async operations to complete
                fileVisitor.waitForCompletion();
                ProcessingResult result = ProcessingResult.create(
                        scanResult,
                        processedFiles.get(),
                        skippedFiles.get(),
                        errorFiles.get(),
                        totalBytes.get(),
                        processedBytes.get()
                );

                logger.info("File processing completed. Processed: {}, Skipped: {}, Errors: {}, Total bytes: {}",
                        result.getProcessedFiles(), result.getSkippedFiles(), result.getErrorFiles(),
                        result.getTotalBytes());

                return result;

            } catch (IOException e) {
                logger.error("Error during file processing", e);
                throw new CompletionException("File processing failed", e);
            } catch (IllegalStateException e) {
                logger.error("Error during file processing", e);
                throw e;
            } catch (RuntimeException e) {
                logger.error("Error during file processing", e);
                throw new IllegalStateException("Unexpected error during file processing", e);
            } catch (Exception e) {
                logger.error("Error during file processing", e);
                throw new CompletionException("File processing failed", e);
            } finally {
                isRunning = false;
                currentSnapshotId = null;
            }
        }, executorService);
    }

    /**
     * Stops the current processing operation.
     */
    public void stop() {
        isRunning = false;
        if (!executorService.isShutdown()) {
            executorService.shutdown();
        }
        try {
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Checks if processing is currently running.
     */
    public boolean isRunning() {
        return isRunning;
    }

    private void resetCounters() {
        processedFiles.set(0);
        skippedFiles.set(0);
        errorFiles.set(0);
        totalBytes.set(0);
        processedBytes.set(0);
    }

    /**
     * File visitor that handles chunking of files during scanning.
     */
    private class ChunkingFileVisitor implements FileVisitor {
        /** List of futures for chunking operations. */
        private final List<CompletableFuture<FileChunker.ChunkingResult>> chunkingFutures = new ArrayList<>();

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            if (!isRunning) {
                return FileVisitResult.TERMINATE;
            }

            // Only process regular files, skip directories and special files
            if (!attrs.isRegularFile()) {
                logger.debug("Skipping non-regular file: {}", file);
                skippedFiles.incrementAndGet();
                return FileVisitResult.CONTINUE;
            }

            try {
                // Create chunking options
                FileChunker.ChunkingOptions options = new FileChunker.ChunkingOptions()
                        .withChunkSize(chunker.getChunkSize())
                        .withUseAsyncIO(true)
                        .withDetectSparseFiles(true);

                // Start chunking file
                CompletableFuture<FileChunker.ChunkingResult> chunkingFuture = chunker.chunkFile(file, options)
                        .thenCompose(result -> {
                            return CompletableFuture.supplyAsync(() -> {
                                if (result.isSuccess()) {
                                    // Wait a moment to ensure all chunk metadata is committed
                                    // This addresses SQLite's connection isolation issues
                                    try {
                                        Thread.sleep(100); // Reduced delay for test performance
                                    } catch (InterruptedException ie) {
                                        Thread.currentThread().interrupt();
                                        // Don't fail the operation if interrupted
                                    }
                                    processChunkingResult(result);
                                } else {
                                    logger.error("Chunking failed for file: {}", file, result.getError());
                                    errorFiles.incrementAndGet();
                                }
                                return result;
                            }, executorService);
                        })
                        .exceptionally(throwable -> {
                            logger.error("Error chunking file: {}", file, throwable);
                            errorFiles.incrementAndGet();
                            IOException ioException = throwable instanceof IOException
                                    ? (IOException) throwable
                                    : new IOException(throwable);
                            return new FileChunker.ChunkingResult(file, ioException);
                        });

                chunkingFutures.add(chunkingFuture);

            } catch (RuntimeException e) {
                logger.error("Error starting chunking for file: {}", file, e);
                errorFiles.incrementAndGet();
            }

            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
            return isRunning ? FileVisitResult.CONTINUE : FileVisitResult.TERMINATE;
        }

        @Override
        public FileVisitResult visitFailed(Path file, IOException exc) throws IOException {
            logger.warn("Failed to visit file: {}", file, exc);
            skippedFiles.incrementAndGet();
            return FileVisitResult.CONTINUE;
        }

        private void processChunkingResult(FileChunker.ChunkingResult result) {
            try {
                // Create file metadata with generated ID and snapshot ID
                String fileId = java.util.UUID.randomUUID().toString();

                // The FixedSizeFileChunker now handles chunk storage automatically
                // when it has a content store set, so we don't need to
                // manually ensure chunks exist here.
                List<String> chunkHashes = result.getChunkHashes();

                // Wait longer to ensure all chunk metadata is committed
                // This addresses SQLite's connection isolation issues
                try {
                    Thread.sleep(500); // Reduced delay for test performance
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    // Don't fail operation if interrupted
                }

                // Verify chunks exist with retries
                for (String chunkHash : chunkHashes) {
                    if (!verifyChunkExists(chunkHash)) {
                        return;
                    }
                }

                FileMetadata fileMetadata = new FileMetadata(
                        fileId,
                        currentSnapshotId,
                        result.getFile().toString(),
                        result.getTotalSize(),
                        Instant.now(),
                        result.getFileHash(),
                        chunkHashes
                );
                // Store file metadata with retry for foreign key constraint
                storeFileMetadataWithRetry(fileMetadata, chunkHashes, result);

                processedFiles.incrementAndGet();
                processedBytes.addAndGet(result.getTotalSize());
                logger.debug("Completed processing file: {} ({} chunks)",
                        result.getFile(), result.getChunkCount());

            } catch (IOException e) {
                logger.error("Error storing metadata for file: {}", result.getFile(), e);
                errorFiles.incrementAndGet();
            }
        }

        /**
         * Verifies that a chunk exists in the content store with retries.
         *
         * @param chunkHash the hash of the chunk to verify
         * @return true if the chunk exists, false otherwise
         */
        private boolean verifyChunkExists(String chunkHash) {
            boolean chunkExists = false;
            int maxRetries = 5; // Reduced retries for test performance
            for (int attempt = 1; attempt <= maxRetries; attempt++) {
                try {
                    if (contentStore.existsChunk(chunkHash)) {
                        chunkExists = true;
                        logger.debug("Chunk {} is visible (attempt {}/{})", chunkHash, attempt, maxRetries);
                        break;
                    } else if (attempt < maxRetries) {
                        logger.debug("Chunk {} not yet visible (attempt {}/{}), waiting...",
                                chunkHash, attempt, maxRetries);
                        try {
                            // Use longer exponential backoff with more aggressive initial delay
                            long delayMs = 200L * attempt; // 200ms, 400ms, 600ms, ...
                            Thread.sleep(delayMs);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            return false;
                        }
                    }
                } catch (IOException e) {
                    logger.warn("Failed to check if chunk exists: {} (attempt {}/{})",
                            chunkHash, attempt, maxRetries, e);
                    if (attempt < maxRetries) {
                        try {
                            long delayMs = 200L * attempt;
                            Thread.sleep(delayMs);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            return false;
                        }
                    }
                }
            }
            if (!chunkExists) {
                logger.error("Chunk {} does not exist in content store after {} retries", chunkHash, maxRetries);
                errorFiles.incrementAndGet();
                return false;
            }
            return true;
        }

        /**
         * Stores file metadata with retry logic for foreign key constraints.
         *
         * @param fileMetadata the file metadata to store
         * @param chunkHashes the list of chunk hashes
         * @param result the chunking result
         * @throws IOException if storing fails after all retries
         */
        private void storeFileMetadataWithRetry(FileMetadata fileMetadata, List<String> chunkHashes,
                FileChunker.ChunkingResult result) throws IOException {
            int maxRetries = 10; // Reduced retries for test performance
            IOException lastException = null;

            for (int attempt = 1; attempt <= maxRetries; attempt++) {
                Transaction transaction = null;
                try {
                    // Begin transaction for file metadata
                    transaction = metadataService.beginTransaction();

                    // Ensure all chunk metadata exists before inserting file
                    try {
                        ensureChunkMetadataExists(chunkHashes);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Interrupted while ensuring chunk metadata exists", ie);
                    }

                    // Now insert file metadata
                    metadataService.insertFile(fileMetadata);
                    transaction.commit();
                    // Success, break out of retry loop
                    break;
                } catch (IOException e) {
                    lastException = e;
                    if (transaction != null) {
                        try {
                            transaction.rollback();
                        } catch (Exception rollbackEx) {
                            logger.warn("Failed to rollback transaction for file {}: {}",
                                    result.getFile(), rollbackEx.getMessage());
                        }
                    }
                    // If it's a foreign key constraint or visibility issue, chunks might not be visible yet
                    if (e.getMessage() != null
                            && (e.getMessage().contains("FOREIGN KEY constraint failed")
                            || e.getMessage().contains("SQLITE_CONSTRAINT_FOREIGNKEY")
                            || e.getMessage().contains("Not all chunk metadata is visible"))) {
                        if (attempt < maxRetries) {
                            long delayMs = 200L * attempt; // Reduced backoff: 200ms, 400ms, 600ms
                            logger.warn(
                                    "Chunk metadata visibility issue for file {} (attempt {}), retrying after {}ms...",
                                    result.getFile(), attempt, delayMs);
                            try {
                                Thread.sleep(delayMs);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                throw new IOException("Interrupted while retrying file insertion", ie);
                            }
                        } else {
                            logger.error("Failed to insert file metadata after {} attempts: {}",
                                    maxRetries, result.getFile());
                            throw lastException;
                        }
                    } else {
                        // Not a foreign key constraint, don't retry
                        throw e;
                    }
                } finally {
                    if (transaction != null) {
                        try {
                            transaction.close();
                        } catch (Exception closeEx) {
                            logger.warn("Failed to close transaction for file {}: {}",
                                    result.getFile(), closeEx.getMessage());
                        }
                    }
                }
            }
        }

        /**
         * Ensures that all chunk metadata exists before inserting file metadata.
         *
         * @param chunkHashes the list of chunk hashes to verify
         * @throws InterruptedException if the thread is interrupted
         * @throws IOException if there's an error creating chunk metadata
         */
        private void ensureChunkMetadataExists(List<String> chunkHashes)
                throws InterruptedException, IOException {
            for (String chunkHash : chunkHashes) {
                if (!metadataService.getChunkMetadata(chunkHash).isPresent()) {
                    // Create missing chunk metadata
                    ChunkMetadata chunkMetadata = new ChunkMetadata(
                            chunkHash,
                            0, // Size unknown, will be updated when chunk is accessed
                            java.time.Instant.now(),
                            1, // Initial reference count
                            java.time.Instant.now()
                    );
                    metadataService.upsertChunk(chunkMetadata);
                    logger.debug("Created missing chunk metadata for: {}", chunkHash);
                    // Wait a bit for the chunk metadata to be committed
                    Thread.sleep(200);
                }
            }
        }

        public void waitForCompletion() {
            try {
                // Add timeout to prevent infinite hanging
                CompletableFuture.allOf(chunkingFutures.toArray(new CompletableFuture[0]))
                        .get(30, java.util.concurrent.TimeUnit.SECONDS);
            } catch (java.util.concurrent.TimeoutException e) {
                logger.error("Timeout waiting for chunking completion after 5 minutes", e);
                // Cancel any remaining futures
                chunkingFutures.forEach(future -> future.cancel(true));
            } catch (Exception e) {
                logger.error("Error waiting for chunking completion", e);
            }
        }
    }

    /**
     * Progress listener for tracking scan progress.
     */
    private class ProcessingProgressListener implements FilesystemScanner.ProgressListener {
        @Override
        public void onScanStarted(Path directory) {
            logger.info("Started scanning directory: {}", directory);
        }

        @Override
        public void onFileProcessed(Path file, long filesProcessed, long totalFiles) {
            try {
                long fileSize = java.nio.file.Files.size(file);
                totalBytes.addAndGet(fileSize);
                logger.debug("Processed file: {} ({} bytes)", file, fileSize);
            } catch (IOException e) {
                logger.warn("Could not get size for file: {}", file, e);
            }
        }

        @Override
        public void onScanCompleted(ScanResult result) {
            logger.info("Scan completed. Found {} files, {} errors",
                    result.getScannedFiles().size(), result.getErrors().size());
        }

        @Override
        public void onScanError(Path path, Exception error) {
            logger.warn("Error scanning path: {}", path, error);
            skippedFiles.incrementAndGet();
        }
    }

    /**
     * Result of a file processing operation.
     */
    public static class ProcessingResult {
        /** Scan result from the filesystem scanner. */
        private final ScanResult scanResult;
        /** Number of successfully processed files. */
        private final int processedFiles;
        /** Number of skipped files. */
        private final int skippedFiles;
        /** Number of files with errors. */
        private final int errorFiles;
        /** Total bytes in all files. */
        private final long totalBytes;
        /** Total bytes processed. */
        private final long processedBytes;

        /**
         * Creates a new ProcessingResult.
         * @deprecated Use {@link #create(ScanResult, int, int, int, long, long)} instead.
         */
        @Deprecated
        public ProcessingResult(ScanResult scanResult, int processedFiles, int skippedFiles,
                              int errorFiles, long totalBytes, long processedBytes) {
            this.scanResult = scanResult;
            this.processedFiles = processedFiles;
            this.skippedFiles = skippedFiles;
            this.errorFiles = errorFiles;
            this.totalBytes = totalBytes;
            this.processedBytes = processedBytes;
        }

        /**
         * Creates a new ProcessingResult.
         *
         * @param scanResult the scan result from the filesystem scanner
         * @param processedFiles the number of successfully processed files
         * @param skippedFiles the number of skipped files
         * @param errorFiles the number of files with errors
         * @param totalBytes the total bytes in all files
         * @param processedBytes the total bytes processed
         * @return a new ProcessingResult instance
         */
        public static ProcessingResult create(ScanResult scanResult, int processedFiles, int skippedFiles,
                                             int errorFiles, long totalBytes, long processedBytes) {
            return new ProcessingResult(scanResult, processedFiles, skippedFiles,
                    errorFiles, totalBytes, processedBytes);
        }

        public ScanResult getScanResult() {
            return scanResult;
        }

        public int getProcessedFiles() {
            return processedFiles;
        }

        public int getSkippedFiles() {
            return skippedFiles;
        }

        public int getErrorFiles() {
            return errorFiles;
        }

        public long getTotalBytes() {
            return totalBytes;
        }

        public long getProcessedBytes() {
            return processedBytes;
        }

        public double getProcessingPercentage() {
            return totalBytes > 0 ? (double) processedBytes / totalBytes * 100 : 0;
        }
    }
}