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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.justsyncit.scanner;

import com.justsyncit.hash.Blake3Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Implementation of AsyncFileChunker with CompletionHandler pattern and async
 * I/O.
 * Uses AsynchronousFileChannel for optimal SSD/HDD performance.
 * Follows Single Responsibility Principle by focusing only on async chunking
 * operations.
 */
public class AsyncFileChunkerImpl implements AsyncFileChunker {

    /** Logger for the async chunker. */
    private static final Logger logger = LoggerFactory.getLogger(AsyncFileChunkerImpl.class);

    /** Default chunk size (64KB). */
    private static final int DEFAULT_CHUNK_SIZE = 64 * 1024;
    /** Default maximum concurrent operations. */
    private static final int DEFAULT_MAX_CONCURRENT_OPERATIONS = 4;

    /** BLAKE3 service for hash calculation. */
    private final Blake3Service blake3Service;
    /** Async buffer pool for memory management. */
    private AsyncByteBufferPool asyncBufferPool;
    /** Async chunk handler for processing chunks. */
    private AsyncChunkHandler asyncChunkHandler;
    /** Current chunk size. */
    private volatile int chunkSize;
    /** Maximum number of concurrent operations. */
    private volatile int maxConcurrentOperations;
    /** Number of currently active operations. */
    private final AtomicInteger activeOperations;
    /** Whether the chunker has been closed. */
    private volatile boolean closed;
    /** Executor service for async operations. */
    private final ExecutorService executorService;
    /** Semaphore for controlling concurrent operations. */
    private final Semaphore operationSemaphore;

    /**
     * Creates a new AsyncFileChunkerImpl with default settings.
     *
     * @param blake3Service BLAKE3 service for hash calculation
     * @return a new AsyncFileChunkerImpl with default settings
     * @throws IllegalArgumentException if blake3Service is null
     */
    public static AsyncFileChunkerImpl create(Blake3Service blake3Service) {
        return create(blake3Service, AsyncByteBufferPoolImpl.create(), DEFAULT_CHUNK_SIZE, null);
    }

    /**
     * Creates a new AsyncFileChunkerImpl with custom settings.
     *
     * @param blake3Service     BLAKE3 service for hash calculation
     * @param asyncBufferPool   async buffer pool for memory management
     * @param chunkSize         chunk size in bytes
     * @param asyncChunkHandler async chunk handler for processing chunks
     * @return a new AsyncFileChunkerImpl with custom settings
     * @throws IllegalArgumentException if parameters are invalid
     */
    public static AsyncFileChunkerImpl create(Blake3Service blake3Service, AsyncByteBufferPool asyncBufferPool,
            int chunkSize, AsyncChunkHandler asyncChunkHandler) {
        if (blake3Service == null) {
            throw new IllegalArgumentException("BLAKE3 service cannot be null");
        }
        if (asyncBufferPool == null) {
            throw new IllegalArgumentException("Async buffer pool cannot be null");
        }
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("Chunk size must be positive");
        }

