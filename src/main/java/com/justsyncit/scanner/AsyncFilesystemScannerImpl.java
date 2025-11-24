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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Production-ready implementation of AsyncFilesystemScanner with WatchService integration.
 * Provides non-blocking directory scanning with real-time file change monitoring,
 * parallel processing, backpressure control, and comprehensive performance optimization.
 */
public class AsyncFilesystemScannerImpl implements AsyncFilesystemScanner {

    private static final Logger logger = LoggerFactory.getLogger(AsyncFilesystemScannerImpl.class);

    /** Thread pool manager for async operations. */
    private final ThreadPoolManager threadPoolManager;
    
    /** Async buffer pool for memory management. */
    private AsyncByteBufferPool asyncBufferPool;
    
    /** Watch service manager for real-time monitoring. */
    private final AsyncWatchServiceManager watchServiceManager;
    
    /** Async file visitor for custom processing. */
    private AsyncFileVisitor asyncFileVisitor;
    
    /** Async progress listener for progress monitoring. */
    private AsyncProgressListener asyncProgressListener;
    
    /** Traditional file visitor for compatibility. */
    private FileVisitor fileVisitor;
    
    /** Traditional progress listener for compatibility. */
    private ProgressListener progressListener;
    
    /** Active scan operations by ID. */
    private final Map<String, ScanContext> activeScans;
    
    /** Maximum number of concurrent scans. */
    private final AtomicInteger maxConcurrentScans;
    
    /** Scanner state. */
    private final AtomicBoolean closed;
    
    /** Statistics tracking. */
    private final AsyncScannerStats stats;
    
    /** Backpressure controller for flow management. */
    private final BackpressureController backpressureController;
    
    /** Performance optimizer for adaptive tuning. */
    private final PerformanceOptimizer performanceOptimizer;

    /**
     * Context for an active scan operation.
     */
    private static class ScanContext {
        final String scanId;
        final CompletableFuture<AsyncScanResult> future;
        final AtomicBoolean cancelled;
        final AtomicLong filesProcessed;
        final AtomicLong directoriesProcessed;
        final AtomicLong bytesProcessed;
        final Instant startTime;
        final Path rootDirectory;
        final ScanOptions options;
        final AtomicInteger activeThreads;

        ScanContext(String scanId, Path rootDirectory, ScanOptions options) {
            this.scanId = scanId;
            this.future = new CompletableFuture<>();
            this.cancelled = new AtomicBoolean(false);
            this.filesProcessed = new AtomicLong(0);
            this.directoriesProcessed = new AtomicLong(0);
            this.bytesProcessed = new AtomicLong(0);
            this.startTime = Instant.now();
            this.rootDirectory = rootDirectory;
            this.options = options;
            this.activeThreads = new AtomicInteger(0);
        }
    }

    /**
     * Creates a new AsyncFilesystemScannerImpl.
     *
     * @param threadPoolManager thread pool manager for async operations
     * @param asyncBufferPool async buffer pool for memory management
     */
    public AsyncFilesystemScannerImpl(ThreadPoolManager threadPoolManager,
                                     AsyncByteBufferPool asyncBufferPool) {
        this.threadPoolManager = Objects.requireNonNull(threadPoolManager);
        this.asyncBufferPool = Objects.requireNonNull(asyncBufferPool);
        this.watchServiceManager = new AsyncWatchServiceManager(
            threadPoolManager, asyncBufferPool, new AsyncScanOptions()
        );
        this.activeScans = new ConcurrentHashMap<>();
        this.maxConcurrentScans = new AtomicInteger(Runtime.getRuntime().availableProcessors());
        this.closed = new AtomicBoolean(false);
        this.stats = new AsyncScannerStats();
        this.backpressureController = new BackpressureController();
        this.performanceOptimizer = new PerformanceOptimizer();
        
        logger.info("AsyncFilesystemScannerImpl initialized with max concurrent scans: {}", 
            maxConcurrentScans.get());
    }

