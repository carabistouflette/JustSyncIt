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
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Implementation of FileChunker with fixed-size chunking and async I/O.
 * Uses AsynchronousFileChannel for optimal SSD/HDD performance.
 * Follows Single Responsibility Principle by focusing only on chunking operations.
 */
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
    /** Current chunk size. */
    private volatile int chunkSize;
    /** Executor service for async operations. */
    private final ExecutorService executorService;
    /** Whether the chunker has been closed. */
    private volatile boolean closed;
    /** Content store for storing chunks. */
    private ContentStore contentStore;

    /**
     * Creates a new FixedSizeFileChunker with default settings.
     *
     * @param blake3Service BLAKE3 service for hash calculation
     * @return a new FixedSizeFileChunker with default settings
     * @throws IllegalArgumentException if blake3Service is null
     */
    public static FixedSizeFileChunker create(Blake3Service blake3Service) {
        return create(blake3Service, ByteBufferPool.create(), DEFAULT_CHUNK_SIZE, null);
    }

    /**
     * Creates a new FixedSizeFileChunker with custom settings.
     *
     * @param blake3Service BLAKE3 service for hash calculation
     * @param bufferPool    buffer pool for memory management
     * @param chunkSize    chunk size in bytes
     * @return a new FixedSizeFileChunker with custom settings
     * @throws IllegalArgumentException if parameters are invalid
     */
    public static FixedSizeFileChunker create(Blake3Service blake3Service, BufferPool bufferPool, int chunkSize) {
        return create(blake3Service, bufferPool, chunkSize, null);
    }

    /**
     * Creates a new FixedSizeFileChunker with custom settings and content store.
     *
     * @param blake3Service BLAKE3 service for hash calculation
     * @param bufferPool    buffer pool for memory management
     * @param chunkSize    chunk size in bytes
     * @param contentStore  content store for storing chunks
     * @return a new FixedSizeFileChunker with custom settings and content store
     * @throws IllegalArgumentException if parameters are invalid
     */
    public static FixedSizeFileChunker create(Blake3Service blake3Service, BufferPool bufferPool, int chunkSize,
                                             ContentStore contentStore) {
        if (blake3Service == null) {
            throw new IllegalArgumentException("BLAKE3 service cannot be null");
        }
        if (bufferPool == null) {
            throw new IllegalArgumentException("Buffer pool cannot be null");
        }
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("Chunk size must be positive");
        }

        return new FixedSizeFileChunker(blake3Service, bufferPool, chunkSize, contentStore);
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
        this.chunkSize = DEFAULT_CHUNK_SIZE;
        this.contentStore = null;
        this.executorService = Executors.newFixedThreadPool(DEFAULT_BUFFER_COUNT);
        this.closed = false;
    }

    /**
     * Creates a new FixedSizeFileChunker with custom settings.
     *
     * @param blake3Service BLAKE3 service for hash calculation
     * @param bufferPool    buffer pool for memory management
     * @param chunkSize    chunk size in bytes
     * @throws IllegalArgumentException if parameters are invalid
     * @deprecated Use {@link #create(Blake3Service, BufferPool, int)} instead
     */
    @Deprecated
    @SuppressWarnings("EI_EXPOSE_REP2")
    public FixedSizeFileChunker(Blake3Service blake3Service, BufferPool bufferPool, int chunkSize) {
        // No validation in constructor - use static factory method instead
        this.blake3Service = blake3Service;
        this.bufferPool = bufferPool;
        this.chunkSize = chunkSize;
        this.contentStore = null;
        this.executorService = Executors.newFixedThreadPool(DEFAULT_BUFFER_COUNT);
        this.closed = false;
    }

    /**
     * Creates a new FixedSizeFileChunker with custom settings and content store.
     *
     * @param blake3Service BLAKE3 service for hash calculation
     * @param bufferPool    buffer pool for memory management
     * @param chunkSize    chunk size in bytes
     * @param contentStore  content store for storing chunks
     * @throws IllegalArgumentException if parameters are invalid
     * @deprecated Use {@link #create(Blake3Service, BufferPool, int, ContentStore)} instead
     */
    @Deprecated
    @SuppressWarnings("EI_EXPOSE_REP2")
    public FixedSizeFileChunker(Blake3Service blake3Service, BufferPool bufferPool, int chunkSize,
                                ContentStore contentStore) {
        // No validation in constructor - use static factory method instead
        this.blake3Service = blake3Service;
        this.bufferPool = bufferPool;
        this.chunkSize = chunkSize;
        this.contentStore = contentStore;
        this.executorService = Executors.newFixedThreadPool(DEFAULT_BUFFER_COUNT);
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
                ? finalOptions.getChunkSize() : this.chunkSize;

        return CompletableFuture.supplyAsync(() -> {
            try {
                return performChunking(file, finalOptions, effectiveChunkSize);
            } catch (Exception e) {
                logger.error("Error chunking file: {}", file, e);
                return FileChunker.ChunkingResult.createFailed(file, e);
            }
        }, executorService);
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
    public void setBufferPool(BufferPool bufferPool) {
        if (bufferPool == null) {
            throw new IllegalArgumentException("Buffer pool cannot be null");
        }
        // Note: BufferPool is an interface, we store the reference directly
        // as these are service objects that are meant to be used directly
        this.bufferPool = bufferPool;
        logger.debug("Updated buffer pool to {}", bufferPool.getClass().getSimpleName());
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
     * Performs the actual file chunking operation.
     */
    private FileChunker.ChunkingResult performChunking(Path file, ChunkingOptions options, int effectiveChunkSize) {
        long fileSize;
        try {
            fileSize = Files.size(file);
        } catch (IOException e) {
            return FileChunker.ChunkingResult.createFailed(file, e);
        }

        // Handle empty file case
        if (fileSize == 0) {
            try {
                String fileHash = blake3Service.hashBuffer(new byte[0]);
                return new FileChunker.ChunkingResult(file, 0, 0, 0, fileHash, new ArrayList<>());
            } catch (Exception e) {
                return FileChunker.ChunkingResult.createFailed(file, e);
            }
        }

        // Calculate number of chunks
        int chunkCount = (int) Math.ceil((double) fileSize / effectiveChunkSize);
        List<String> chunkHashes = new ArrayList<>(chunkCount);

        logger.debug("Chunking file {} ({} bytes) into {} chunks of {} bytes each",
                file, fileSize, chunkCount, effectiveChunkSize);

        // For small files (less than 1MB), use sync I/O to avoid channel closure issues
        // Async I/O provides no benefit for small files and causes channel closure problems
        boolean useAsyncIO = options.isUseAsyncIO() && fileSize >= 1024 * 1024;

        if (useAsyncIO) {
            return performAsyncChunking(file, options, effectiveChunkSize, fileSize, chunkCount, chunkHashes);
        } else {
            return performSyncChunking(file, options, effectiveChunkSize, fileSize, chunkCount, chunkHashes);
        }
    }

    /**
     * Performs chunking using asynchronous I/O.
     */
    private FileChunker.ChunkingResult performAsyncChunking(Path file, ChunkingOptions options, int chunkSize,
                                             long fileSize, int chunkCount, List<String> chunkHashes) {
        AsynchronousFileChannel channel = null;
        try {
            channel = AsynchronousFileChannel.open(file, StandardOpenOption.READ);
            String fileHash = calculateFileHash(channel, fileSize);
            AtomicInteger completedChunks = new AtomicInteger(0);
            CompletableFuture<Void>[] chunkFutures = new CompletableFuture[chunkCount];

            // Process chunks concurrently
            for (int i = 0; i < chunkCount; i++) {
                final int chunkIndex = i;
                final long offset = (long) i * chunkSize;
                final int length = (int) Math.min(chunkSize, fileSize - offset);

                chunkFutures[i] = processChunkAsync(channel, offset, length, chunkIndex)
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

            // Wait for all chunks to complete with timeout
            try {
                CompletableFuture.allOf(chunkFutures).get(120, java.util.concurrent.TimeUnit.SECONDS);
            } catch (java.util.concurrent.TimeoutException e) {
                logger.error("Timeout waiting for chunk processing after 120 seconds", e);
                // Cancel any remaining futures
                for (CompletableFuture<?> future : chunkFutures) {
                    future.cancel(true);
                }
                throw new java.util.concurrent.CompletionException("Chunk processing timed out after 120 seconds", e);
            } catch (java.lang.InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new java.util.concurrent.CompletionException("Interrupted while waiting for chunk processing", e);
            } catch (java.util.concurrent.ExecutionException e) {
                throw new java.util.concurrent.CompletionException("Failed to process chunks", e);
            }

            FileChunker.ChunkingResult result = new FileChunker.ChunkingResult(
                    file, chunkCount, fileSize, 0, fileHash, chunkHashes);

            // Close channel after all async operations complete successfully
            if (channel != null) {
                try {
                    channel.close();
                } catch (IOException e) {
                    logger.warn("Failed to close file channel: {}", e.getMessage());
                }
            }

            return result;
        } catch (IOException e) {
            // Close channel on exception
            if (channel != null) {
                try {
                    channel.close();
                } catch (IOException closeException) {
                    logger.warn("Failed to close file channel during exception handling: {}",
                            closeException.getMessage());
                }
            }
            return FileChunker.ChunkingResult.createFailed(file, e);
        } catch (RuntimeException e) {
            // Close channel on exception
            if (channel != null) {
                try {
                    channel.close();
                } catch (IOException closeException) {
                    logger.warn("Failed to close file channel during exception handling: {}",
                            closeException.getMessage());
                }
            }
            return FileChunker.ChunkingResult.createFailed(file, e);
        }
    }

    /**
     * Performs chunking using synchronous I/O.
     */
    private FileChunker.ChunkingResult performSyncChunking(Path file, ChunkingOptions options, int chunkSize,
                                             long fileSize, int chunkCount, List<String> chunkHashes) {
        AsynchronousFileChannel channel = null;
        try {
            channel = AsynchronousFileChannel.open(file, StandardOpenOption.READ);
            String fileHash = calculateFileHash(channel, fileSize);

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
    }

    /**
     * Processes a single chunk asynchronously with enhanced error handling.
     */
    private CompletableFuture<String> processChunkAsync(AsynchronousFileChannel channel, long offset, int length,
                                                    int chunkIndex) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return processChunkSync(channel, offset, length);
            } catch (OutOfMemoryError e) {
                logger.error("Out of memory while processing chunk at offset {} length {}", offset, length, e);
                throw new java.util.concurrent.CompletionException("Insufficient memory for chunk processing", e);
            } catch (Exception e) {
                logger.error("Unexpected error processing chunk at offset {} length {} chunk {}",
                        offset, length, chunkIndex, e);
                throw new java.util.concurrent.CompletionException("Failed to process chunk " + chunkIndex, e);
            }
        }, executorService);
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
     * Calculates the hash of the entire file using incremental hashing for large files.
     */
    private String calculateFileHash(AsynchronousFileChannel channel, long fileSize) throws IOException {
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
                return calculateFileHashIncrementally(channel, fileSize);
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
            com.justsyncit.hash.IncrementalHasherFactory hasherFactory =
                    new com.justsyncit.hash.Blake3IncrementalHasherFactory(
                            com.justsyncit.hash.Sha256HashAlgorithm.create());
            com.justsyncit.hash.IncrementalHasherFactory.IncrementalHasher incrementalHasher =
                    hasherFactory.createIncrementalHasher();

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
     * Closes the chunker and releases resources.
     */
    public void close() {
        if (closed) {
            return;
        }

        closed = true;
        executorService.shutdown();
        bufferPool.clear();
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
                ? contentStore.getClass().getSimpleName() : "null");
    }
}