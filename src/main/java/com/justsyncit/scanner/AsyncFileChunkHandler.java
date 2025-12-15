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

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Phaser;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Implementation of AsyncChunkHandler for file chunking operations.
 * Uses BLAKE3 service for hash calculation with async processing.
 * Follows Single Responsibility Principle by focusing only on chunk processing.
 */
public class AsyncFileChunkHandler implements AsyncChunkHandler {

    /** Logger for the chunk handler. */
    private static final Logger logger = LoggerFactory.getLogger(AsyncFileChunkHandler.class);

    /** Default maximum concurrent chunks. */
    private static final int DEFAULT_MAX_CONCURRENT_CHUNKS = 4;

    /** BLAKE3 service for hash calculation. */
    private final Blake3Service blake3Service;
    /** Maximum number of concurrent chunks. */
    private volatile int maxConcurrentChunks;
    /** Number of currently processing chunks. */
    private final AtomicInteger processingChunks;
    /** Executor service for async operations. */
    private final ExecutorService executorService;
    /** Semaphore for controlling concurrent processing. */
    private Semaphore processingSemaphore;
    /** Phaser for tracking active chunk processing tasks. */
    private final Phaser phaser;
    /** Whether the handler has been closed. */
    private volatile boolean closed;

    /**
     * Creates a new AsyncFileChunkHandler with default settings.
     *
     * @param blake3Service BLAKE3 service for hash calculation
     * @return a new AsyncFileChunkHandler with default settings
     * @throws IllegalArgumentException if blake3Service is null
     */
    public static AsyncFileChunkHandler create(Blake3Service blake3Service) {
        return create(blake3Service, DEFAULT_MAX_CONCURRENT_CHUNKS);
    }

    /**
     * Creates a new AsyncFileChunkHandler with custom settings.
     *
     * @param blake3Service       BLAKE3 service for hash calculation
     * @param maxConcurrentChunks maximum number of concurrent chunks
     * @return a new AsyncFileChunkHandler with custom settings
     * @throws IllegalArgumentException if parameters are invalid
     */
    public static AsyncFileChunkHandler create(Blake3Service blake3Service, int maxConcurrentChunks) {
        if (blake3Service == null) {
            throw new IllegalArgumentException("BLAKE3 service cannot be null");
        }
        if (maxConcurrentChunks <= 0) {
            throw new IllegalArgumentException("Max concurrent chunks must be positive");
        }

        return new AsyncFileChunkHandler(blake3Service, maxConcurrentChunks);
    }

    /**
     * Creates a new AsyncFileChunkHandler with specified settings.
     */
    private AsyncFileChunkHandler(Blake3Service blake3Service, int maxConcurrentChunks) {
        this.blake3Service = blake3Service;
        this.maxConcurrentChunks = maxConcurrentChunks;
        this.processingChunks = new AtomicInteger(0);
        this.executorService = Executors.newFixedThreadPool(maxConcurrentChunks);
        this.processingSemaphore = new Semaphore(maxConcurrentChunks);
        // Register the main thread/handler itself as a party
        this.phaser = new Phaser(1);
        this.closed = false;
    }

    @Override
    public CompletableFuture<String> processChunkAsync(ByteBuffer chunkData, int chunkIndex, int totalChunks,
            Path file) {
        if (chunkData == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Chunk data cannot be null"));
        }
        if (closed) {
            return CompletableFuture.failedFuture(new IllegalStateException("Chunk handler has been closed"));
        }

        // Register this chunk task
        phaser.register();

        return CompletableFuture.supplyAsync(() -> {
            try {
                processingSemaphore.acquire();
                processingChunks.incrementAndGet();

                try {
                    return processChunkSync(chunkData, chunkIndex, totalChunks, file);
                } finally {
                    processingSemaphore.release();
                    processingChunks.decrementAndGet();
                    // Deregister when done
                    phaser.arriveAndDeregister();
                }
            } catch (InterruptedException e) {
                // Ensure we deregister even on interrupt before start
                phaser.arriveAndDeregister();
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while processing chunk " + chunkIndex, e);
            } catch (Exception e) {
                // Ensure we deregister on any other exception
                phaser.arriveAndDeregister();
                throw e;
            }
        }, executorService);
    }

    @Override
    public void processChunkAsync(ByteBuffer chunkData, int chunkIndex, int totalChunks, Path file,
            CompletionHandler<String, Exception> handler) {
        if (chunkData == null) {
            throw new IllegalArgumentException("Chunk data cannot be null");
        }
        if (handler == null) {
            throw new IllegalArgumentException("Handler cannot be null");
        }
        if (closed) {
            throw new IllegalStateException("Chunk handler has been closed");
        }

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
        if (chunks == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Chunks array cannot be null"));
        }
        if (closed) {
            return CompletableFuture.failedFuture(new IllegalStateException("Chunk handler has been closed"));
        }

        // Check for null chunks in array
        for (ByteBuffer chunk : chunks) {
            if (chunk == null) {
                return CompletableFuture
                        .failedFuture(new IllegalArgumentException("Chunk array cannot contain null elements"));
            }
        }

        @SuppressWarnings("unchecked")
        CompletableFuture<String>[] futures = (CompletableFuture<String>[]) new CompletableFuture<?>[chunks.length];
        for (int i = 0; i < chunks.length; i++) {
            final int chunkIndex = i;
            futures[i] = processChunkAsync(chunks[i], chunkIndex, chunks.length, file);
        }

        return CompletableFuture.allOf(futures)
                .thenApply(v -> {
                    String[] results = new String[chunks.length];
                    for (int i = 0; i < chunks.length; i++) {
                        results[i] = futures[i].join();
                    }
                    return results;
                });
    }