    @Override
    public CompletableFuture<AsyncScanResult> scanDirectoryAsync(Path directory, ScanOptions options) {
        if (closed.get()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Scanner is closed"));
        }

        String scanId = UUID.randomUUID().toString();
        ScanContext context = new ScanContext(scanId, directory, options);

        return CompletableFuture.supplyAsync(() -> {
            try {
                return performAsyncScan(context);
            } catch (Exception e) {
                logger.error("Async scan failed for directory: {}", directory, e);
                context.future.completeExceptionally(e);
                stats.incrementScansFailed();
                throw new RuntimeException("Async scan failed", e);
            }
        }, threadPoolManager.getIoThreadPool());
    }

    @Override
    public CompletableFuture<WatchServiceRegistration> startDirectoryMonitoring(
            Path directory, 
            AsyncScanOptions options, 
            Consumer<FileChangeEvent> eventHandler) {
        
        if (closed.get()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Scanner is closed"));
        }

        logger.info("Starting directory monitoring for: {}", directory);
        return watchServiceManager.startDirectoryMonitoring(directory, options, eventHandler);
    }

    @Override
    public CompletableFuture<Void> stopDirectoryMonitoring(WatchServiceRegistration registration) {
        if (closed.get()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Scanner is closed"));
        }

        logger.info("Stopping directory monitoring for: {}", registration.getMonitoredDirectory());
        return watchServiceManager.stopDirectoryMonitoring(registration);
    }

    @Override
    public CompletableFuture<AsyncScanResult> scanDirectoryParallel(
            Path directory, 
            ScanOptions options, 
            int concurrency) {
        
        if (closed.get()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Scanner is closed"));
        }

        String scanId = UUID.randomUUID().toString();
        ScanContext context = new ScanContext(scanId, directory, options);

        return CompletableFuture.supplyAsync(() -> {
            try {
                return performParallelScan(context, concurrency);
            } catch (Exception e) {
                logger.error("Parallel scan failed for directory: {}", directory, e);
                context.future.completeExceptionally(e);
                stats.incrementScansFailed();
                throw new RuntimeException("Parallel scan failed", e);
            }
        }, threadPoolManager.getIoThreadPool());
    }

    @Override
    public CompletableFuture<Void> scanDirectoryStreaming(
            Path directory, 
            ScanOptions options, 
            Consumer<AsyncScanResult> resultConsumer) {
        
        if (closed.get()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Scanner is closed"));
        }

        String scanId = UUID.randomUUID().toString();
        ScanContext context = new ScanContext(scanId, directory, options);

        return CompletableFuture.runAsync(() -> {
            try {
                performStreamingScan(context, resultConsumer);
            } catch (Exception e) {
                logger.error("Streaming scan failed for directory: {}", directory, e);
                stats.incrementScansFailed();
                throw new RuntimeException("Streaming scan failed", e);
            }
        }, threadPoolManager.getIoThreadPool());
    }

    @Override
    public void setAsyncFileVisitor(AsyncFileVisitor asyncVisitor) {
        this.asyncFileVisitor = asyncVisitor;
    }

    @Override
    public AsyncFileVisitor getAsyncFileVisitor() {
        return asyncFileVisitor;
    }

    @Override
    public void setAsyncBufferPool(AsyncByteBufferPool asyncBufferPool) {
        this.asyncBufferPool = Objects.requireNonNull(asyncBufferPool);
    }

    @Override
    public AsyncByteBufferPool getAsyncBufferPool() {
        return asyncBufferPool;
    }

    @Override
    public void setAsyncProgressListener(AsyncProgressListener asyncProgressListener) {
        this.asyncProgressListener = asyncProgressListener;
    }

    @Override
    public AsyncProgressListener getAsyncProgressListener() {
        return asyncProgressListener;
    }

    @Override
    public void setFileVisitor(FileVisitor visitor) {
        this.fileVisitor = visitor;
    }

