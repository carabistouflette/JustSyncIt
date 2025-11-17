package com.justsyncit.scanner;

import com.justsyncit.storage.ContentStore;
import com.justsyncit.storage.metadata.FileMetadata;
import com.justsyncit.storage.metadata.MetadataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
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
    private static final Logger logger = LoggerFactory.getLogger(FileProcessor.class);
    
    private final FilesystemScanner scanner;
    private final FileChunker chunker;
    private final ContentStore contentStore;
    private final MetadataService metadataService;
    private final ExecutorService executorService;
    
    private volatile boolean isRunning = false;
    private final AtomicInteger processedFiles = new AtomicInteger(0);
    private final AtomicInteger skippedFiles = new AtomicInteger(0);
    private final AtomicInteger errorFiles = new AtomicInteger(0);
    private final AtomicLong totalBytes = new AtomicLong(0);
    private final AtomicLong processedBytes = new AtomicLong(0);
    private String currentSnapshotId;
    
    /**
     * Creates a new FileProcessor with the specified dependencies.
     */
    public FileProcessor(FilesystemScanner scanner, FileChunker chunker, 
                        ContentStore contentStore, MetadataService metadataService) {
        this.scanner = scanner;
        this.chunker = chunker;
        this.contentStore = contentStore;
        this.metadataService = metadataService;
        this.executorService = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors(),
            r -> {
                Thread t = new Thread(r, "FileProcessor-" + System.currentTimeMillis());
                t.setDaemon(true);
                return t;
            });
    }
    
    /**
     * Processes files from the specified directory using the given options.
     */
    public CompletableFuture<ProcessingResult> processDirectory(Path directory, ScanOptions options) {
        return CompletableFuture.supplyAsync(() -> {
            isRunning = true;
            resetCounters();
            
            // Create a single snapshot for this processing session
            currentSnapshotId = "processing-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8);
            
            logger.info("Starting file processing for directory: {} with snapshot: {}", directory, currentSnapshotId);
            
            try {
                // Create snapshot first
                try {
                    metadataService.createSnapshot(currentSnapshotId, "Processing session for directory: " + directory);
                } catch (IOException e) {
                    logger.warn("Failed to create snapshot: {}", e.getMessage());
                    // Continue anyway, we'll handle the foreign key issue gracefully
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
                
                ProcessingResult result = new ProcessingResult(
                    scanResult,
                    processedFiles.get(),
                    skippedFiles.get(),
                    errorFiles.get(),
                    totalBytes.get(),
                    processedBytes.get()
                );
                
                logger.info("File processing completed. Processed: {}, Skipped: {}, Errors: {}, Total bytes: {}",
                    result.getProcessedFiles(), result.getSkippedFiles(), result.getErrorFiles(), result.getTotalBytes());
                
                return result;
                
            } catch (Exception e) {
                logger.error("Error during file processing", e);
                throw new RuntimeException("File processing failed", e);
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
        executorService.shutdown();
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
                
                // Start chunking the file
                CompletableFuture<FileChunker.ChunkingResult> chunkingFuture = chunker.chunkFile(file, options)
                    .thenCompose(result -> {
                        return CompletableFuture.supplyAsync(() -> {
                            if (result.isSuccess()) {
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
                        return new FileChunker.ChunkingResult(file,
                            throwable instanceof IOException ? (IOException) throwable : new IOException(throwable));
                    });
                
                chunkingFutures.add(chunkingFuture);
                
            } catch (Exception e) {
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
                
                FileMetadata fileMetadata = new FileMetadata(
                    fileId,
                    currentSnapshotId,
                    result.getFile().toString(),
                    result.getTotalSize(),
                    Instant.now(),
                    result.getFileHash(),
                    result.getChunkHashes()
                );
                
                // Store file metadata
                metadataService.insertFile(fileMetadata);
                
                processedFiles.incrementAndGet();
                processedBytes.addAndGet(result.getTotalSize());
                
                logger.debug("Completed processing file: {} ({} chunks)",
                    result.getFile(), result.getChunkCount());
                
            } catch (IOException e) {
                logger.error("Error storing metadata for file: {}", result.getFile(), e);
                errorFiles.incrementAndGet();
            }
        }
        
        public void waitForCompletion() {
            try {
                CompletableFuture.allOf(chunkingFutures.toArray(new CompletableFuture[0])).get();
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
        private final ScanResult scanResult;
        private final int processedFiles;
        private final int skippedFiles;
        private final int errorFiles;
        private final long totalBytes;
        private final long processedBytes;
        
        public ProcessingResult(ScanResult scanResult, int processedFiles, int skippedFiles, 
                              int errorFiles, long totalBytes, long processedBytes) {
            this.scanResult = scanResult;
            this.processedFiles = processedFiles;
            this.skippedFiles = skippedFiles;
            this.errorFiles = errorFiles;
            this.totalBytes = totalBytes;
            this.processedBytes = processedBytes;
        }
        
        public ScanResult getScanResult() { return scanResult; }
        public int getProcessedFiles() { return processedFiles; }
        public int getSkippedFiles() { return skippedFiles; }
        public int getErrorFiles() { return errorFiles; }
        public long getTotalBytes() { return totalBytes; }
        public long getProcessedBytes() { return processedBytes; }
        
        public double getProcessingPercentage() {
            return totalBytes > 0 ? (double) processedBytes / totalBytes * 100 : 0;
        }
    }
}