    @Override
    public void processChunksAsync(ByteBuffer[] chunks, Path file,
            CompletionHandler<String[], Exception> handler) {
        if (chunks == null) {
            throw new IllegalArgumentException("Chunks array cannot be null");
        }
        if (handler == null) {
            throw new IllegalArgumentException("Handler cannot be null");
        }
        if (closed) {
            throw new IllegalStateException("Chunk handler has been closed");
        }

        // Check for null chunks in array
        for (ByteBuffer chunk : chunks) {
            if (chunk == null) {
                throw new IllegalArgumentException("Chunk array cannot contain null elements");
            }
        }

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
        return maxConcurrentChunks;
    }

    @Override
    public void setMaxConcurrentChunks(int maxConcurrentChunks) {
        if (maxConcurrentChunks <= 0) {
            throw new IllegalArgumentException("Max concurrent chunks must be positive");
        }

        int oldMax = this.maxConcurrentChunks;
        this.maxConcurrentChunks = maxConcurrentChunks;

        // Recreate semaphore with new limit
        Semaphore newSemaphore = new Semaphore(maxConcurrentChunks);

        // Transfer permits from old semaphore to new one
        int availablePermits = processingSemaphore.availablePermits();
        if (availablePermits > 0) {
            try {
                // Drain old semaphore
                processingSemaphore.acquire(availablePermits);
                // Release to new semaphore
                newSemaphore.release(availablePermits);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while updating concurrent chunks", e);
            }
        }

        this.processingSemaphore = newSemaphore;
        logger.debug("Updated max concurrent chunks from {} to {}", oldMax, maxConcurrentChunks);
    }

    @Override
    public boolean supportsBackpressure() {
        return true;
    }

    @Override
    public CompletableFuture<Void> applyBackpressure() {
        return CompletableFuture.runAsync(() -> {
            try {
                processingSemaphore.acquire();
                logger.trace("Applied backpressure - permits available: {}", processingSemaphore.availablePermits());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while applying backpressure", e);
            }
        }, executorService);
    }

    @Override
    public CompletableFuture<Void> releaseBackpressure() {
        return CompletableFuture.runAsync(() -> {
            processingSemaphore.release();
            logger.trace("Released backpressure - permits available: {}", processingSemaphore.availablePermits());
        }, executorService);
    }

    /**
     * Processes a single chunk synchronously.
     *
     * @param chunkData   the chunk data to process
     * @param chunkIndex  the index of this chunk
     * @param totalChunks the total number of chunks
     * @param file        the source file
     * @return the hash of the processed chunk
     * @throws RuntimeException if processing fails
     */
    private String processChunkSync(ByteBuffer chunkData, int chunkIndex, int totalChunks, Path file) {
        try {
            byte[] data = new byte[chunkData.remaining()];
            chunkData.get(data);

            String hash = blake3Service.hashBuffer(data);

            logger.debug("Processed chunk {}/{} from file {} ({} bytes) -> hash {}",
                    chunkIndex + 1, totalChunks, file, data.length, hash);

            return hash;
        } catch (com.justsyncit.hash.HashingException e) {
            throw new RuntimeException("Failed to hash chunk " + chunkIndex + " from file " + file, e);
        } catch (Exception e) {
            throw new RuntimeException("Unexpected error processing chunk " + chunkIndex + " from file " + file, e);
        }
    }

    /**
     * Gets the current number of processing chunks.
     *
     * @return the number of currently processing chunks
     */
    public int getProcessingChunks() {
        return processingChunks.get();
    }

    /**
     * Gets the number of available permits for backpressure control.
     *
     * @return the number of available permits
     */
    public int getAvailablePermits() {
        return processingSemaphore.availablePermits();
    }

    /**
     * Closes the chunk handler and releases all resources.
     *
     * @return a CompletableFuture that completes when all resources have been
     *         released
     */
    public CompletableFuture<Void> closeAsync() {
        return CompletableFuture.runAsync(() -> {
            if (closed) {
                return;
            }

            closed = true;
            executorService.shutdown();

            try {
                // Arrive and deregister the main thread/handler
                phaser.arriveAndDeregister();

                // Wait for all registered parties (chunk tasks) to arrive (complete)
                // Use a reasonable timeout (e.g., 5 minute) to avoid blocking forever
                try {
                    phaser.awaitAdvanceInterruptibly(phaser.getPhase(), 5, TimeUnit.MINUTES);
                } catch (TimeoutException e) {
                    logger.warn("Timed out waiting for chunk processing to complete during close");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Interrupted while waiting for chunk processing to complete");
            }

            logger.info("Closed AsyncFileChunkHandler");
        }, executorService);
    }

    /**
     * Checks if the chunk handler has been closed.
     *
     * @return true if closed, false otherwise
     */
    public boolean isClosed() {
        return closed;
    }

    /**
     * Gets statistics about the chunk handler.
     *
     * @return a CompletableFuture that completes with handler statistics
     */
    public CompletableFuture<String> getStatsAsync() {
        return CompletableFuture.supplyAsync(() -> {
            return String.format(
                    "AsyncFileChunkHandler Stats - Max Concurrent: %d, Processing: %d, Available Permits: %d, Closed: %b",
                    maxConcurrentChunks, processingChunks.get(), getAvailablePermits(), closed);
        }, executorService);
    }
}