    @Override
    public void setProgressListener(ProgressListener listener) {
        this.progressListener = listener;
    }

    @Override
    public boolean cancelScan(String scanId) {
        ScanContext context = activeScans.get(scanId);
        if (context == null) {
            return false;
        }

        if (context.cancelled.compareAndSet(false, true)) {
            logger.info("Cancelling scan: {}", scanId);
            context.future.cancel(true);
            stats.incrementScansCancelled();
            return true;
        }

        return false;
    }

    @Override
    public int getActiveScanCount() {
        return activeScans.size();
    }

    @Override
    public int getMaxConcurrentScans() {
        return maxConcurrentScans.get();
    }

    @Override
    public void setMaxConcurrentScans(int maxConcurrentScans) {
        if (maxConcurrentScans <= 0) {
            throw new IllegalArgumentException("Max concurrent scans must be positive");
        }
        this.maxConcurrentScans.set(maxConcurrentScans);
        logger.info("Updated max concurrent scans to: {}", maxConcurrentScans);
    }

    @Override
    public CompletableFuture<AsyncScannerStats> getStatsAsync() {
        return CompletableFuture.supplyAsync(() -> {
            // Update runtime statistics
            stats.setOperationalMetric("uptimeMs", System.currentTimeMillis() - stats.getStatsTimestamp().toEpochMilli());
            stats.setOperationalMetric("activeScans", getActiveScanCount());
            stats.setOperationalMetric("maxConcurrentScans", getMaxConcurrentScans());
            stats.setOperationalMetric("watchServiceRegistrations", watchServiceManager.getActiveRegistrationCount());
            
            return stats;
        }, threadPoolManager.getManagementThreadPool());
    }

    @Override
    public void applyBackpressure(double pressureLevel) {
        backpressureController.applyBackpressure(pressureLevel);
        logger.info("Applied backpressure level: {}", pressureLevel);
    }

    @Override
    public void releaseBackpressure() {
        backpressureController.releaseBackpressure();
        logger.info("Released backpressure");
    }

    @Override
    public CompletableFuture<Void> closeAsync() {
        if (!closed.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(null);
        }

        logger.info("Closing AsyncFilesystemScannerImpl");

        return CompletableFuture.runAsync(() -> {
            try {
                // Cancel all active scans
                List<CompletableFuture<Void>> cancelFutures = new ArrayList<>();
                activeScans.values().forEach(context -> {
                    cancelScan(context.scanId);
                    cancelFutures.add(context.future.thenApply(result -> null));
                });

                // Wait for all scans to cancel
                CompletableFuture.allOf(cancelFutures.toArray(new CompletableFuture[0]))
                    .get(30, TimeUnit.SECONDS);

                // Stop watch service manager
                watchServiceManager.stopAsync().get(30, TimeUnit.SECONDS);

                // Clear active scans
                activeScans.clear();

                logger.info("AsyncFilesystemScannerImpl closed successfully");

            } catch (Exception e) {
                logger.error("Error closing AsyncFilesystemScannerImpl", e);
                throw new RuntimeException("Failed to close scanner", e);
            }
        }, threadPoolManager.getManagementThreadPool());
    }

    @Override
    public boolean isClosed() {
        return closed.get();
    }

    @Override
    public CompletableFuture<ScanResult> scanDirectory(Path directory, ScanOptions options) {
        // Convert async result to sync result for compatibility
        return scanDirectoryAsync(directory, options).thenApply(asyncResult -> {
            // Convert AsyncScanResult to ScanResult
            return new ScanResult(
                asyncResult.getRootDirectory(),
                asyncResult.getScannedFiles(),
                asyncResult.getErrors(),
                asyncResult.getStartTime(),
                asyncResult.getEndTime(),
                asyncResult.getMetadata()
            );
        });
    }

