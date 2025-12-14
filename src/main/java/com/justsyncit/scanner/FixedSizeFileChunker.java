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

import com.justsyncit.hash.Blake3Service;
import com.justsyncit.storage.ContentStore;
import com.justsyncit.storage.StorageIntegrityException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
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

/**
 * Implementation of FileChunker with fixed-size chunking and async I/O.
 * Uses AsynchronousFileChannel for optimal SSD/HDD performance.
 * Follows Single Responsibility Principle by focusing only on chunking
 * operations.
 */
@SuppressFBWarnings({ "EI_EXPOSE_REP", "EI_EXPOSE_REP2" })
public class FixedSizeFileChunker implements FileChunker {

    /** Logger for the chunker. */
    private static final Logger logger = LoggerFactory.getLogger(FixedSizeFileChunker.class);

    /** Default chunk size (64KB). */
    private static final int DEFAULT_CHUNK_SIZE = 64 * 1024;
    /** Default buffer count. */
    private static final int DEFAULT_BUFFER_COUNT = 4;

    /** BLAKE3 service for hash calculation. */
    private final Blake3Service blake3Service;
    /** Buffer pool for memory management. */
    private BufferPool bufferPool;
    /** Async buffer pool for memory management. */
    private AsyncByteBufferPool asyncBufferPool;
    /** Current chunk size. */
    private volatile int chunkSize;
    /** Executor service for async operations. */
    private final ExecutorService executorService;
    /** Whether the chunker has been closed. */
    private volatile boolean closed;
    /** Content store for storing chunks. */
    private ContentStore contentStore;
    /** Semaphore for controlling concurrent operations. */
    private final Semaphore operationSemaphore;
    /** Number of currently active operations. */
    private final AtomicInteger activeOperations;
    /** Maximum number of concurrent operations. */
    private volatile int maxConcurrentOperations;

    /**
     * Creates a new FixedSizeFileChunker with default settings.
     *
     * @param blake3Service BLAKE3 service for hash calculation
     * @return a new FixedSizeFileChunker with default settings
     * @throws IllegalArgumentException if blake3Service is null
     */
    public static FixedSizeFileChunker create(Blake3Service blake3Service) {
        return create(blake3Service, ByteBufferPool.create(), AsyncByteBufferPoolImpl.create(), DEFAULT_CHUNK_SIZE,
                null);
    }

    /**
     * Creates a new FixedSizeFileChunker with custom settings.
     *
     * @param blake3Service BLAKE3 service for hash calculation
     * @param bufferPool    buffer pool for memory management
     * @param chunkSize     chunk size in bytes
     * @return a new FixedSizeFileChunker with custom settings
     * @throws IllegalArgumentException if parameters are invalid
     */
    public static FixedSizeFileChunker create(Blake3Service blake3Service, BufferPool bufferPool, int chunkSize) {
        return create(blake3Service, bufferPool, AsyncByteBufferPoolImpl.create(), chunkSize, null);
    }

    /**
     * Creates a new FixedSizeFileChunker with custom settings and content store.
     *
     * @param blake3Service BLAKE3 service for hash calculation
     * @param bufferPool    buffer pool for memory management
     * @param chunkSize     chunk size in bytes
     * @param contentStore  content store for storing chunks
     * @return a new FixedSizeFileChunker with custom settings and content store
     * @throws IllegalArgumentException if parameters are invalid
     */
    public static FixedSizeFileChunker create(Blake3Service blake3Service, BufferPool bufferPool, int chunkSize,
            ContentStore contentStore) {
        return create(blake3Service, bufferPool, AsyncByteBufferPoolImpl.create(), chunkSize, contentStore);
    }

