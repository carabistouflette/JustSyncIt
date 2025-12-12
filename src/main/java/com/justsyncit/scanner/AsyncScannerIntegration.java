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

import com.justsyncit.ServiceFactory;
import com.justsyncit.hash.Blake3Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Integration coordinator for async directory scanning with file chunking and
 * buffer management.
 * Provides seamless coordination between AsyncFilesystemScanner,
 * AsyncFileChunker, and AsyncByteBufferPool.
 */
public class AsyncScannerIntegration {

    private static final Logger logger = LoggerFactory.getLogger(AsyncScannerIntegration.class);

    /** Async filesystem scanner for directory operations. */
    private final AsyncFilesystemScanner asyncScanner;

    /** Async file chunker for processing large files. */
    private final AsyncFileChunker asyncFileChunker;

    /** Async buffer pool for memory management. */
    private final AsyncByteBufferPool asyncBufferPool;

    /** Thread pool manager for resource coordination. */
    private final ThreadPoolManager threadPoolManager;

    /** Event processor for file change handling. */
    private final AsyncFileEventProcessor eventProcessor;

    /** Integration state. */
    private volatile boolean initialized = false;

    /**
     * Configuration for scanner integration.
     */
    public static class IntegrationConfig {
        private int maxConcurrentScans = Runtime.getRuntime().availableProcessors();
        private int chunkSize = 64 * 1024; // 64KB chunks
        private int bufferSize = 1024 * 1024; // 1MB buffers
        private long debounceDelayMs = 100;
        private int eventBatchSize = 100;
        private boolean enableEventProcessing = true;

        public IntegrationConfig withMaxConcurrentScans(int maxScans) {
            this.maxConcurrentScans = Math.max(1, maxScans);
            return this;
        }

        public IntegrationConfig withChunkSize(int size) {
            this.chunkSize = Math.max(1024, size);
            return this;
        }

        public IntegrationConfig withBufferSize(int size) {
            this.bufferSize = Math.max(4096, size);
            return this;
        }

        public IntegrationConfig withDebounceDelay(long delayMs) {
            this.debounceDelayMs = Math.max(0, delayMs);
            return this;
        }

        public IntegrationConfig withEventBatchSize(int size) {
            this.eventBatchSize = Math.max(1, size);
            return this;
        }

        public IntegrationConfig withEventProcessing(boolean enabled) {
            this.enableEventProcessing = enabled;
            return this;
        }

        // Getters
        public int getMaxConcurrentScans() {
            return maxConcurrentScans;
        }

        public int getChunkSize() {
            return chunkSize;
        }

        public int getBufferSize() {
            return bufferSize;
        }

        public long getDebounceDelayMs() {
            return debounceDelayMs;
        }

        public int getEventBatchSize() {
            return eventBatchSize;
        }

        public boolean isEventProcessingEnabled() {
            return enableEventProcessing;
        }
    }

    /**
     * Creates a new AsyncScannerIntegration with default configuration.
     *
     * @param threadPoolManager thread pool manager for resource coordination
     */
    public AsyncScannerIntegration(ThreadPoolManager threadPoolManager) {
        this(new IntegrationConfig(), threadPoolManager);
    }

    /**
     * Creates a new AsyncScannerIntegration with custom configuration.
     *
     * @param config            integration configuration
     * @param threadPoolManager thread pool manager for resource coordination
     */
    public AsyncScannerIntegration(IntegrationConfig config, ThreadPoolManager threadPoolManager) {
        this.threadPoolManager = Objects.requireNonNull(threadPoolManager);

        // Initialize async buffer pool - use existing implementation
        this.asyncBufferPool = AsyncByteBufferPoolImpl.create(config.getBufferSize(),
                Runtime.getRuntime().availableProcessors() * 2);

        // Initialize async file chunker - use existing implementation
        try {
            ServiceFactory serviceFactory = new ServiceFactory();
            Blake3Service blake3Service = serviceFactory.createBlake3Service();
            this.asyncFileChunker = AsyncFileChunkerImpl.create(blake3Service, asyncBufferPool, config.getChunkSize(),
                    null);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize async file chunker", e);
        }
        this.asyncFileChunker.setAsyncBufferPool(asyncBufferPool);

        // Initialize async scanner
        this.asyncScanner = new AsyncFilesystemScannerImpl(threadPoolManager, asyncBufferPool);
        this.asyncScanner.setMaxConcurrentScans(config.getMaxConcurrentScans());

        // Initialize event processor if enabled
        if (config.isEventProcessingEnabled()) {
            AsyncFileEventProcessor.EventProcessorConfig eventConfig = new AsyncFileEventProcessor.EventProcessorConfig()
                    .withThreadPoolSize(threadPoolManager.getConfiguration()
                            .getPoolConfig(ThreadPoolManager.PoolType.IO).getMaximumPoolSize())
                    .withBatchSize(config.getEventBatchSize())
                    .withDebounceDelay(config.getDebounceDelayMs());

            this.eventProcessor = new AsyncFileEventProcessor(eventConfig, this::handleFileChangeEvent);
        } else {
            this.eventProcessor = null;
        }

        logger.info(
                "AsyncScannerIntegration initialized with config: maxScans={}, chunkSize={}, bufferSize={}, eventProcessing={}",
                config.getMaxConcurrentScans(), config.getChunkSize(), config.getBufferSize(),
                config.isEventProcessingEnabled());
    }