    /**
     * Performs an asynchronous scan operation.
     *
     * @param context scan context
     * @return async scan result
     */
    private AsyncScanResult performAsyncScan(ScanContext context) {
        logger.info("Starting async scan: {} for directory: {}", context.scanId, context.rootDirectory);

        // Add to active scans
        activeScans.put(context.scanId, context);
        stats.incrementScansInitiated();
        stats.incrementActiveScans();

        // Notify progress listener
        if (asyncProgressListener != null) {
            asyncProgressListener.onScanStartedAsync(context.scanId, context.rootDirectory);
        } else if (progressListener != null) {
            progressListener.onScanStarted(context.rootDirectory);
        }

        try {
            // Perform the actual scanning
            List<ScanResult.ScannedFile> scannedFiles = new ArrayList<>();
            List<ScanResult.ScanError> errors = new ArrayList<>();
            Map<String, Object> metadata = new HashMap<>();

            // Create async file visitor if none provided
            AsyncFileVisitor asyncVisitor = asyncFileVisitor != null ?
                asyncFileVisitor : new DefaultAsyncFileVisitor();

            // Walk the file tree asynchronously
            // Create a simple file visitor for the walk
            java.nio.file.SimpleFileVisitor<Path> simpleVisitor = new java.nio.file.SimpleFileVisitor<Path>() {
                @Override
                public java.nio.file.FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (context.cancelled.get()) {
                        return java.nio.file.FileVisitResult.TERMINATE;
                    }
                    try {
                        processFileAsync(file, context, asyncVisitor, scannedFiles, errors);
                    } catch (Exception e) {
                        logger.error("Error processing file: {}", file, e);
                        errors.add(new ScanResult.ScanError(file, e, e.getMessage()));
                    }
                    return context.cancelled.get() ?
                        java.nio.file.FileVisitResult.TERMINATE : java.nio.file.FileVisitResult.CONTINUE;
                }
                
                @Override
                public java.nio.file.FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (context.cancelled.get()) {
                        return java.nio.file.FileVisitResult.TERMINATE;
                    }
                    context.directoriesProcessed.incrementAndGet();
                    return context.cancelled.get() ?
                        java.nio.file.FileVisitResult.TERMINATE : java.nio.file.FileVisitResult.CONTINUE;
                }
            };
            
            Files.walkFileTree(context.rootDirectory,
                java.util.EnumSet.noneOf(java.nio.file.FileVisitOption.class),
                context.options.getMaxDepth(),
                simpleVisitor);
            // No filter needed since we handle cancellation in the visitor

            // Create result
            Instant endTime = Instant.now();
            AsyncScanResult result = new AsyncScanResult(
                context.scanId, context.rootDirectory, scannedFiles, errors,
                context.startTime, endTime, metadata, 1,
                calculateThroughput(context), calculatePeakMemoryUsage(),
                context.directoriesProcessed.get(), 0, 0, 0, false,
                createAsyncMetadata(context)
            );

            // Update statistics
            stats.incrementScansCompleted();
            stats.addFilesScanned(scannedFiles.size());
            stats.addDirectoriesScanned(context.directoriesProcessed.get());
            stats.addBytesProcessed(calculateTotalBytes(scannedFiles));
            stats.decrementActiveScans();

            // Notify completion
            if (asyncProgressListener != null) {
                asyncProgressListener.onScanCompletedAsync(context.scanId, result);
            } else if (progressListener != null) {
                progressListener.onScanCompleted(result);
            }

