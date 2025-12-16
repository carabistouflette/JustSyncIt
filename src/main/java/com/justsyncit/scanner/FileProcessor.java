package com.justsyncit.scanner;

import com.justsyncit.storage.ContentStore;

import com.justsyncit.storage.metadata.ChunkMetadata;
import com.justsyncit.storage.metadata.FileMetadata;
import com.justsyncit.storage.metadata.MetadataService;
import com.justsyncit.storage.metadata.Snapshot;
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
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service that orchestrates the workflow between filesystem scanning and
 * chunking.
 * Integrates with ContentStore and MetadataService to provide complete file
 * processing.
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

    // Constants for retry logic and timeouts
    private static final int MAX_RETRIES = 5;
    private static final long MAX_BACKOFF_MS = 500L;
    private static final long INITIAL_BACKOFF_MS = 100L;
    private static final long CHUNKING_TIMEOUT_HOURS = 24L;
    private static final int BATCH_SIZE = 200;
    private static final long PERSISTENCE_SHUTDOWN_TIMEOUT_MS = 30000L;

    /** ExecutorService for processing tasks. */
    private volatile ExecutorService executorService;
    /** Scheduler for delayed retry operations. */
    private final java.util.concurrent.ScheduledExecutorService scheduler = java.util.concurrent.Executors
            .newScheduledThreadPool(1, r -> {
                Thread t = new Thread(r, "FileProcessor-Scheduler");
                t.setDaemon(true);
                return t;
            });

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
    /** Counter for detected files during scan. */
    private final AtomicInteger detectedFiles = new AtomicInteger(0);
    /** Semaphore to limit concurrent file processing. */
    private final java.util.concurrent.Semaphore fileConcurrencySemaphore = new java.util.concurrent.Semaphore(4);
    /** Current snapshot ID for this processing session. */
    private String currentSnapshotId;
    /** Current file being processed. */
    private volatile String currentFile;
    /** Current activity description. */
    private volatile String currentActivity = "Initializing...";
    /** Optional listener for progress updates. */
    private volatile java.util.function.Consumer<FileProcessor> progressListener;

    /** Queue for chunking results waiting to be persisted. */
    private final java.util.concurrent.BlockingQueue<FileChunker.ChunkingResult> persistenceQueue = new java.util.concurrent.LinkedBlockingQueue<>(
            10000);
    /** Worker thread for batch persistence. */
    private volatile Thread persistenceThread;

    /**
     * Sets a listener to be notified of progress updates.
     * 
     * @param listener the listener to notify
     */
    public void setSnapshotId(String snapshotId) {
        this.currentSnapshotId = snapshotId;
    }

    /** Optional listener for detailed events. */
    private volatile EventListener eventListener;

    /**
     * Interface for listening to detailed file processing events.
     */
    public interface EventListener {
        void onEvent(String type, String level, String message, String file);
    }

    public void setEventListener(EventListener eventListener) {
        this.eventListener = eventListener;
    }

    public void setProgressListener(java.util.function.Consumer<FileProcessor> progressListener) {
        this.progressListener = progressListener;
    }

    /**
     * Gets the current file being processed.
     * 
     * @return the path of the current file as a string, or null if not processing
     */
    public String getCurrentFile() {
        return currentFile;
    }

    /**
     * Gets the current activity description.
     * 
     * @return the current activity
     */
    public String getCurrentActivity() {
        return currentActivity;
    }

    private void setCurrentActivity(String activity) {
        this.currentActivity = activity;
    }

    /**
     * Creates a new FileProcessor with specified dependencies.
     *
     * @deprecated Use
     *             {@link #create(FilesystemScanner, FileChunker, ContentStore, MetadataService)}
     *             instead.
     */
    @Deprecated
    @SuppressWarnings("EI_EXPOSE_REP2")

    public FileProcessor(FilesystemScanner scanner, FileChunker chunker,
            ContentStore contentStore, MetadataService metadataService) {
        // No validation in constructor - use static factory method instead
        // Note: We don't create defensive copies here as these are service interfaces
        // that are meant to be used directly. The static factory method handles
        // validation.
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
     * @param scanner         the filesystem scanner for discovering files
     * @param chunker         the file chunker for processing files into chunks
     * @param contentStore    the content store for storing chunks
     * @param metadataService the metadata service for storing file and chunk
     *                        metadata
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
     *
     * @param directory the directory to process
     * @param options   the scan options
     * @return a future that completes with the processing result
     */
    public CompletableFuture<ProcessingResult> processDirectory(Path directory, ScanOptions options) {
        return processDirectory(directory, options, new FileChunker.ChunkingOptions()
                .withChunkSize(chunker.getChunkSize())
                .withUseAsyncIO(true)
                .withDetectSparseFiles(true));
    }

    /**
     * Processes files from the specified directory using the given options.
     *
     * @param directory       the directory to process
     * @param options         the scan options
     * @param chunkingOptions the chunking options
     * @return a future that completes with the processing result
     */
    public CompletableFuture<ProcessingResult> processDirectory(Path directory, ScanOptions options,
            FileChunker.ChunkingOptions chunkingOptions) {
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

            // Determine snapshot ID (use existing or generate new)
            boolean externalSnapshot = (currentSnapshotId != null);
            if (!externalSnapshot) {
                currentSnapshotId = "processing-" + System.currentTimeMillis() + "-"
                        + UUID.randomUUID().toString().substring(0, 8);
            }
            logger.info("Starting file processing for directory: {} with snapshot: {}", directory, currentSnapshotId);

            try {
                // Create snapshot if it doesn't exist
                if (!metadataService.getSnapshot(currentSnapshotId).isPresent()) {
                    Transaction snapshotTransaction = null;
                    try {
                        snapshotTransaction = metadataService.beginTransaction();
                        metadataService.createSnapshot(currentSnapshotId,
                                "Processing session for directory: " + directory);
                        snapshotTransaction.commit();
                        logger.debug("Snapshot created and committed: {}", currentSnapshotId);

                        // Wait a moment to ensure the snapshot is visible to all connections
                        // Wait a moment to ensure the snapshot is visible to all connections
                        // Removed artificial delay - transaction commit should ensure visibility
                    } catch (IOException e) {
                        if (snapshotTransaction != null) {
                            try {
                                snapshotTransaction.rollback();
                            } catch (Exception rollbackEx) {
                                logger.warn("Failed to rollback snapshot transaction: {}", rollbackEx.getMessage());
                            }
                        }
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
                } else {
                    logger.debug("Using existing snapshot: {}", currentSnapshotId);
                }

                // Initialize and start persistence worker
                PersistenceWorker worker = new PersistenceWorker();
                persistenceThread = new Thread(worker, "PersistenceWorker-" + currentSnapshotId);
                persistenceThread.start();

                // Configure scanner with file visitor that handles chunking
                ChunkingFileVisitor fileVisitor = new ChunkingFileVisitor(chunkingOptions, currentSnapshotId);
                scanner.setFileVisitor(fileVisitor);

                // Configure scanner with progress listener
                scanner.setProgressListener(new ProcessingProgressListener());
                // Perform scan
                CompletableFuture<ScanResult> scanFuture = scanner.scanDirectory(directory, options);
                ScanResult scanResult = scanFuture.get();

                // Wait for all async operations to complete
                fileVisitor.waitForCompletion();

                // Signal persistence worker to shut down and wait for it
                worker.shutdown();
                try {
                    persistenceThread.join(PERSISTENCE_SHUTDOWN_TIMEOUT_MS);
                } catch (InterruptedException e) {
                    logger.warn("Interrupted while waiting for persistence worker to finish", e);
                    Thread.currentThread().interrupt();
                }

                ProcessingResult result = ProcessingResult.create(
                        scanResult,
                        processedFiles.get(),
                        skippedFiles.get(),
                        errorFiles.get(),
                        totalBytes.get(),
                        processedBytes.get());

                logger.info("File processing completed. Processed: {}, Skipped: {}, Errors: {}, Total bytes: {}",
                        result.getProcessedFiles(), result.getSkippedFiles(), result.getErrorFiles(),
                        result.getTotalBytes());

                // Update snapshot with final statistics
                try {
                    // Fetch the current snapshot to ensure we have the correct ID and created_at
                    Optional<Snapshot> currentSnapshotOpt = metadataService.getSnapshot(currentSnapshotId);
                    if (currentSnapshotOpt.isPresent()) {
                        Snapshot currentSnapshot = currentSnapshotOpt.get();
                        // Create a new snapshot instance with updated stats
                        Snapshot updatedSnapshot = new Snapshot(
                                currentSnapshot.getId(),
                                currentSnapshot.getName(),
                                currentSnapshot.getDescription(),
                                currentSnapshot.getCreatedAt(),
                                result.getProcessedFiles(),
                                result.getProcessedBytes());

                        metadataService.updateSnapshot(updatedSnapshot);
                        logger.info("Updated snapshot statistics for {}", currentSnapshotId);
                    } else {
                        logger.warn("Could not find snapshot {} to update statistics", currentSnapshotId);
                    }
                } catch (IOException e) {
                    logger.error("Failed to update snapshot statistics", e);
                    // Don't fail the whole operation just because stats update failed
                }

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

        if (!scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
            }
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
        /** Queue of files to process. */
        private final ConcurrentLinkedQueue<Path> processingQueue = new ConcurrentLinkedQueue<>();
        /** Phaser for tracking active chunking operations. */
        private final java.util.concurrent.Phaser chunkingPhaser = new java.util.concurrent.Phaser(1); // Register self
        /** Chunking options to use. */
        private final FileChunker.ChunkingOptions options;
        /** Snapshot ID to use for this visitor. */
        private final String snapshotId;

        ChunkingFileVisitor(FileChunker.ChunkingOptions options, String snapshotId) {
            this.options = options;
            this.snapshotId = snapshotId;
        }

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

            // Queue the file for processing later
            // This separates scanning (fast) from processing (slow)
            // and allows accurate total size calculation before processing starts
            processingQueue.add(file);
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
            return isRunning ? FileVisitResult.CONTINUE : FileVisitResult.TERMINATE;
        }

        @Override
        public FileVisitResult visitFailed(Path file, IOException exc) throws IOException {
            if (exc instanceof java.nio.file.AccessDeniedException) {
                logger.warn("Access denied to file: {} (skipping)", file);
            } else {
                logger.warn("Failed to visit file: {}", file, exc);
            }
            skippedFiles.incrementAndGet();
            return FileVisitResult.CONTINUE;
        }

        private void startProcessingFile(Path file) {
            try {
                // Use provided chunking options
                // We create a copy to ensure thread safety if options are mutable
                FileChunker.ChunkingOptions currentOptions = new FileChunker.ChunkingOptions(options);

                // Update current file and notify listener
                currentFile = file.toString();
                setCurrentActivity("Pending...");
                if (progressListener != null) {
                    progressListener.accept(FileProcessor.this);
                }

                // Acquire permit to prevent opening too many files at once
                try {
                    fileConcurrencySemaphore.acquire();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while waiting for file processing slot", e);
                }

                // Add progress callback for granular updates
                currentOptions.withProgressCallback(bytes -> {
                    processedBytes.addAndGet(bytes);
                    if (progressListener != null) {
                        progressListener.accept(FileProcessor.this);
                    }
                });

                // Add status callback for activity updates
                currentOptions.withStatusCallback(status -> {
                    setCurrentActivity(status);
                    if (progressListener != null) {
                        progressListener.accept(FileProcessor.this);
                    }
                });

                setCurrentActivity("Reading file");

                if (progressListener != null) {
                    progressListener.accept(FileProcessor.this);
                }

                // Start chunking file
                chunkingPhaser.register();
                chunker.chunkFile(file, currentOptions)
                        .thenComposeAsync(result -> {
                            try {
                                if (result.isSuccess()) {
                                    // Enqueue for batch persistence instead of processing immediately
                                    if (!persistenceQueue.offer(result)) {
                                        logger.warn("Persistence queue full, processing immediately: {}",
                                                result.getFile());
                                        return processChunkingResultAsync(result).thenApply(v -> result);
                                    }
                                } else {
                                    if (result.getError() instanceof java.nio.file.AccessDeniedException) {
                                        if (eventListener != null) {
                                            eventListener.onEvent("SCAN_ERROR", "WARN", "Access denied",
                                                    file.toString());
                                        } else {
                                            logger.warn("Access denied for file: {}", file);
                                        }
                                        skippedFiles.incrementAndGet(); // Count as skipped, not error
                                    } else {
                                        if (eventListener != null) {
                                            eventListener.onEvent("CHUNK_ERROR", "ERROR",
                                                    result.getError() != null ? result.getError().getMessage()
                                                            : "Unknown error",
                                                    file.toString());
                                        } else {
                                            logger.error("Chunking failed for file: {}", file, result.getError());
                                        }
                                        errorFiles.incrementAndGet();
                                    }
                                }
                                return CompletableFuture.completedFuture(result);
                            } catch (Exception e) {
                                logger.error("Error processing chunking result for file: {}", file, e);
                                errorFiles.incrementAndGet();
                                IOException ioException = e instanceof IOException
                                        ? (IOException) e
                                        : new IOException(e);
                                return CompletableFuture
                                        .completedFuture(FileChunker.ChunkingResult.createFailed(file, ioException));
                            }
                        }, executorService)
                        .exceptionally(throwable -> {
                            Throwable cause = throwable;
                            if (throwable instanceof java.util.concurrent.CompletionException) {
                                cause = throwable.getCause();
                            }

                            if (throwable instanceof java.nio.file.AccessDeniedException) {
                                if (eventListener != null) {
                                    eventListener.onEvent("SCAN_ERROR", "WARN", "Access denied", file.toString());
                                } else {
                                    logger.warn("Access denied for file: {}", file);
                                }
                                skippedFiles.incrementAndGet(); // Count as skipped
                                return FileChunker.ChunkingResult.createFailed(file, (java.io.IOException) cause);
                            }

                            if (throwable instanceof java.nio.file.NoSuchFileException
                                    || throwable instanceof java.io.FileNotFoundException
                                    || (cause instanceof java.nio.file.NoSuchFileException)
                                    || (cause instanceof java.io.FileNotFoundException)) {
                                if (eventListener != null) {
                                    eventListener.onEvent("SCAN_ERROR", "WARN", "File vanished during scan",
                                            file.toString());
                                } else {
                                    logger.warn("File vanished during scan: {}", file);
                                }
                                skippedFiles.incrementAndGet(); // Count as skipped
                                return FileChunker.ChunkingResult.createFailed(file, new IOException("File vanished"));
                            }

                            if (eventListener != null) {
                                eventListener.onEvent("CHUNK_ERROR", "ERROR", throwable.getMessage(), file.toString());
                            } else {
                                logger.error("Error chunking file: {}", file, throwable);
                            }

                            errorFiles.incrementAndGet();
                            IOException ioException = throwable instanceof IOException
                                    ? (IOException) throwable
                                    : new IOException(throwable);
                            return FileChunker.ChunkingResult.createFailed(file, ioException);
                        })
                        .handle((result, throwable) -> {
                            // Always release the semaphore and arrive at phaser when the future completes
                            try {
                                fileConcurrencySemaphore.release();
                            } finally {
                                chunkingPhaser.arriveAndDeregister();
                            }

                            // Ensure we always return a valid result, even if both result and throwable are
                            // null
                            if (result == null) {
                                if (throwable != null) {
                                    if (eventListener != null) {
                                        eventListener.onEvent("ERROR", "ERROR", throwable.getMessage(),
                                                file.toString());
                                    } else {
                                        logger.error("Null result with throwable for file: {}", file, throwable);
                                    }
                                    errorFiles.incrementAndGet();
                                    IOException ioException = throwable instanceof IOException
                                            ? (IOException) throwable
                                            : new IOException(throwable);
                                    return FileChunker.ChunkingResult.createFailed(file, ioException);
                                } else {
                                    if (eventListener != null) {
                                        eventListener.onEvent("ERROR", "ERROR", "Unexpected null result",
                                                file.toString());
                                    } else {
                                        logger.error("Both result and throwable are null for file: {}", file);
                                    }
                                    errorFiles.incrementAndGet();
                                    return FileChunker.ChunkingResult.createFailed(file,
                                            new IOException("Unexpected null result without exception"));
                                }
                            }
                            return result;
                        });

            } catch (RuntimeException | IOException e) {
                logger.error("Error starting chunking for file: {}", file, e);
                // Release permit if we failed to start
                fileConcurrencySemaphore.release();
                errorFiles.incrementAndGet();
            }
        }

        public void waitForCompletion() {
            // Process the queue
            Path file;
            while ((file = processingQueue.poll()) != null && isRunning) {
                startProcessingFile(file);
            }

            try {
                // Arrive and deregister self (the main thread)
                chunkingPhaser.arriveAndDeregister();

                // Wait for all registered parties (chunking tasks) to arrive
                // We use a pragmatic timeout to avoid hanging forever
                try {
                    chunkingPhaser.awaitAdvanceInterruptibly(chunkingPhaser.getPhase(),
                            CHUNKING_TIMEOUT_HOURS, java.util.concurrent.TimeUnit.HOURS);
                } catch (java.util.concurrent.TimeoutException e) {
                    logger.error("Timeout waiting for chunking completion after {} hours", CHUNKING_TIMEOUT_HOURS, e);
                    chunkingPhaser.forceTermination();
                }

            } catch (Exception e) {
                logger.error("Error waiting for chunking completion", e);
            }
        }
    }

    private CompletableFuture<Void> processChunkingResultAsync(FileChunker.ChunkingResult result) {
        // Create file metadata with generated ID and snapshot ID
        String fileId = java.util.UUID.randomUUID().toString();
        List<String> chunkHashes = result.getChunkHashes();

        // Verify chunks exist with retries (async)
        List<CompletableFuture<Boolean>> checks = new ArrayList<>();
        for (String chunkHash : chunkHashes) {
            checks.add(verifyChunkExistsAsync(chunkHash));
        }

        return CompletableFuture.allOf(checks.toArray(new CompletableFuture<?>[0]))
                .thenCompose(v -> {
                    boolean allChunksExist = checks.stream().allMatch(CompletableFuture::join);

                    if (!allChunksExist) {
                        logger.warn("Not all chunks exist for file {}, skipping file processing", result.getFile());
                        skippedFiles.incrementAndGet();
                        return CompletableFuture.completedFuture(null);
                    }

                    FileMetadata fileMetadata = new FileMetadata(
                            fileId,
                            currentSnapshotId,
                            result.getFile().toString(),
                            result.getTotalSize(),
                            Instant.now(),
                            result.getFileHash(),
                            chunkHashes);

                    return storeFileMetadataWithRetryAsync(fileMetadata, chunkHashes, result)
                            .thenAccept(ignore -> {
                                processedFiles.incrementAndGet();
                                logger.debug("Completed processing file: {} ({} chunks)",
                                        result.getFile(), result.getChunkCount());

                                setCurrentActivity("Completed");

                                if (progressListener != null) {
                                    progressListener.accept(FileProcessor.this);
                                }
                            })
                            .exceptionally(ex -> {
                                logger.error("Error storing metadata for file: {}", result.getFile(), ex);
                                errorFiles.incrementAndGet();
                                return null;
                            });
                });
    }

    /**
     * Verifies that a chunk exists in the content store with retries (async).
     *
     * @param chunkHash the hash of the chunk to verify
     * @return a future that completes with true if the chunk exists, false
     *         otherwise
     */
    private CompletableFuture<Boolean> verifyChunkExistsAsync(String chunkHash) {
        return verifyChunkExistsAsync(chunkHash, 1);
    }

    private CompletableFuture<Boolean> verifyChunkExistsAsync(String chunkHash, int attempt) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        try {
            if (contentStore.existsChunk(chunkHash)) {
                logger.debug("Chunk {} is visible (attempt {}/{})", chunkHash, attempt, MAX_RETRIES);
                future.complete(true);
            } else {
                if (attempt < MAX_RETRIES) {
                    logger.debug("Chunk {} not yet visible (attempt {}/{}), waiting...",
                            chunkHash, attempt, MAX_RETRIES);
                    // Calculate delay with exponential backoff
                    long delayMs = Math.min(INITIAL_BACKOFF_MS * (1L << (attempt - 1)), MAX_BACKOFF_MS);

                    scheduler.schedule(() -> {
                        verifyChunkExistsAsync(chunkHash, attempt + 1)
                                .thenAccept(future::complete)
                                .exceptionally(ex -> {
                                    future.completeExceptionally(ex);
                                    return null;
                                });
                    }, delayMs, TimeUnit.MILLISECONDS);
                } else {
                    logger.error("Chunk {} does not exist in content store after {} retries", chunkHash, MAX_RETRIES);
                    future.complete(false);
                }
            }
        } catch (IOException e) {
            if (attempt < MAX_RETRIES) {
                logger.warn("Failed to check if chunk exists: {} (attempt {}/{}), retrying...",
                        chunkHash, attempt, MAX_RETRIES, e);
                long delayMs = Math.min(INITIAL_BACKOFF_MS * (1L << (attempt - 1)), MAX_BACKOFF_MS);
                scheduler.schedule(() -> {
                    verifyChunkExistsAsync(chunkHash, attempt + 1)
                            .thenAccept(future::complete)
                            .exceptionally(ex -> {
                                future.completeExceptionally(ex);
                                return null;
                            });
                }, delayMs, TimeUnit.MILLISECONDS);
            } else {
                logger.error("Failed to check if chunk exists: {} after {} retries", chunkHash, MAX_RETRIES, e);
                future.complete(false);
            }
        }
        return future;
    }

    /**
     * Stores file metadata with retry logic for foreign key constraints (async).
     */
    private CompletableFuture<Void> storeFileMetadataWithRetryAsync(FileMetadata fileMetadata, List<String> chunkHashes,
            FileChunker.ChunkingResult result) {
        return storeFileMetadataWithRetryAsync(fileMetadata, chunkHashes, result, 1);
    }

    private CompletableFuture<Void> storeFileMetadataWithRetryAsync(FileMetadata fileMetadata, List<String> chunkHashes,
            FileChunker.ChunkingResult result, int attempt) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        executorService.submit(() -> {
            Transaction transaction = null;
            try {
                // Begin transaction for file metadata
                transaction = metadataService.beginTransaction();

                // Ensure all chunk metadata exists before inserting file
                ensureChunkMetadataExists(chunkHashes);

                // Now insert file metadata
                metadataService.insertFile(fileMetadata);
                transaction.commit();
                // Success
                future.complete(null);
            } catch (Exception e) {
                if (transaction != null) {
                    try {
                        transaction.rollback();
                    } catch (Exception rollbackEx) {
                        logger.warn("Failed to rollback transaction for file {}: {}",
                                result.getFile(), rollbackEx.getMessage());
                    }
                }

                // Check if retryable
                boolean retryable = false;
                if (e instanceof IOException) {
                    String msg = e.getMessage();
                    if (msg != null && (msg.contains("FOREIGN KEY constraint failed")
                            || msg.contains("SQLITE_CONSTRAINT_FOREIGNKEY")
                            || msg.contains("Not all chunk metadata is visible"))) {
                        retryable = true;
                    }
                }

                if (retryable && attempt < MAX_RETRIES) {
                    // Adjusted backoff: 200ms, 400ms, 600ms, 800ms
                    long delayMs = 200L * attempt;
                    logger.warn(
                            "Chunk metadata visibility issue for file {} (attempt {}), retrying after {}ms...",
                            result.getFile(), attempt, delayMs);

                    scheduler.schedule(() -> {
                        storeFileMetadataWithRetryAsync(fileMetadata, chunkHashes, result, attempt + 1)
                                .thenAccept(v -> future.complete(null))
                                .exceptionally(ex -> {
                                    future.completeExceptionally(ex);
                                    return null;
                                });
                    }, delayMs, TimeUnit.MILLISECONDS);
                } else if (e.getMessage() != null && e.getMessage().contains("Snapshot does not exist")) {
                    logger.error("Snapshot missing during processing: {}. Aborting FileProcessor.", result.getFile());
                    stop();
                    future.completeExceptionally(new IOException("Snapshot missing, aborting processing", e));
                } else {
                    future.completeExceptionally(e);
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
        });

        return future;
    }

    /**
     * Ensures that all chunk metadata exists before inserting file metadata.
     *
     * @param chunkHashes the list of chunk hashes to verify
     * @throws InterruptedException if the thread is interrupted
     * @throws IOException          if there's an error creating chunk metadata
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
                        java.time.Instant.now());
                metadataService.upsertChunk(chunkMetadata);
                logger.debug("Created missing chunk metadata for: {}", chunkHash);
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
                detectedFiles.incrementAndGet();
                logger.debug("Processed file: {} ({} bytes)", file, fileSize);
            } catch (java.nio.file.NoSuchFileException e) {
                logger.debug("File processed but no longer exists (skipping size check): {}", file);
            } catch (IOException e) {
                logger.warn("Could not get size for file: {}", file, e);
            }
            if (progressListener != null) {
                setCurrentActivity("Scanning: " + file.getFileName());
                progressListener.accept(FileProcessor.this);
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
         *
         * @deprecated Use {@link #create(ScanResult, int, int, int, long, long)}
         *             instead.
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
         * @param scanResult     the scan result from the filesystem scanner
         * @param processedFiles the number of successfully processed files
         * @param skippedFiles   the number of skipped files
         * @param errorFiles     the number of files with errors
         * @param totalBytes     the total bytes in all files
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

    // Public getters for live progress monitoring

    /**
     * @return Current number of processed files.
     */
    public int getProcessedFilesCount() {
        return processedFiles.get();
    }

    /**
     * @return Current number of skipped files.
     */
    public int getSkippedFilesCount() {
        return skippedFiles.get();
    }

    /**
     * @return Current number of files with errors.
     */
    public int getErrorFilesCount() {
        return errorFiles.get();
    }

    /**
     * @return Current number of detected files.
     */
    public int getDetectedFilesCount() {
        return detectedFiles.get();
    }

    /**
     * @return Current total bytes discovered.
     */
    public long getTotalBytesCount() {
        return totalBytes.get();
    }

    /**
     * @return Current total bytes processed.
     */
    public long getProcessedBytesCount() {
        return processedBytes.get();
    }

    public double getProcessingPercentage() {
        return totalBytes.get() > 0 ? (double) processedBytes.get() / totalBytes.get() * 100 : 0;
    }

    /**
     * Worker that processes the persistence queue in batches.
     */
    private class PersistenceWorker implements Runnable {
        private volatile boolean shouldRun = true;

        public void shutdown() {
            shouldRun = false;
        }

        @Override
        public void run() {
            List<FileChunker.ChunkingResult> batch = new ArrayList<>();
            while (shouldRun || !persistenceQueue.isEmpty()) {
                try {
                    // Drain queue to batch
                    if (batch.isEmpty()) {
                        FileChunker.ChunkingResult result = persistenceQueue.poll(100, TimeUnit.MILLISECONDS);
                        if (result != null) {
                            batch.add(result);
                        }
                    }
                    persistenceQueue.drainTo(batch, BATCH_SIZE - 1); // Max BATCH_SIZE items per batch

                    if (!batch.isEmpty()) {
                        processBatch(batch);
                        batch.clear();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    logger.error("Error in persistence worker", e);
                }
            }

            // Process any remaining items
            if (!persistenceQueue.isEmpty()) {
                batch.clear();
                persistenceQueue.drainTo(batch);
                if (!batch.isEmpty()) {
                    processBatch(batch);
                }
            }
        }

        private void processBatch(List<FileChunker.ChunkingResult> batch) {
            try {
                List<FileMetadata> metadataList = new ArrayList<>();
                List<FileChunker.ChunkingResult> validResults = new ArrayList<>();

                for (FileChunker.ChunkingResult result : batch) {
                    try {
                        // Create file metadata
                        String fileId = java.util.UUID.randomUUID().toString();
                        FileMetadata fileMetadata = new FileMetadata(
                                fileId,
                                currentSnapshotId,
                                result.getFile().toString(),
                                result.getTotalSize(),
                                Instant.now(),
                                result.getFileHash(),
                                result.getChunkHashes());

                        metadataList.add(fileMetadata);
                        validResults.add(result);
                    } catch (Exception e) {
                        logger.error("Error preparing metadata for file: {}", result.getFile(), e);
                        errorFiles.incrementAndGet();
                    }
                }

                if (metadataList.isEmpty()) {
                    return;
                }

                try {
                    // Batch insert
                    metadataService.insertFiles(metadataList);

                    // Update stats for all successful files
                    int successCount = validResults.size();
                    processedFiles.addAndGet(successCount);

                    setCurrentActivity("Persisting batch..." + successCount);
                    if (progressListener != null) {
                        progressListener.accept(FileProcessor.this);
                    }

                } catch (IOException e) {
                    logger.error("Batch insert failed, falling back to individual processing", e);
                    // Fallback to individual processing
                    for (FileChunker.ChunkingResult result : validResults) {
                        try {
                            FileProcessor.this.processChunkingResultAsync(result).join();
                        } catch (Exception ex) {
                            logger.error("Error processing fallback result for file: {}", result.getFile(), ex);
                        }
                    }
                }

                // Briefly show completion specific to this batch size before loop continues or
                // waits
                // But don't overwrite if we are immediately grabbing next batch
                // Only if queue is empty might we want to indicate idle?

                // briefly show completion specific to this batch size before loop continues or
                // waits
                setCurrentActivity("Persisted batch of " + validResults.size() + " (Done)");
                if (progressListener != null) {
                    progressListener.accept(FileProcessor.this);
                }

            } catch (Exception e) {
                logger.error("Unexpected error in batch processing", e);
            }
        }
    }

    /**
     * Processes a single file using the given options.
     *
     * @param file            the file to process
     * @param chunkingOptions the chunking options
     * @return a future that completes with the processing result
     */
    public CompletableFuture<ProcessingResult> processFile(Path file, FileChunker.ChunkingOptions chunkingOptions) {
        if (executorService.isShutdown()) {
            throw new IllegalStateException("FileProcessor has been stopped");
        }

        return CompletableFuture.supplyAsync(() -> {
            if (isRunning) {
                // For MVP single-file processing, we might want to allow this if we manage
                // concurrency.
                // But keeping same restrictions as processDirectory for safety.
                throw new IllegalStateException("FileProcessor is already running");
            }
            if (file == null || !Files.exists(file) || !Files.isRegularFile(file)) {
                throw new IllegalArgumentException("File must exist and be regular file: " + file);
            }

            isRunning = true;
            resetCounters();

            boolean externalSnapshot = (currentSnapshotId != null);
            if (!externalSnapshot) {
                currentSnapshotId = "processing-file-" + System.currentTimeMillis();
            }

            try {
                // Initialize persistence worker
                PersistenceWorker worker = new PersistenceWorker();
                persistenceThread = new Thread(worker, "PersistenceWorker-" + currentSnapshotId);
                persistenceThread.start();

                // Create visitor
                ChunkingFileVisitor fileVisitor = new ChunkingFileVisitor(chunkingOptions, currentSnapshotId);

                // Directly start processing the file
                fileVisitor.startProcessingFile(file);

                // Wait for completion
                fileVisitor.waitForCompletion();

                // Shutdown worker
                worker.shutdown();
                try {
                    persistenceThread.join(PERSISTENCE_SHUTDOWN_TIMEOUT_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                // Construct ScannedFile for result
                long fileSize = Files.size(file);
                Instant lastMod = Files.getLastModifiedTime(file).toInstant();
                ScanResult.ScannedFile scannedFile = new ScanResult.ScannedFile(file, fileSize, lastMod, false, false,
                        null);

                ProcessingResult result = ProcessingResult.create(
                        new ScanResult(file.getParent(), List.of(scannedFile), null, Instant.now(), Instant.now(),
                                null),
                        processedFiles.get(),
                        skippedFiles.get(),
                        errorFiles.get(),
                        totalBytes.get(),
                        processedBytes.get());

                return result;

            } catch (Exception e) {
                logger.error("Error processing file: " + file, e);
                // Wrap in CompletionException if not already
                if (e instanceof CompletionException)
                    throw (CompletionException) e;
                throw new CompletionException(e);
            } finally {
                isRunning = false;
                if (!externalSnapshot)
                    currentSnapshotId = null;
            }
        }, executorService);
    }
}