        return new AsyncFileChunkerImpl(blake3Service, asyncBufferPool, chunkSize, asyncChunkHandler);
    }

    /**
     * Creates a new AsyncFileChunkerImpl with specified settings.
     */
    private AsyncFileChunkerImpl(Blake3Service blake3Service, AsyncByteBufferPool asyncBufferPool,
            int chunkSize, AsyncChunkHandler asyncChunkHandler) {
        this.blake3Service = blake3Service;
        this.asyncBufferPool = asyncBufferPool;
        this.chunkSize = chunkSize;
        this.asyncChunkHandler = asyncChunkHandler != null ? asyncChunkHandler
                : new DefaultAsyncChunkHandler(blake3Service);
        this.maxConcurrentOperations = DEFAULT_MAX_CONCURRENT_OPERATIONS;
        this.activeOperations = new AtomicInteger(0);
        this.closed = false;
        this.executorService = Executors.newFixedThreadPool(DEFAULT_MAX_CONCURRENT_OPERATIONS);
        this.operationSemaphore = new Semaphore(maxConcurrentOperations);
    }

    @Override
    public void chunkFileAsync(Path file, ChunkingOptions options,
            CompletionHandler<ChunkingResult, Exception> handler) {
        if (file == null) {
            throw new IllegalArgumentException("File cannot be null");
        }
        if (handler == null) {
            throw new IllegalArgumentException("Handler cannot be null");
        }
        if (closed) {
            handler.failed(new IllegalStateException("Chunker has been closed"));
            return;
        }

        CompletableFuture<ChunkingResult> future = chunkFileAsync(file, options);
        future.whenComplete((result, throwable) -> {
            if (throwable != null) {
                handler.failed(
                        throwable instanceof Exception ? (Exception) throwable : new RuntimeException(throwable));
            } else if (result != null && !result.isSuccess()) {
                // Convert failed ChunkingResult to exception for CompletionHandler
                Exception exception = result.getError();
                if (exception != null) {
                    handler.failed(exception);
                } else {
                    handler.failed(new RuntimeException("Chunking failed: " + result));
                }
            } else {
                handler.completed(result);
            }
        });
    }

    @Override
    public CompletableFuture<ChunkingResult> chunkFileAsync(Path file, ChunkingOptions options) {
        if (file == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("File cannot be null"));
        }
        if (closed) {
            return CompletableFuture.failedFuture(new IllegalStateException("Chunker has been closed"));
        }

        return performAsyncChunking(file, options);
    }

    @Override
    public CompletableFuture<ChunkingResult> chunkFile(Path file, ChunkingOptions options) {
        // Delegate to async implementation for backward compatibility
        return chunkFileAsync(file, options);
    }

    @Override
    public void setAsyncBufferPool(AsyncByteBufferPool asyncBufferPool) {
        if (asyncBufferPool == null) {
            throw new IllegalArgumentException("Async buffer pool cannot be null");
        }
        this.asyncBufferPool = asyncBufferPool;
        logger.debug("Updated async buffer pool to {}", asyncBufferPool.getClass().getSimpleName());
    }

    @Override
    public AsyncByteBufferPool getAsyncBufferPool() {
        return asyncBufferPool;
    }

    @Override
    public void setAsyncChunkHandler(AsyncChunkHandler asyncChunkHandler) {
        if (asyncChunkHandler == null) {
            throw new IllegalArgumentException("Async chunk handler cannot be null");
        }
        this.asyncChunkHandler = asyncChunkHandler;
        logger.debug("Updated async chunk handler to {}", asyncChunkHandler.getClass().getSimpleName());
    }

    @Override
    public AsyncChunkHandler getAsyncChunkHandler() {
        return asyncChunkHandler;
    }

    @Override
    public CompletableFuture<String> getStatsAsync() {
        return CompletableFuture.supplyAsync(() -> {
            return String.format(
                    "AsyncFileChunkerImpl Stats - Active Operations: %d, Max Concurrent: %d, Chunk Size: %d, Closed: %b",
                    activeOperations.get(), maxConcurrentOperations, chunkSize, closed);
        }, executorService);
    }

    @Override
    public void setBufferPool(BufferPool bufferPool) {
        // For backward compatibility, wrap sync buffer pool in async wrapper
        if (bufferPool instanceof AsyncByteBufferPool) {
            setAsyncBufferPool((AsyncByteBufferPool) bufferPool);
        } else {
            // Create async wrapper for sync buffer pool
            setAsyncBufferPool(new SyncToAsyncBufferPoolWrapper(bufferPool));
        }
    }

    @Override
    public void setChunkSize(int chunkSize) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("Chunk size must be positive");
        }
        this.chunkSize = chunkSize;
        logger.debug("Updated chunk size to {}", chunkSize);
    }

    @Override
    public int getChunkSize() {
        return chunkSize;
    }

    @Override
    public String storeChunk(byte[] data) throws IOException {
        // This method is not implemented in AsyncFileChunker
        // Chunk storage should be handled by ContentStore
        throw new UnsupportedOperationException("storeChunk not implemented in AsyncFileChunker");
    }

    @Override
    public byte[] retrieveChunk(String hash) throws IOException {
        // This would integrate with ContentStore in a real implementation
        throw new UnsupportedOperationException("retrieveChunk not implemented in AsyncFileChunker");
    }

    @Override
    public boolean existsChunk(String hash) throws IOException {
        // This would integrate with ContentStore in a real implementation
        throw new UnsupportedOperationException("existsChunk not implemented in AsyncFileChunker");
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public int getActiveOperations() {
        return activeOperations.get();
    }

    @Override
    public int getMaxConcurrentOperations() {
        return maxConcurrentOperations;
    }

    @Override
    public void setMaxConcurrentOperations(int maxConcurrentOperations) {
        if (maxConcurrentOperations <= 0) {
            throw new IllegalArgumentException("Max concurrent operations must be positive");
        }
        this.maxConcurrentOperations = maxConcurrentOperations;
        logger.debug("Updated max concurrent operations to {}", maxConcurrentOperations);
    }

    @Override
    public CompletableFuture<Void> closeAsync() {
        if (closed) {
            return CompletableFuture.completedFuture(null);
        }

        closed = true;
        return CompletableFuture.runAsync(() -> {
            try {
                asyncBufferPool.clearAsync().get();
            } catch (Exception e) {
                logger.warn("Error closing async buffer pool: {}", e.getMessage());
            }
            logger.info("Closed AsyncFileChunkerImpl");
        }, executorService).thenRun(() -> executorService.shutdown());
    }

    /**
     * Performs the actual async file chunking operation.
     */
    private CompletableFuture<ChunkingResult> performAsyncChunking(Path file, ChunkingOptions options) {
        if (!Files.exists(file)) {
            return CompletableFuture.completedFuture(
                    ChunkingResult.createFailed(file, new IllegalArgumentException("File does not exist: " + file)));
        }
        if (!Files.isRegularFile(file)) {
            return CompletableFuture.completedFuture(
                    ChunkingResult.createFailed(file,
                            new IllegalArgumentException("Path is not a regular file: " + file)));
        }

        final ChunkingOptions finalOptions = options != null ? options : new ChunkingOptions();
        final int effectiveChunkSize = finalOptions.getChunkSize() > 0
                ? finalOptions.getChunkSize()
                : this.chunkSize;

        // Update max concurrent operations if specified in options
        if (finalOptions.getMaxConcurrentChunks() != this.maxConcurrentOperations) {
            this.maxConcurrentOperations = finalOptions.getMaxConcurrentChunks();
            // Update semaphore permits
            operationSemaphore.drainPermits();
            operationSemaphore.release(this.maxConcurrentOperations);
        }

        CompletableFuture<ChunkingResult> resultFuture = new CompletableFuture<>();
        AsynchronousFileChannel channel = null;

        try {
            long fileSize = Files.size(file);

            // Handle empty file case
            if (fileSize == 0) {
                try {
                    String fileHash = blake3Service.hashBuffer(new byte[0]);
                    return CompletableFuture.completedFuture(
                            new ChunkingResult(file, 0, 0, 0, fileHash, new ArrayList<>()));
                } catch (com.justsyncit.hash.HashingException e) {
                    return CompletableFuture.completedFuture(ChunkingResult.createFailed(file, e));
                }
            }

            // Calculate number of chunks
            int chunkCount = (int) Math.ceil((double) fileSize / effectiveChunkSize);
            List<String> chunkHashes = new ArrayList<>(chunkCount);

            logger.debug("Chunking file {} ({} bytes) into {} chunks of {} bytes each",
                    file, fileSize, chunkCount, effectiveChunkSize);

            channel = AsynchronousFileChannel.open(file, StandardOpenOption.READ);
            final AsynchronousFileChannel finalChannel = channel;

            // Calculate file hash asynchronously
            calculateFileHashAsync(finalChannel, fileSize)
                    .thenCompose(fileHash -> {
                        // Process chunks concurrently using true async I/O
                        return processAllChunksAsync(finalChannel, file, effectiveChunkSize, fileSize, chunkCount,
                                chunkHashes)
                                .thenApply(
                                        v -> new ChunkingResult(file, chunkCount, fileSize, 0, fileHash, chunkHashes));
                    })
                    .whenComplete((result, throwable) -> {
                        // Close channel after all operations complete
                        closeChannelAsync(finalChannel);
                        if (throwable != null) {
                            resultFuture.completeExceptionally(throwable);
                        } else {
                            resultFuture.complete(result);
                        }
                    });

        } catch (IOException e) {
            closeChannelAsync(channel);
            resultFuture.completeExceptionally(e);
        } catch (Exception e) {
            closeChannelAsync(channel);
            resultFuture.completeExceptionally(e);
        }

        return resultFuture;
    }

    /**
     * Calculates the hash of the entire file asynchronously.
     */
    private CompletableFuture<String> calculateFileHashAsync(AsynchronousFileChannel channel, long fileSize) {
        // For small files, read all at once
        if (fileSize <= asyncBufferPool.getDefaultBufferSize()) {
            return asyncBufferPool.acquireAsync((int) fileSize)
                    .thenCompose(buffer -> {
                        CompletableFuture<String> hashFuture = new CompletableFuture<>();

                        channel.read(buffer, 0, null, new java.nio.channels.CompletionHandler<Integer, Void>() {
                            @Override
                            public void completed(Integer bytesRead, Void attachment) {
                                buffer.flip();

                                byte[] fileData = new byte[buffer.remaining()];
                                buffer.get(fileData);

                                asyncBufferPool.releaseAsync(buffer);

                                try {
                                    String hash = blake3Service.hashBuffer(fileData);
                                    hashFuture.complete(hash);
                                } catch (Exception e) {
                                    hashFuture.completeExceptionally(e);
                                }
                            }

                            @Override
                            public void failed(Throwable exc, Void attachment) {
                                asyncBufferPool.releaseAsync(buffer);
                                hashFuture.completeExceptionally(exc);
                            }
                        });

                        return hashFuture;
                    });
        } else {
            // For large files, use incremental hashing
            return calculateFileHashIncrementallyAsync(channel, fileSize);
        }
    }

    /**
     * Calculates file hash incrementally for large files.
     */
    private CompletableFuture<String> calculateFileHashIncrementallyAsync(AsynchronousFileChannel channel,
            long fileSize) {
        try {
            com.justsyncit.hash.IncrementalHasherFactory hasherFactory = new com.justsyncit.hash.Blake3IncrementalHasherFactory(
                    com.justsyncit.hash.Sha256HashAlgorithm.create());
            com.justsyncit.hash.IncrementalHasherFactory.IncrementalHasher incrementalHasher = hasherFactory
                    .createIncrementalHasher();

            return calculateFileHashIncrementallyRecursive(channel, fileSize, incrementalHasher, 0);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(new IOException("Failed to calculate incremental file hash", e));
        }
    }

    /**
     * Recursively processes file chunks for incremental hashing.
     */
    private CompletableFuture<String> calculateFileHashIncrementallyRecursive(
            AsynchronousFileChannel channel, long fileSize,
            com.justsyncit.hash.IncrementalHasherFactory.IncrementalHasher incrementalHasher,
            long position) {

        if (position >= fileSize) {
            try {
                return CompletableFuture.completedFuture(incrementalHasher.digest());
            } catch (Exception e) {
                return CompletableFuture.failedFuture(e);
            }
        }

        int bufferSize = asyncBufferPool.getDefaultBufferSize();
        int bytesToRead = (int) Math.min(bufferSize, fileSize - position);

        return asyncBufferPool.acquireAsync(bufferSize)
                .thenCompose(buffer -> {
                    CompletableFuture<Integer> readFuture = new CompletableFuture<>();

                    channel.read(buffer, position, null, new java.nio.channels.CompletionHandler<Integer, Void>() {
                        @Override
                        public void completed(Integer bytesRead, Void attachment) {
                            buffer.flip();

                            byte[] chunkData = new byte[buffer.remaining()];
                            buffer.get(chunkData);

                            incrementalHasher.update(chunkData);
                            asyncBufferPool.releaseAsync(buffer);

                            readFuture.complete(bytesRead);
                        }

                        @Override
                        public void failed(Throwable exc, Void attachment) {
                            asyncBufferPool.releaseAsync(buffer);
                            readFuture.completeExceptionally(exc);
                        }
                    });

                    return readFuture;
                }).thenCompose(bytesRead -> {
                    long newPosition = position + bytesRead;
                    return calculateFileHashIncrementallyRecursive(channel, fileSize, incrementalHasher, newPosition);
                });
    }

    /**
     * Processes all chunks asynchronously.
     */
    private CompletableFuture<Void> processAllChunksAsync(AsynchronousFileChannel channel, Path file, int chunkSize,
            long fileSize, int chunkCount, List<String> chunkHashes) {
        List<CompletableFuture<Void>> chunkFutures = new ArrayList<>(chunkCount);

        // Submit all chunk processing tasks
        for (int i = 0; i < chunkCount; i++) {
            final int chunkIndex = i;
            final long offset = (long) i * chunkSize;
            final int length = (int) Math.min(chunkSize, fileSize - offset);

            CompletableFuture<Void> future = processChunkAsync(channel, offset, length, chunkIndex, chunkCount, file)
                    .thenAccept(hash -> {
                        synchronized (chunkHashes) {
                            chunkHashes.add(hash);
                        }
                    })
                    .exceptionally(throwable -> {
                        logger.error("Error processing chunk at offset {} length {}", offset, length, throwable);
                        return null; // Return null for exceptionally case
                    });
            chunkFutures.add(future);
        }

        // Wait for all chunks to complete without blocking
        return CompletableFuture.allOf(chunkFutures.toArray(new CompletableFuture<?>[0]))
                .thenApply(v -> {
                    logger.debug("Completed processing {} chunks for file {}", chunkCount, file);
                    return null;
                });
    }

    /**
     * Processes a single chunk asynchronously using true async I/O.
     */
    private CompletableFuture<String> processChunkAsync(AsynchronousFileChannel channel, long offset, int length,
            int chunkIndex, int totalChunks, Path file) {
        CompletableFuture<String> resultFuture = new CompletableFuture<>();

        // Acquire operation permit
        try {
            operationSemaphore.acquire();
            activeOperations.incrementAndGet();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            resultFuture.completeExceptionally(new RuntimeException("Interrupted while acquiring operation permit", e));
            return resultFuture;
        }

        // Acquire buffer asynchronously
        asyncBufferPool.acquireAsync(length)
                .thenCompose(buffer -> {
                    CompletableFuture<String> processingFuture = new CompletableFuture<>();

                    // Use CompletionHandler pattern for true async I/O
                    channel.read(buffer, offset, null, new java.nio.channels.CompletionHandler<Integer, Void>() {
                        @Override
                        public void completed(Integer bytesRead, Void attachment) {
                            if (bytesRead == -1) {
                                asyncBufferPool.releaseAsync(buffer);
                                processingFuture.completeExceptionally(new IOException("Unexpected end of file"));
                                return;
                            }

                            buffer.flip();

                            // Use the configured async helper to process the chunk (hash it)
                            asyncChunkHandler.processChunkAsync(buffer, chunkIndex, totalChunks, file)
                                    .whenComplete((hash, throwable) -> {
                                        asyncBufferPool.releaseAsync(buffer);
                                        if (throwable != null) {
                                            processingFuture.completeExceptionally(throwable);
                                        } else {
                                            processingFuture.complete(hash);
                                        }
                                    });
                        }

                        @Override
                        public void failed(Throwable exc, Void attachment) {
                            asyncBufferPool.releaseAsync(buffer);
                            processingFuture.completeExceptionally(
                                    new RuntimeException("Failed to read chunk " + chunkIndex, exc));
                        }
                    });

                    return processingFuture;
                })
                .whenComplete((hash, throwable) -> {
                    operationSemaphore.release();
                    activeOperations.decrementAndGet();
                    if (throwable != null) {
                        resultFuture.completeExceptionally(throwable);
                    } else {
                        resultFuture.complete(hash);
                    }
                });

        return resultFuture;
    }

    /**
     * Closes a file channel asynchronously.
     */
    private void closeChannelAsync(AsynchronousFileChannel channel) {
        if (channel != null) {
            CompletableFuture.runAsync(() -> {
                try {
                    channel.close();
                } catch (IOException e) {
                    logger.warn("Failed to close file channel: {}", e.getMessage());
                }
            }, executorService);
        }
    }

    /**
     * Default async chunk handler implementation.
     */
    private static class DefaultAsyncChunkHandler implements AsyncChunkHandler {
        private final Blake3Service blake3Service;

        public DefaultAsyncChunkHandler(Blake3Service blake3Service) {
            this.blake3Service = blake3Service;
        }

        @Override
        public CompletableFuture<String> processChunkAsync(ByteBuffer chunkData, int chunkIndex, int totalChunks,
                Path file) {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    byte[] data = new byte[chunkData.remaining()];
                    chunkData.get(data);
                    return blake3Service.hashBuffer(data);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to process chunk", e);
                }
            });
        }

        @Override
        public void processChunkAsync(ByteBuffer chunkData, int chunkIndex, int totalChunks, Path file,
                CompletionHandler<String, Exception> handler) {
            processChunkAsync(chunkData, chunkIndex, totalChunks, file)
                    .whenComplete((result, throwable) -> {
                        if (throwable != null) {
                            handler.failed(throwable instanceof Exception ? (Exception) throwable
                                    : new RuntimeException(throwable));
                        } else {
                            handler.completed(result);
                        }
                    });
        }

        @Override
        public CompletableFuture<String[]> processChunksAsync(ByteBuffer[] chunks, Path file) {
            List<CompletableFuture<String>> futures = new ArrayList<>(chunks.length);
            for (int i = 0; i < chunks.length; i++) {
                futures.add(processChunkAsync(chunks[i], i, chunks.length, file));
            }
            return CompletableFuture.allOf(futures.toArray(new CompletableFuture<?>[0]))
                    .thenApply(v -> {
                        String[] results = new String[chunks.length];
                        for (int i = 0; i < chunks.length; i++) {
                            results[i] = futures.get(i).join();
                        }
                        return results;
                    });
        }

        @Override
        public void processChunksAsync(ByteBuffer[] chunks, Path file,
                CompletionHandler<String[], Exception> handler) {
            processChunksAsync(chunks, file)
                    .whenComplete((result, throwable) -> {
                        if (throwable != null) {
                            handler.failed(throwable instanceof Exception ? (Exception) throwable
                                    : new RuntimeException(throwable));
                        } else {
                            handler.completed(result);
                        }
                    });
        }

        @Override
        public int getMaxConcurrentChunks() {
            return 4;
        }

        @Override
        public void setMaxConcurrentChunks(int maxConcurrentChunks) {
            // Default implementation doesn't support changing concurrency
        }
    }

    /**
     * Wrapper to make sync buffer pool async-compatible.
     */
    private static class SyncToAsyncBufferPoolWrapper implements AsyncByteBufferPool {
        private final BufferPool syncPool;

        public SyncToAsyncBufferPoolWrapper(BufferPool syncPool) {
            this.syncPool = syncPool;
        }

        @Override
        public CompletableFuture<ByteBuffer> acquireAsync(int size) {
            return CompletableFuture.supplyAsync(() -> syncPool.acquire(size));
        }

        @Override
        public CompletableFuture<Void> releaseAsync(ByteBuffer buffer) {
            return CompletableFuture.runAsync(() -> syncPool.release(buffer));
        }

        @Override
        public CompletableFuture<Void> clearAsync() {
            return CompletableFuture.runAsync(syncPool::clear);
        }

        @Override
        public CompletableFuture<Integer> getAvailableCountAsync() {
            return CompletableFuture.supplyAsync(syncPool::getAvailableCount);
        }

        @Override
        public CompletableFuture<Integer> getTotalCountAsync() {
            return CompletableFuture.supplyAsync(syncPool::getTotalCount);
        }

        @Override
        public CompletableFuture<Integer> getBuffersInUseAsync() {
            return CompletableFuture.supplyAsync(() -> syncPool.getTotalCount() - syncPool.getAvailableCount());
        }

        @Override
        public CompletableFuture<String> getStatsAsync() {
            return CompletableFuture.supplyAsync(
                    () -> String.format("SyncToAsyncBufferPoolWrapper - Total: %d, Available: %d, Default Size: %d",
                            syncPool.getTotalCount(), syncPool.getAvailableCount(), syncPool.getDefaultBufferSize()));
        }

        @Override
        public ByteBuffer acquire(int size) {
            return syncPool.acquire(size);
        }

        @Override
        public void release(ByteBuffer buffer) {
            syncPool.release(buffer);
        }

        @Override
        public void clear() {
            syncPool.clear();
        }

        @Override
        public int getAvailableCount() {
            return syncPool.getAvailableCount();
        }

        @Override
        public int getTotalCount() {
            return syncPool.getTotalCount();
        }

        @Override
        public int getDefaultBufferSize() {
            return syncPool.getDefaultBufferSize();
        }
    }
}