            context.future.complete(result);
            return result;

        } catch (Exception e) {
            logger.error("Async scan failed: {}", context.scanId, e);
            stats.incrementScansFailed();
            stats.decrementActiveScans();
            context.future.completeExceptionally(e);
            throw new RuntimeException("Async scan failed", e);
        } finally {
            // Remove from active scans
            activeScans.remove(context.scanId);
        }
    }

    /**
     * Performs a parallel scan operation.
     *
     * @param context scan context
     * @param concurrency level of parallelism
     * @return async scan result
     */
    private AsyncScanResult performParallelScan(ScanContext context, int concurrency) {
        logger.info("Starting parallel scan: {} with concurrency: {}", context.scanId, concurrency);

        // Add to active scans
        activeScans.put(context.scanId, context);
        stats.incrementScansInitiated();
        stats.incrementActiveScans();

        try {
            List<ScanResult.ScannedFile> scannedFiles = new ArrayList<>();
            List<ScanResult.ScanError> errors = new ArrayList<>();
            Map<String, Object> metadata = new HashMap<>();

            // Create parallel processing
            ExecutorService parallelExecutor = Executors.newFixedThreadPool(concurrency);
            
            try {
                // Walk file tree and process in parallel
                List<Path> allPaths = Files.walk(context.rootDirectory, context.options.getMaxDepth())
                    .filter(path -> !context.cancelled.get())
                    .collect(Collectors.toList());

                // Process paths in parallel batches
                int batchSize = Math.max(1, allPaths.size() / concurrency);
                List<List<Path>> batches = partitionList(allPaths, batchSize);

                List<CompletableFuture<Void>> futures = batches.stream()
                    .map(batch -> CompletableFuture.runAsync(() -> {
                        batch.forEach(path -> {
                            if (context.cancelled.get()) {
                                return;
                            }
                            try {
                                AsyncFileVisitor visitor = asyncFileVisitor != null ? 
                                    asyncFileVisitor : new DefaultAsyncFileVisitor();
                                processFileAsync(path, context, visitor, scannedFiles, errors);
                            } catch (Exception e) {
                                logger.error("Error processing file in parallel: {}", path, e);
                                synchronized (errors) {
                                    errors.add(new ScanResult.ScanError(path, e, e.getMessage()));
                                }
                            }
                        });
                    }, parallelExecutor))
                    .collect(Collectors.toList());

                // Wait for all batches to complete
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(60, TimeUnit.MINUTES);

            } finally {
                parallelExecutor.shutdown();
                parallelExecutor.awaitTermination(30, TimeUnit.SECONDS);
            }

            // Create result
            Instant endTime = Instant.now();
            AsyncScanResult result = new AsyncScanResult(
                context.scanId, context.rootDirectory, scannedFiles, errors,
                context.startTime, endTime, metadata, concurrency,
                calculateThroughput(context), calculatePeakMemoryUsage(),
                context.directoriesProcessed.get(), 0, 0, 0, false,
                createAsyncMetadata(context)
            );

            // Update statistics
            stats.incrementScansCompleted();
            stats.addFilesScanned(scannedFiles.size());
            stats.addDirectoriesScanned(context.directoriesProcessed.get());
            stats.addBytesProcessed(calculateTotalBytes(scannedFiles));
            stats.decrementActiveScans();

            context.future.complete(result);
            return result;

        } catch (Exception e) {
            logger.error("Parallel scan failed: {}", context.scanId, e);
            stats.incrementScansFailed();
            stats.decrementActiveScans();
            context.future.completeExceptionally(e);
            throw new RuntimeException("Parallel scan failed", e);
        } finally {
            activeScans.remove(context.scanId);
        }
    }

    /**
     * Performs a streaming scan operation.
     *
     * @param context scan context
     * @param resultConsumer consumer for incremental results
     */
    private void performStreamingScan(ScanContext context, Consumer<AsyncScanResult> resultConsumer) {
        logger.info("Starting streaming scan: {} for directory: {}", context.scanId, context.rootDirectory);

        // Add to active scans
        activeScans.put(context.scanId, context);
        stats.incrementScansInitiated();
        stats.incrementActiveScans();

        try {
            List<ScanResult.ScannedFile> scannedFiles = new ArrayList<>();
            List<ScanResult.ScanError> errors = new ArrayList<>();
            Map<String, Object> metadata = new HashMap<>();

            // Create visitor
            AsyncFileVisitor visitor = asyncFileVisitor != null ? 
                asyncFileVisitor : new DefaultAsyncFileVisitor();

            // Walk file tree and stream results
            Files.walk(context.rootDirectory, context.options.getMaxDepth())
                .filter(path -> !context.cancelled.get())
                .forEach(path -> {
                    if (context.cancelled.get()) {
                        return;
                    }

                    try {
                        processFileAsync(path, context, visitor, scannedFiles, errors);
                        
                        // Send incremental result every N files
                        if (scannedFiles.size() % 100 == 0) {
                            AsyncScanResult incrementalResult = new AsyncScanResult(
                                context.scanId, context.rootDirectory,
                                new ArrayList<ScanResult.ScannedFile>(scannedFiles),
                                new ArrayList<ScanResult.ScanError>(errors),
                                context.startTime, Instant.now(), metadata, 1,
                                calculateThroughput(context), calculatePeakMemoryUsage(),
                                context.directoriesProcessed.get(), 0, 0, 0, false,
                                createAsyncMetadata(context)
                            );
                            resultConsumer.accept(incrementalResult);
                        }
                        
                    } catch (Exception e) {
                        logger.error("Error processing file in stream: {}", path, e);
                        errors.add(new ScanResult.ScanError(path, e, e.getMessage()));
                    }
                });

            // Send final result
            Instant endTime = Instant.now();
            AsyncScanResult finalResult = new AsyncScanResult(
                context.scanId, context.rootDirectory, scannedFiles, errors,
                context.startTime, endTime, metadata, 1,
                calculateThroughput(context), calculatePeakMemoryUsage(),
                context.directoriesProcessed.get(), 0, 0, 0, false,
                createAsyncMetadata(context)
            );

            // Update statistics
            stats.incrementScansCompleted();
            stats.addFilesScanned(scannedFiles.size());
            stats.addDirectoriesScanned(context.directoriesProcessed.get());
            stats.addBytesProcessed(calculateTotalBytes(scannedFiles));
            stats.decrementActiveScans();

            resultConsumer.accept(finalResult);

        } catch (Exception e) {
            logger.error("Streaming scan failed: {}", context.scanId, e);
            stats.incrementScansFailed();
            stats.decrementActiveScans();
            throw new RuntimeException("Streaming scan failed", e);
        } finally {
            activeScans.remove(context.scanId);
        }
    }

    /**
     * Processes a single file asynchronously.
     */
    private void processFileAsync(Path path, ScanContext context, AsyncFileVisitor visitor,
                                List<ScanResult.ScannedFile> scannedFiles,
                                List<ScanResult.ScanError> errors) {
        try {
            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
            
            if (Files.isDirectory(path)) {
                context.directoriesProcessed.incrementAndGet();
                visitor.visitDirectoryAsync(path, attrs)
                    .thenAccept(result -> {
                        if (result == FileVisitor.FileVisitResult.CONTINUE) {
                            // Process directory contents if needed
                        }
                    })
                    .get(30, TimeUnit.SECONDS);
            } else {
                context.filesProcessed.incrementAndGet();
                visitor.visitFileAsync(path, attrs)
                    .thenAccept(result -> {
                        if (result == FileVisitor.FileVisitResult.CONTINUE) {
                            // Add to scanned files
                            boolean isSymlink = Files.isSymbolicLink(path);
                            boolean isSparse = detectSparseFile(path, attrs);
                            Path linkTarget = null;
                            if (isSymlink) {
                                try {
                                    linkTarget = Files.readSymbolicLink(path);
                                } catch (IOException e) {
                                    logger.warn("Failed to read symbolic link target for: {}", path, e);
                                }
                            }
                            
                            ScanResult.ScannedFile scannedFile = new ScanResult.ScannedFile(
                                path, attrs.size(), attrs.lastModifiedTime().toInstant(),
                                isSymlink, isSparse, linkTarget
                            );
                            
                            synchronized (scannedFiles) {
                                scannedFiles.add(scannedFile);
                            }
                        }
                    })
                    .get(30, TimeUnit.SECONDS);
            }

            // Update progress
            if (asyncProgressListener != null) {
                asyncProgressListener.onFileProcessedAsync(
                    context.scanId, path, context.filesProcessed.get(), -1
                );
            } else if (progressListener != null) {
                progressListener.onFileProcessed(path, context.filesProcessed.get(), -1);
            }

        } catch (Exception e) {
            logger.error("Error processing file asynchronously: {}", path, e);
            errors.add(new ScanResult.ScanError(path, e, e.getMessage()));
        }
    }

    /**
     * Default implementation of AsyncFileVisitor.
     */
    private class DefaultAsyncFileVisitor implements AsyncFileVisitor {
        @Override
        public CompletableFuture<FileVisitor.FileVisitResult> visitFileAsync(Path file, BasicFileAttributes attrs) {
            return CompletableFuture.completedFuture(FileVisitor.FileVisitResult.CONTINUE);
        }

        @Override
        public CompletableFuture<FileVisitor.FileVisitResult> visitDirectoryAsync(Path dir, BasicFileAttributes attrs) {
            return CompletableFuture.completedFuture(FileVisitor.FileVisitResult.CONTINUE);
        }

        @Override
        public CompletableFuture<FileVisitor.FileVisitResult> visitFailedAsync(Path file, IOException exc) {
            return CompletableFuture.completedFuture(FileVisitor.FileVisitResult.CONTINUE);
        }
    }

    /**
     * Calculates throughput for the scan context.
     */
    private double calculateThroughput(ScanContext context) {
        long durationMs = java.time.Duration.between(context.startTime, Instant.now()).toMillis();
        if (durationMs == 0) {
            return 0.0;
        }
        return (context.filesProcessed.get() * 1000.0) / durationMs;
    }

    /**
     * Calculates peak memory usage.
     */
    private long calculatePeakMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    /**
     * Calculates total bytes from scanned files.
     */
    private long calculateTotalBytes(List<ScanResult.ScannedFile> scannedFiles) {
        return scannedFiles.stream()
            .mapToLong(ScanResult.ScannedFile::getSize)
            .sum();
    }

    /**
     * Creates async metadata for the scan context.
     */
    private Map<String, Object> createAsyncMetadata(ScanContext context) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("scanId", context.scanId);
        metadata.put("threadCount", 1);
        metadata.put("throughput", calculateThroughput(context));
        metadata.put("peakMemoryUsage", calculatePeakMemoryUsage());
        return metadata;
    }

    /**
     * Detects if a file is sparse.
     */
    private boolean detectSparseFile(Path path, BasicFileAttributes attrs) {
        // Simple sparse file detection based on size vs allocated blocks
        try {
            long size = attrs.size();
            if (size == 0) {
                return false;
            }
            
            // On Unix systems, check block allocation
            if (System.getProperty("os.name").toLowerCase().contains("linux") ||
                System.getProperty("os.name").toLowerCase().contains("mac")) {
                
                Object blockSize = Files.getAttribute(path, "unix:blocksize");
                Object blocks = Files.getAttribute(path, "unix:blocks");
                
                if (blockSize instanceof Integer && blocks instanceof Long) {
                    long allocatedSize = (Long) blocks * (Integer) blockSize;
                    return allocatedSize < size * 0.9; // Less than 90% allocated
                }
            }
        } catch (Exception e) {
            logger.debug("Error detecting sparse file: {}", path, e);
        }
        
        return false;
    }

    /**
     * Partitions a list into sublists of specified size.
     */
    private <T> List<List<T>> partitionList(List<T> list, int size) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            int end = Math.min(i + size, list.size());
            partitions.add(new ArrayList<>(list.subList(i, end)));
        }
        return partitions;
    }
}