    /**
     * Creates a new FixedSizeFileChunker with custom settings and async buffer
     * pool.
     *
     * @param blake3Service   BLAKE3 service for hash calculation
     * @param bufferPool      buffer pool for memory management
     * @param asyncBufferPool async buffer pool for memory management
     * @param chunkSize       chunk size in bytes
     * @param contentStore    content store for storing chunks
     * @return a new FixedSizeFileChunker with custom settings and content store
     * @throws IllegalArgumentException if parameters are invalid
     */
    public static FixedSizeFileChunker create(Blake3Service blake3Service, BufferPool bufferPool,
            AsyncByteBufferPool asyncBufferPool, int chunkSize,
            ContentStore contentStore) {
        if (blake3Service == null) {
            throw new IllegalArgumentException("BLAKE3 service cannot be null");
        }
        if (bufferPool == null) {
            throw new IllegalArgumentException("Buffer pool cannot be null");
        }
        if (asyncBufferPool == null) {
            throw new IllegalArgumentException("Async buffer pool cannot be null");
        }
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("Chunk size must be positive");
        }

        return new FixedSizeFileChunker(blake3Service, bufferPool, asyncBufferPool, chunkSize, contentStore);
    }

    /**
     * Creates a new FixedSizeFileChunker with default settings.
     *
     * @param blake3Service BLAKE3 service for hash calculation
     * @throws IllegalArgumentException if blake3Service is null
     * @deprecated Use {@link #create(Blake3Service)} instead
     */
    @Deprecated
    public FixedSizeFileChunker(Blake3Service blake3Service) {
        // No validation in constructor - use static factory method instead
        this.blake3Service = blake3Service;
        this.bufferPool = ByteBufferPool.create();
        this.asyncBufferPool = AsyncByteBufferPoolImpl.create();
        this.chunkSize = DEFAULT_CHUNK_SIZE;
        this.contentStore = null;
        this.maxConcurrentOperations = DEFAULT_BUFFER_COUNT;
        this.activeOperations = new AtomicInteger(0);
        this.executorService = Executors.newFixedThreadPool(DEFAULT_BUFFER_COUNT);
        this.operationSemaphore = new Semaphore(maxConcurrentOperations);
        this.closed = false;
    }

    /**
     * Creates a new FixedSizeFileChunker with custom settings.
     *
     * @param blake3Service BLAKE3 service for hash calculation
     * @param bufferPool    buffer pool for memory management
     * @param chunkSize     chunk size in bytes
     * @throws IllegalArgumentException if parameters are invalid
     * @deprecated Use {@link #create(Blake3Service, BufferPool, int)} instead
     */
    @Deprecated
    @SuppressWarnings("EI_EXPOSE_REP2")
    @SuppressFBWarnings("EI_EXPOSE_REP2")
    public FixedSizeFileChunker(Blake3Service blake3Service, BufferPool bufferPool, int chunkSize) {
        // No validation in constructor - use static factory method instead
        this.blake3Service = blake3Service;
        this.bufferPool = bufferPool;
        this.asyncBufferPool = AsyncByteBufferPoolImpl.create();
        this.chunkSize = chunkSize;
        this.contentStore = null;
        this.maxConcurrentOperations = DEFAULT_BUFFER_COUNT;
        this.activeOperations = new AtomicInteger(0);
        this.executorService = Executors.newFixedThreadPool(DEFAULT_BUFFER_COUNT);
        this.operationSemaphore = new Semaphore(maxConcurrentOperations);
        this.closed = false;
    }

    /**
     * Creates a new FixedSizeFileChunker with custom settings and content store.
     *
     * @param blake3Service BLAKE3 service for hash calculation
     * @param bufferPool    buffer pool for memory management
     * @param chunkSize     chunk size in bytes
     * @param contentStore  content store for storing chunks
     * @throws IllegalArgumentException if parameters are invalid
     * @deprecated Use {@link #create(Blake3Service, BufferPool, int, ContentStore)}
     *             instead
     */
    @Deprecated
    @SuppressWarnings("EI_EXPOSE_REP2")
    @SuppressFBWarnings("EI_EXPOSE_REP2")
    public FixedSizeFileChunker(Blake3Service blake3Service, BufferPool bufferPool, int chunkSize,
            ContentStore contentStore) {
        // No validation in constructor - use static factory method instead
        this.blake3Service = blake3Service;
        this.bufferPool = bufferPool;
        this.asyncBufferPool = AsyncByteBufferPoolImpl.create();
        this.chunkSize = chunkSize;
        this.contentStore = contentStore;
        this.maxConcurrentOperations = DEFAULT_BUFFER_COUNT;
        this.activeOperations = new AtomicInteger(0);
        this.executorService = Executors.newFixedThreadPool(DEFAULT_BUFFER_COUNT);
        this.operationSemaphore = new Semaphore(maxConcurrentOperations);
        this.closed = false;
    }

    /**
     * Creates a new FixedSizeFileChunker with specified settings.
     */
    private FixedSizeFileChunker(Blake3Service blake3Service, BufferPool bufferPool,
            AsyncByteBufferPool asyncBufferPool, int chunkSize,
            ContentStore contentStore) {
        this.blake3Service = blake3Service;
        this.bufferPool = bufferPool;
        this.asyncBufferPool = asyncBufferPool;
        this.chunkSize = chunkSize;
        this.contentStore = contentStore;
        this.maxConcurrentOperations = DEFAULT_BUFFER_COUNT;
        this.activeOperations = new AtomicInteger(0);
        this.executorService = Executors.newFixedThreadPool(DEFAULT_BUFFER_COUNT);
        this.operationSemaphore = new Semaphore(maxConcurrentOperations);
        this.closed = false;
    }

    @Override
    public CompletableFuture<ChunkingResult> chunkFile(Path file, ChunkingOptions options) {
        if (file == null) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("File cannot be null"));
        }
        if (closed) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Chunker has been closed"));
        }
        if (!Files.exists(file)) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("File does not exist: " + file));
        }
        if (!Files.isRegularFile(file)) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Path is not a regular file: " + file));
        }

        final ChunkingOptions finalOptions = options != null ? options : new ChunkingOptions();
        final int effectiveChunkSize = finalOptions.getChunkSize() > 0
                ? finalOptions.getChunkSize()
                : this.chunkSize;

        return performChunkingAsync(file, finalOptions, effectiveChunkSize);
    }

    @Override
    public String storeChunk(byte[] data) throws IOException {
        // This method is not implemented in FileChunker
        // Chunk storage should be handled by ContentStore
        throw new UnsupportedOperationException("storeChunk not implemented in FileChunker");
    }

    @Override
    public byte[] retrieveChunk(String hash) throws IOException, StorageIntegrityException {
        // This would integrate with ContentStore in a real implementation
        throw new UnsupportedOperationException("retrieveChunk not implemented in FileChunker");
    }

    @Override
    public boolean existsChunk(String hash) throws IOException {
        // This would integrate with ContentStore in a real implementation
        throw new UnsupportedOperationException("existsChunk not implemented in FileChunker");
    }

    @Override
    @SuppressWarnings("EI_EXPOSE_REP2")
    @SuppressFBWarnings("EI_EXPOSE_REP2")
    public void setBufferPool(BufferPool bufferPool) {
        if (bufferPool == null) {
            throw new IllegalArgumentException("Buffer pool cannot be null");
        }
        // Note: BufferPool is an interface, we store the reference directly
        // as these are service objects that are meant to be used directly
        this.bufferPool = bufferPool;
        logger.debug("Updated buffer pool to {}", bufferPool.getClass().getSimpleName());
    }

    /**
     * Sets the async buffer pool for memory management.
     * If no pool is set, a default async pool will be used.
     *
     * @param asyncBufferPool the async buffer pool to use
     * @throws IllegalArgumentException if asyncBufferPool is null
     */
    public void setAsyncBufferPool(AsyncByteBufferPool asyncBufferPool) {
        if (asyncBufferPool == null) {
            throw new IllegalArgumentException("Async buffer pool cannot be null");
        }
        this.asyncBufferPool = asyncBufferPool;
        logger.debug("Updated async buffer pool to {}", asyncBufferPool.getClass().getSimpleName());
    }

    /**
     * Gets the current async buffer pool.
     *
     * @return the current async buffer pool
     */
    public AsyncByteBufferPool getAsyncBufferPool() {
        return asyncBufferPool;
    }

    /**
     * Gets the current number of active operations.
     *
     * @return the number of active operations
     */
    public int getActiveOperations() {
        return activeOperations.get();
    }

    /**
     * Gets the maximum number of concurrent operations allowed.
     *
     * @return the maximum number of concurrent operations
     */
    public int getMaxConcurrentOperations() {
        return maxConcurrentOperations;
    }

    /**
     * Sets the maximum number of concurrent operations allowed.
     *
     * @param maxConcurrentOperations the maximum number of concurrent operations
     * @throws IllegalArgumentException if maxConcurrentOperations is not positive
     */
    public void setMaxConcurrentOperations(int maxConcurrentOperations) {
        if (maxConcurrentOperations <= 0) {
            throw new IllegalArgumentException("Max concurrent operations must be positive");
        }
        this.maxConcurrentOperations = maxConcurrentOperations;
        // Update semaphore permits
        operationSemaphore.drainPermits();
        operationSemaphore.release(maxConcurrentOperations);
        logger.debug("Updated max concurrent operations to {}", maxConcurrentOperations);
    }

    /**
     * Checks if the chunker has been closed.
     *
     * @return true if closed, false otherwise
     */
    public boolean isClosed() {
        return closed;
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

    /**
     * Performs the actual file chunking operation asynchronously.
     */
    private CompletableFuture<FileChunker.ChunkingResult> performChunkingAsync(Path file, ChunkingOptions options,
            int effectiveChunkSize) {
        CompletableFuture<FileChunker.ChunkingResult> resultFuture = new CompletableFuture<>();

        try {
            long fileSize = Files.size(file);

            // Handle empty file case
            if (fileSize == 0) {
                try {
                    String fileHash = blake3Service.hashBuffer(new byte[0]);
                    resultFuture.complete(new FileChunker.ChunkingResult(file, 0, 0, 0, fileHash, new ArrayList<>()));
                } catch (Exception e) {
                    resultFuture.complete(FileChunker.ChunkingResult.createFailed(file, e));
                }
                return resultFuture;
            }

            // Calculate number of chunks
            int chunkCount = (int) Math.ceil((double) fileSize / effectiveChunkSize);
            List<String> chunkHashes = new ArrayList<>(chunkCount);

            logger.debug("Chunking file {} ({} bytes) into {} chunks of {} bytes each",
                    file, fileSize, chunkCount, effectiveChunkSize);

            // For small files (less than 1MB), use sync I/O to avoid channel closure issues
            // Async I/O provides no benefit for small files and causes channel closure
            // problems
            boolean useAsyncIO = options.isUseAsyncIO() && fileSize >= 1024 * 1024;

            if (useAsyncIO) {
                return performAsyncChunking(file, options, effectiveChunkSize, fileSize, chunkCount, chunkHashes);
            } else {
                return performSyncChunking(file, options, effectiveChunkSize, fileSize, chunkCount, chunkHashes);
            }
        } catch (IOException e) {
            resultFuture.complete(FileChunker.ChunkingResult.createFailed(file, e));
        }

        return resultFuture;
    }

    /**
     * Performs chunking using true asynchronous I/O with CompletionHandler pattern.
     */
    private CompletableFuture<FileChunker.ChunkingResult> performAsyncChunking(Path file, ChunkingOptions options,
            int chunkSize,
            long fileSize, int chunkCount, List<String> chunkHashes) {
        CompletableFuture<FileChunker.ChunkingResult> resultFuture = new CompletableFuture<>();

        // Update max concurrent operations if specified in options
        if (options.getMaxConcurrentChunks() != this.maxConcurrentOperations) {
            this.maxConcurrentOperations = options.getMaxConcurrentChunks();
            // Update semaphore permits
            operationSemaphore.drainPermits();
            operationSemaphore.release(this.maxConcurrentOperations);
        }

        AsynchronousFileChannel channel = null;
        try {
            channel = AsynchronousFileChannel.open(file, StandardOpenOption.READ);
            final AsynchronousFileChannel finalChannel = channel;

            // Calculate file hash asynchronously
            calculateFileHashAsync(finalChannel, fileSize)
                    .thenCompose(fileHash -> {
                        // Process chunks concurrently using true async I/O
                        return processAllChunksAsync(finalChannel, file, chunkSize, fileSize, chunkCount, chunkHashes)
                                .thenApply(v -> new FileChunker.ChunkingResult(file, chunkCount, fileSize, 0, fileHash,
                                        chunkHashes));
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
        }

        return resultFuture;
    }

    /**
     * Performs chunking using synchronous I/O.
     */
    private CompletableFuture<FileChunker.ChunkingResult> performSyncChunking(Path file, ChunkingOptions options,
            int chunkSize,
            long fileSize, int chunkCount, List<String> chunkHashes) {
        return CompletableFuture.supplyAsync(() -> {
            AsynchronousFileChannel channel = null;
            try {
                channel = AsynchronousFileChannel.open(file, StandardOpenOption.READ);
                String fileHash = calculateFileHashSync(channel, fileSize);

                // Process chunks sequentially
                for (int i = 0; i < chunkCount; i++) {
                    long offset = (long) i * chunkSize;
                    int length = (int) Math.min(chunkSize, fileSize - offset);

                    String chunkHash = processChunkSync(channel, offset, length);
                    chunkHashes.add(chunkHash);
                }

                return new FileChunker.ChunkingResult(file, chunkCount, fileSize, 0, fileHash, chunkHashes);
            } catch (Exception e) {
                return FileChunker.ChunkingResult.createFailed(file, e);
            } finally {
                if (channel != null) {
                    try {
                        channel.close();
                    } catch (IOException e) {
                        logger.warn("Failed to close file channel: {}", e.getMessage());
                    }
                }
            }
        }, executorService);
    }

    /**
     * Processes all chunks asynchronously using true async I/O.
     */
    private CompletableFuture<Void> processAllChunksAsync(AsynchronousFileChannel channel, Path file, int chunkSize,
            long fileSize, int chunkCount, List<String> chunkHashes) {
        @SuppressWarnings("unchecked")
        CompletableFuture<Void>[] chunkFutures = (CompletableFuture<Void>[]) new CompletableFuture<?>[chunkCount];
        AtomicInteger completedChunks = new AtomicInteger(0);

        // Submit all chunk processing tasks
        for (int i = 0; i < chunkCount; i++) {
            final int chunkIndex = i;
            final long offset = (long) i * chunkSize;
            final int length = (int) Math.min(chunkSize, fileSize - offset);

            chunkFutures[i] = processChunkAsync(channel, offset, length, chunkIndex, file)
                    .thenAccept(hash -> {
                        synchronized (chunkHashes) {
                            chunkHashes.add(hash);
                        }
                        completedChunks.incrementAndGet();
                    })
                    .exceptionally(throwable -> {
                        logger.error("Error processing chunk at offset {} length {}", offset, length, throwable);
                        return null; // Return null for exceptionally case
                    });
        }

        // Wait for all chunks to complete without blocking
        return CompletableFuture.allOf(chunkFutures)
                .thenApply(v -> {
                    logger.debug("Completed processing {} chunks for file {}", chunkCount, file);
                    return null;
                });
    }

    /**
     * Processes a single chunk asynchronously using true async I/O with
     * CompletionHandler.
     */
    private CompletableFuture<String> processChunkAsync(AsynchronousFileChannel channel, long offset, int length,
            int chunkIndex, Path file) {
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
                    CompletableFuture<Integer> readFuture = new CompletableFuture<>();

                    // Use CompletionHandler pattern for true async I/O
                    channel.read(buffer, offset, null, new CompletionHandler<Integer, Void>() {
                        @Override
                        public void completed(Integer bytesRead, Void attachment) {
                            if (bytesRead == -1) {
                                asyncBufferPool.releaseAsync(buffer);
                                operationSemaphore.release();
                                activeOperations.decrementAndGet();
                                resultFuture.completeExceptionally(new IOException("Unexpected end of file"));
                                return;
                            }

                            buffer.flip();
                            byte[] chunkData = new byte[buffer.remaining()];
                            buffer.get(chunkData);

                            // Release buffer
                            asyncBufferPool.releaseAsync(buffer);

                            try {
                                // Calculate hash
                                String hash = blake3Service.hashBuffer(chunkData);

                                // Store chunk if content store is available
                                if (contentStore != null) {
                                    try {
                                        contentStore.storeChunk(chunkData);
                                        logger.debug("Stored chunk {} ({} bytes)", hash, chunkData.length);
                                    } catch (IOException e) {
                                        logger.warn("Failed to store chunk {}: {}", hash, e.getMessage());
                                        // Don't fail the operation - the hash is still valid
                                    }
                                }

                                operationSemaphore.release();
                                activeOperations.decrementAndGet();
                                resultFuture.complete(hash);

                            } catch (Exception e) {
                                operationSemaphore.release();
                                activeOperations.decrementAndGet();
                                resultFuture.completeExceptionally(
                                        new RuntimeException("Failed to process chunk " + chunkIndex, e));
                            }
                        }

                        @Override
                        public void failed(Throwable exc, Void attachment) {
                            asyncBufferPool.releaseAsync(buffer);
                            operationSemaphore.release();
                            activeOperations.decrementAndGet();
                            resultFuture.completeExceptionally(
                                    new RuntimeException("Failed to read chunk " + chunkIndex, exc));
                        }
                    });

                    return readFuture;
                })
                .exceptionally(throwable -> {
                    operationSemaphore.release();
                    activeOperations.decrementAndGet();
                    resultFuture.completeExceptionally(throwable);
                    return null;
                });

        return resultFuture;
    }

    /**
     * Processes a single chunk synchronously.
     */
    private String processChunkSync(AsynchronousFileChannel channel, long offset, int length) {
        ByteBuffer buffer = bufferPool.acquire(length);
        try {
            // Read chunk data
            channel.read(buffer, offset).get();
            buffer.flip();

            // Calculate hash
            byte[] chunkData = new byte[buffer.remaining()];
            buffer.get(chunkData);

            String hash = blake3Service.hashBuffer(chunkData);

            // Store chunk if content store is available
            if (contentStore != null) {
                try {
                    // Just try to store the chunk - content store should handle deduplication
                    // This avoids the extra existsChunk check which can cause database contention
                    contentStore.storeChunk(chunkData);
                    logger.debug("Stored chunk {} ({} bytes)", hash, chunkData.length);
                } catch (IOException e) {
                    logger.warn("Failed to store chunk {}: {}", hash, e.getMessage());
                    // Don't fail the operation - the hash is still valid
                }
            }

            return hash;
        } catch (java.lang.InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new java.util.concurrent.CompletionException("Interrupted while processing chunk", e);
        } catch (java.util.concurrent.ExecutionException e) {
            throw new java.util.concurrent.CompletionException("Failed to read chunk", e);
        } catch (com.justsyncit.hash.HashingException e) {
            throw new java.util.concurrent.CompletionException("Failed to hash chunk", e);
        } catch (RuntimeException e) {
            logger.error("Error processing chunk at offset {} length {}", offset, length, e);
            throw new java.util.concurrent.CompletionException("Failed to process chunk", e);
        } finally {
            bufferPool.release(buffer);
        }
    }

    /**
     * Calculates the hash of the entire file asynchronously.
     */
    private CompletableFuture<String> calculateFileHashAsync(AsynchronousFileChannel channel, long fileSize) {
        // Handle empty file case
        if (fileSize == 0) {
            try {
                String hash = blake3Service.hashBuffer(new byte[0]);
                return CompletableFuture.completedFuture(hash);
            } catch (Exception e) {
                logger.error("Error hashing empty file", e);
                return CompletableFuture.failedFuture(new IOException("Failed to calculate empty file hash", e));
            }
        }

        // Use incremental hashing for large files to avoid memory issues
        if (fileSize <= asyncBufferPool.getDefaultBufferSize()) {
            // Small file - read all at once
            return asyncBufferPool.acquireAsync((int) fileSize)
                    .thenCompose(buffer -> {
                        CompletableFuture<String> hashFuture = new CompletableFuture<>();

                        channel.read(buffer, 0, null, new CompletionHandler<Integer, Void>() {
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
            // Large file - use incremental hashing
            return calculateFileHashIncrementallyAsync(channel, fileSize);
        }
    }

    /**
     * Calculates the hash of the entire file synchronously (for small files).
     */
    private String calculateFileHashSync(AsynchronousFileChannel channel, long fileSize) throws IOException {
        // Handle empty file case
        if (fileSize == 0) {
            try {
                return blake3Service.hashBuffer(new byte[0]);
            } catch (Exception e) {
                logger.error("Error hashing empty file", e);
                throw new IOException("Failed to calculate empty file hash", e);
            }
        }

        try {
            // Use incremental hashing for large files to avoid memory issues
            if (fileSize <= bufferPool.getDefaultBufferSize()) {
                // Small file - read all at once
                ByteBuffer buffer = bufferPool.acquire((int) fileSize);
                try {
                    int bytesRead = channel.read(buffer, 0).get();
                    buffer.flip();

                    byte[] fileData = new byte[bytesRead];
                    buffer.get(fileData);

                    return blake3Service.hashBuffer(fileData);
                } finally {
                    bufferPool.release(buffer);
                }
            } else {
                // Large file - use incremental hashing
                return calculateFileHashIncrementallySync(channel, fileSize);
            }
        } catch (Exception e) {
            logger.error("Error calculating file hash", e);
            throw new IOException("Failed to calculate file hash", e);
        }
    }

    /**
     * Calculates file hash incrementally for large files.
     */
    private String calculateFileHashIncrementally(AsynchronousFileChannel channel, long fileSize) throws IOException {
        try {
            com.justsyncit.hash.IncrementalHasherFactory hasherFactory = new com.justsyncit.hash.Blake3IncrementalHasherFactory(
                    com.justsyncit.hash.Sha256HashAlgorithm.create());
            com.justsyncit.hash.IncrementalHasherFactory.IncrementalHasher incrementalHasher = hasherFactory
                    .createIncrementalHasher();

            ByteBuffer buffer = bufferPool.acquire(bufferPool.getDefaultBufferSize());
            try {
                long position = 0;
                while (position < fileSize) {
                    buffer.clear();
                    int bytesRead = channel.read(buffer, position).get();
                    if (bytesRead <= 0) {
                        break;
                    }

                    buffer.flip();
                    int actualBytesRead = buffer.remaining();
                    if (actualBytesRead <= 0) {
                        break;
                    }

                    // Create a byte array of the exact size needed
                    byte[] chunkData = new byte[actualBytesRead];
                    buffer.get(chunkData);

                    incrementalHasher.update(chunkData);
                    position += bytesRead;
                }
                return incrementalHasher.digest();
            } finally {
                bufferPool.release(buffer);
            }
        } catch (Exception e) {
            logger.error("Error in incremental file hashing", e);
            throw new IOException("Failed to calculate incremental file hash", e);
        }
    }

    /**
     * Calculates file hash incrementally for large files asynchronously.
     */
    private CompletableFuture<String> calculateFileHashIncrementallyAsync(AsynchronousFileChannel channel,
            long fileSize) {
        try {
            com.justsyncit.hash.IncrementalHasherFactory hasherFactory = new com.justsyncit.hash.Blake3IncrementalHasherFactory(
                    com.justsyncit.hash.Sha256HashAlgorithm.create());
            com.justsyncit.hash.IncrementalHasherFactory.IncrementalHasher incrementalHasher = hasherFactory
                    .createIncrementalHasher();

            return calculateFileHashIncrementallyRecursiveAsync(channel, fileSize, incrementalHasher, 0);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(new IOException("Failed to calculate incremental file hash", e));
        }
    }

    /**
     * Recursively processes file chunks for incremental hashing asynchronously.
     */
    private CompletableFuture<String> calculateFileHashIncrementallyRecursiveAsync(
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
        // Calculate buffer size needed

        return asyncBufferPool.acquireAsync(bufferSize)
                .thenCompose(buffer -> {
                    CompletableFuture<Integer> readFuture = new CompletableFuture<>();

                    channel.read(buffer, position, null, new CompletionHandler<Integer, Void>() {
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
                    return calculateFileHashIncrementallyRecursiveAsync(channel, fileSize, incrementalHasher,
                            newPosition);
                });
    }

    /**
     * Calculates file hash incrementally for large files synchronously.
     */
    private String calculateFileHashIncrementallySync(AsynchronousFileChannel channel, long fileSize)
            throws IOException {
        try {
            com.justsyncit.hash.IncrementalHasherFactory hasherFactory = new com.justsyncit.hash.Blake3IncrementalHasherFactory(
                    com.justsyncit.hash.Sha256HashAlgorithm.create());
            com.justsyncit.hash.IncrementalHasherFactory.IncrementalHasher incrementalHasher = hasherFactory
                    .createIncrementalHasher();

            ByteBuffer buffer = bufferPool.acquire(bufferPool.getDefaultBufferSize());
            try {
                long position = 0;
                while (position < fileSize) {
                    buffer.clear();
                    int bytesRead = channel.read(buffer, position).get();
                    if (bytesRead <= 0) {
                        break;
                    }

                    buffer.flip();
                    int actualBytesRead = buffer.remaining();
                    if (actualBytesRead <= 0) {
                        break;
                    }

                    // Create a byte array of the exact size needed
                    byte[] chunkData = new byte[actualBytesRead];
                    buffer.get(chunkData);

                    incrementalHasher.update(chunkData);
                    position += bytesRead;
                }
                return incrementalHasher.digest();
            } finally {
                bufferPool.release(buffer);
            }
        } catch (Exception e) {
            logger.error("Error in incremental file hashing", e);
            throw new IOException("Failed to calculate incremental file hash", e);
        }
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
     * Closes the chunker and releases resources.
     */
    public void close() {
        if (closed) {
            return;
        }

        closed = true;
        executorService.shutdown();
        bufferPool.clear();
        asyncBufferPool.clearAsync().join(); // Wait for async cleanup to complete
        logger.info("Closed FixedSizeFileChunker");
    }

    /**
     * Sets the content store for storing chunks.
     *
     * @param contentStore the content store to use
     */
    public void setContentStore(ContentStore contentStore) {
        // Note: ContentStore is an interface, we store the reference directly
        // as these are service objects that are meant to be used directly
        this.contentStore = contentStore;
        logger.debug("Set content store to {}", contentStore != null
                ? contentStore.getClass().getSimpleName()
                : "null");
    }
}