    /**
     * Initializes the integration components.
     *
     * @return a CompletableFuture that completes when initialization is done
     */
    public CompletableFuture<Void> initializeAsync() {
        return CompletableFuture.runAsync(() -> {
            if (initialized) {
                logger.warn("AsyncScannerIntegration is already initialized");
                return;
            }

            try {
                logger.info("Initializing AsyncScannerIntegration components");

                // Start event processor if configured
                if (eventProcessor != null) {
                    eventProcessor.startAsync().get(10, java.util.concurrent.TimeUnit.SECONDS);
                    logger.info("Event processor started");
                }

                // Buffer pool and chunker are already initialized
                logger.info("Buffer pool and file chunker initialized");

                initialized = true;
                logger.info("AsyncScannerIntegration initialized successfully");

            } catch (Exception e) {
                logger.error("Failed to initialize AsyncScannerIntegration", e);
                throw new RuntimeException("Integration initialization failed", e);
            }
        }, threadPoolManager.getManagementThreadPool());
    }

    /**
     * Shuts down the integration components.
     *
     * @return a CompletableFuture that completes when shutdown is done
     */
    public CompletableFuture<Void> shutdownAsync() {
        return CompletableFuture.runAsync(() -> {
            if (!initialized) {
                logger.warn("AsyncScannerIntegration is not initialized");
                return;
            }

            try {
                logger.info("Shutting down AsyncScannerIntegration components");

                // Stop event processor
                if (eventProcessor != null) {
                    eventProcessor.stopAsync().get(10, java.util.concurrent.TimeUnit.SECONDS);
                    logger.info("Event processor stopped");
                }

                // Close async scanner
                asyncScanner.closeAsync().get(30, java.util.concurrent.TimeUnit.SECONDS);
                logger.info("Async scanner closed");

                // Shutdown file chunker
                asyncFileChunker.closeAsync().get(10, TimeUnit.SECONDS);
                logger.info("File chunker shutdown");

                // Close buffer pool
                asyncBufferPool.clearAsync().get(10, TimeUnit.SECONDS);
                logger.info("Buffer pool closed");

                initialized = false;
                logger.info("AsyncScannerIntegration shutdown successfully");

            } catch (Exception e) {
                logger.error("Failed to shutdown AsyncScannerIntegration", e);
                throw new RuntimeException("Integration shutdown failed", e);
            }
        }, threadPoolManager.getManagementThreadPool());
    }

