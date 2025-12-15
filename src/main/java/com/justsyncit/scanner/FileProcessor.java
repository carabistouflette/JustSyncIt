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
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
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
                    persistenceThread.join(30000); // Wait up to 30s for persistence to finish
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
        /** List of futures for chunking operations. */
        private final List<CompletableFuture<FileChunker.ChunkingResult>> chunkingFutures = new ArrayList<>();
        /** Lock for synchronizing access to chunkingFutures list. */
        private final Object chunkingFuturesLock = new Object();
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
            logger.warn("Failed to visit file: {}", file, exc);
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
                CompletableFuture<FileChunker.ChunkingResult> chunkingFuture = chunker.chunkFile(file, currentOptions)
                        .thenCompose(result -> {
                            return CompletableFuture.supplyAsync(() -> {
                                try {
                                    if (result.isSuccess()) {
                                        // Enqueue for batch persistence instead of processing immediately
                                        if (!persistenceQueue.offer(result)) {
                                            logger.warn("Persistence queue full, processing immediately: {}",
                                                    result.getFile());
                                            processChunkingResult(result);
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
                                    return result;
                                } catch (Exception e) {
                                    logger.error("Error processing chunking result for file: {}", file, e);
                                    errorFiles.incrementAndGet();
                                    IOException ioException = e instanceof IOException
                                            ? (IOException) e
                                            : new IOException(e);
                                    return FileChunker.ChunkingResult.createFailed(file, ioException);
                                }
                            }, executorService);
                        })
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
                            // Always release the semaphore when the future completes (success or failure)
                            fileConcurrencySemaphore.release();

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

                synchronized (chunkingFuturesLock) {
                    chunkingFutures.add(chunkingFuture);
                }

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
                // Filter out null futures to avoid ForEachOps issues
                List<CompletableFuture<FileChunker.ChunkingResult>> validFutures;
                synchronized (chunkingFuturesLock) {
                    validFutures = new ArrayList<>();
                    for (CompletableFuture<FileChunker.ChunkingResult> future : chunkingFutures) {
                        if (future != null) {
                            validFutures.add(future);
                        }
                    }
                }

                if (validFutures.isEmpty()) {
                    logger.debug("No valid futures to wait for");
                    return;
                }

                // Add timeout to prevent infinite hanging
                // Use the filtered list to create the array for allOf()
                // Ensure no null elements in array to prevent ForEachOps issues
                @SuppressWarnings("unchecked")
                CompletableFuture<FileChunker.ChunkingResult>[] futuresArray = (CompletableFuture<FileChunker.ChunkingResult>[]) validFutures
                        .toArray(
                                new CompletableFuture<?>[validFutures.size()]);
                CompletableFuture.allOf(futuresArray)
                        .get(24, java.util.concurrent.TimeUnit.HOURS); // Increased timeout for large backups
            } catch (java.util.concurrent.TimeoutException e) {
                logger.error("Timeout waiting for chunking completion after 24 hours", e);
                // Cancel any remaining futures
                synchronized (chunkingFuturesLock) {
                    // Create a copy to avoid concurrent modification
                    List<CompletableFuture<FileChunker.ChunkingResult>> futuresCopy = new ArrayList<>(chunkingFutures);
                    for (CompletableFuture<FileChunker.ChunkingResult> future : futuresCopy) {
                        if (future != null && !future.isDone()) {
                            future.cancel(true);
                        }
                    }
                }
            } catch (Exception e) {
                logger.error("Error waiting for chunking completion", e);
            }
        }
    }

    private void processChunkingResult(FileChunker.ChunkingResult result) {
        try {
            // Create file metadata with generated ID and snapshot ID
            String fileId = java.util.UUID.randomUUID().toString();

            // The FixedSizeFileChunker now handles chunk storage automatically
            // when it has a content store set.
            List<String> chunkHashes = result.getChunkHashes();

            // Verification of chunks happens below with retries, effectively handling
            // any slight delay in DB visibility without blocking the thread for 3s.

            // Verify chunks exist with retries
            boolean allChunksExist = true;
            for (String chunkHash : chunkHashes) {
                if (!verifyChunkExists(chunkHash)) {
                    allChunksExist = false;
                    // Continue checking other chunks to give them more time to appear
                    logger.warn("Chunk {} verification failed, continuing with other chunks", chunkHash);
                }
            }

            // If not all chunks exist, don't process this file but don't count as error
            // This allows for transient storage issues to resolve themselves
            if (!allChunksExist) {
                logger.warn("Not all chunks exist for file {}, skipping file processing", result.getFile());
                skippedFiles.incrementAndGet();
                // bytes are already added via progress callback
                return;
            }

            FileMetadata fileMetadata = new FileMetadata(
                    fileId,
                    currentSnapshotId,
                    result.getFile().toString(),
                    result.getTotalSize(),
                    Instant.now(),
                    result.getFileHash(),
                    chunkHashes);
            // Store file metadata with retry for foreign key constraint
            storeFileMetadataWithRetry(fileMetadata, chunkHashes, result);

            processedFiles.incrementAndGet();
            // bytes are already added via progress callback
            logger.debug("Completed processing file: {} ({} chunks)",
                    result.getFile(), result.getChunkCount());

            setCurrentActivity("Completed");

            if (progressListener != null) {
                progressListener.accept(FileProcessor.this);
            }

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
        int maxRetries = 15; // Increased retries for better reliability
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
                        // Use exponential backoff for better reliability
                        // 200ms, 400ms, 800ms, 1600ms, 3200ms, 6400ms, etc.
                        long delayMs = 200L * (1L << (attempt - 1));
                        // Cap at 5 seconds to avoid excessive delays
                        delayMs = Math.min(delayMs, 5000L);
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
                        long delayMs = 200L * (1L << (attempt - 1));
                        delayMs = Math.min(delayMs, 5000L);
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
            // Don't increment error count here - let the caller handle it
            // This allows for more graceful handling of transient failures
            return false;
        }
        return true;
    }

    /**
     * Stores file metadata with retry logic for foreign key constraints.
     *
     * @param fileMetadata the file metadata to store
     * @param chunkHashes  the list of chunk hashes
     * @param result       the chunking result
     * @throws IOException if storing fails after all retries
     */
    private void storeFileMetadataWithRetry(FileMetadata fileMetadata, List<String> chunkHashes,
            FileChunker.ChunkingResult result) throws IOException {
        int maxRetries = 12; // Increased retries for better reliability
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
                // If it's a foreign key constraint or visibility issue, chunks might not be
                // visible yet
                if (e.getMessage() != null
                        && (e.getMessage().contains("FOREIGN KEY constraint failed")
                                || e.getMessage().contains("SQLITE_CONSTRAINT_FOREIGNKEY")
                                || e.getMessage().contains("Not all chunk metadata is visible"))) {
                    if (attempt < maxRetries) {
                        // Increased backoff: 400ms, 800ms, 1200ms, 1600ms, 2000ms, 2400ms,
                        // 2800ms, 3200ms, 3600ms, 4000ms, 4400ms, 4800ms
                        long delayMs = 400L * attempt;
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
                    persistenceQueue.drainTo(batch, 199); // Max 200 items per batch

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
                        FileProcessor.this.processChunkingResult(result);
                    }
                }
            } catch (Exception e) {
                logger.error("Unexpected error in batch processing", e);
            }
        }
    }
}