    /**
     * Scans a directory and processes files with chunking.
     *
     * @param directory directory to scan
     * @param options   scanning options
     * @return a CompletableFuture that completes with scan results
     */
    public CompletableFuture<AsyncScanResult> scanAndChunkAsync(Path directory, ScanOptions options) {
        if (!initialized) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("AsyncScannerIntegration is not initialized"));
        }

        logger.info("Starting scan with chunking for directory: {}", directory);

        return asyncScanner.scanDirectoryAsync(directory, options)
                .thenCompose(scanResult -> {
                    logger.info("Scan completed, processing {} files with chunking",
                            scanResult.getScannedFileCount());

                    // Process each scanned file with chunking
                    java.util.List<CompletableFuture<Void>> chunkingFutures = new java.util.ArrayList<>();

                    for (ScanResult.ScannedFile scannedFile : scanResult.getScannedFiles()) {
                        if (scannedFile.getSize() > asyncFileChunker.getChunkSize()) {
                            CompletableFuture<Void> chunkingFuture = asyncFileChunker.chunkFileAsync(
                                    scannedFile.getPath(),
                                    new FileChunker.ChunkingOptions()
                                            .withChunkSize(asyncFileChunker.getChunkSize()))
                                    .thenAccept(chunkingResult -> {
                                        logger.debug("File chunked: {} -> {} chunks",
                                                scannedFile.getPath(), chunkingResult.getChunkCount());
                                    }).exceptionally(throwable -> {
                                        logger.error("Failed to chunk file: {}", scannedFile.getPath(), throwable);
                                        return null;
                                    });

                            chunkingFutures.add(chunkingFuture);
                        }
                    }

                    // Wait for all chunking operations to complete
                    return CompletableFuture.allOf(chunkingFutures.toArray(new CompletableFuture<?>[0]))
                            .thenApply(v -> {
                                logger.info("All chunking operations completed for scan of: {}", directory);
                                return scanResult;
                            });
                })
                .exceptionally(throwable -> {
                    logger.error("Scan with chunking failed for directory: {}", directory, throwable);
                    throw new RuntimeException("Scan with chunking failed", throwable);
                });
    }

    /**
     * Starts directory monitoring with integrated event processing.
     *
     * @param directory directory to monitor
     * @param options   scanning options
     * @return a CompletableFuture that completes with registration
     */
    public CompletableFuture<WatchServiceRegistration> startMonitoringAsync(Path directory, ScanOptions options) {
        if (!initialized) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("AsyncScannerIntegration is not initialized"));
        }

        if (eventProcessor == null) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Event processing is not enabled"));
        }

        logger.info("Starting integrated monitoring for directory: {}", directory);

        // Convert ScanOptions to AsyncScanOptions
        // Create basic async scan options - convert from regular scan options
        AsyncScanOptions asyncOptions = new AsyncScanOptions();
        // Note: AsyncScanOptions may need additional methods for full conversion

        return asyncScanner.startDirectoryMonitoring(directory, asyncOptions, eventProcessor::processEventAsync)
                .thenApply(registration -> {
                    logger.info("Integrated monitoring started for directory: {}", directory);
                    return registration;
                })
                .exceptionally(throwable -> {
                    logger.error("Failed to start integrated monitoring for directory: {}", directory, throwable);
                    throw new RuntimeException("Monitoring start failed", throwable);
                });
    }

    /**
     * Gets the async filesystem scanner.
     *
     * @return the async scanner
     */
    public AsyncFilesystemScanner getAsyncScanner() {
        return asyncScanner;
    }

    /**
     * Gets the async file chunker.
     *
     * @return the async file chunker
     */
    public AsyncFileChunker getAsyncFileChunker() {
        return asyncFileChunker;
    }

    /**
     * Gets the async buffer pool.
     *
     * @return the async buffer pool
     */
    public AsyncByteBufferPool getAsyncBufferPool() {
        return asyncBufferPool;
    }

    /**
     * Gets the event processor.
     *
     * @return the event processor, or null if not enabled
     */
    public AsyncFileEventProcessor getEventProcessor() {
        return eventProcessor;
    }

    /**
     * Gets integration statistics.
     *
     * @return a CompletableFuture that completes with statistics
     */
    public CompletableFuture<IntegrationStats> getStatsAsync() {
        return CompletableFuture.supplyAsync(() -> {
            IntegrationStats stats = new IntegrationStats();

            // Get scanner stats
            try {
                stats.scannerStats = asyncScanner.getStatsAsync().get(5, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception e) {
                logger.warn("Failed to get scanner stats", e);
            }

            // Get buffer pool stats
            try {
                stats.bufferPoolStats = asyncBufferPool.getStatsAsync().get(5, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception e) {
                logger.warn("Failed to get buffer pool stats", e);
            }

            // Get chunker stats
            try {
                stats.chunkerStats = asyncFileChunker.getStatsAsync().get(5, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception e) {
                logger.warn("Failed to get chunker stats", e);
            }

            // Get event processor stats
            if (eventProcessor != null) {
                stats.eventProcessorStats = eventProcessor.getStats();
            }

            return stats;
        }, threadPoolManager.getManagementThreadPool());
    }

    /**
     * Handles file change events from the event processor.
     *
     * @param event file change event
     */
    private void handleFileChangeEvent(FileChangeEvent event) {
        try {
            logger.debug("Handling integrated file change event: {}", event);

            // For file creation/modification events, trigger chunking if needed
            if ((event.getEventType() == FileChangeEvent.EventType.ENTRY_CREATE ||
                    event.getEventType() == FileChangeEvent.EventType.ENTRY_MODIFY) &&
                    !event.isDirectory() && event.getFileSize() > asyncFileChunker.getChunkSize()) {

                logger.debug("Triggering chunking for changed file: {}", event.getFilePath());

                asyncFileChunker.chunkFileAsync(event.getFilePath(),
                        new FileChunker.ChunkingOptions()
                                .withChunkSize(asyncFileChunker.getChunkSize()))
                        .thenAccept(chunkingResult -> {
                            logger.debug("Changed file chunked: {} -> {} chunks",
                                    event.getFilePath(), chunkingResult.getChunkCount());
                        })
                        .exceptionally(throwable -> {
                            logger.error("Failed to chunk changed file: {}", event.getFilePath(), throwable);
                            return null;
                        });
            }

        } catch (Exception e) {
            logger.error("Error handling file change event: {}", event, e);
        }
    }

    /**
     * Statistics for the integration.
     */
    public static class IntegrationStats {
        public AsyncScannerStats scannerStats;
        public String bufferPoolStats;
        public String chunkerStats;
        public AsyncFileEventProcessor.EventProcessorStats eventProcessorStats;

        @Override
        public String toString() {
            return String.format(
                    "IntegrationStats{scanner=%s, bufferPool=%s, chunker=%s, eventProcessor=%s}",
                    scannerStats, bufferPoolStats, chunkerStats, eventProcessorStats);
        }
    }

    @Override
    public String toString() {
        return String.format(
                "AsyncScannerIntegration{initialized=%b, scanner=%s, chunker=%s, bufferPool=%s, eventProcessor=%s}",
                initialized, asyncScanner, asyncFileChunker, asyncBufferPool, eventProcessor);
    }
}