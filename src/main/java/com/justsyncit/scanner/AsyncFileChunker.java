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

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for asynchronous file chunking operations.
 * Extends FileChunker to maintain compatibility with existing synchronous APIs.
 * Follows Interface Segregation Principle by providing focused async chunking operations.
 * Implements both CompletionHandler pattern and CompletableFuture pattern for flexibility.
 */
public interface AsyncFileChunker extends FileChunker {

    /**
     * Asynchronously chunks a file into fixed-size pieces using CompletionHandler pattern.
     * This method provides non-blocking file operations with callback notification.
     *
     * @param file the file to chunk
     * @param options the chunking options
     * @param handler the completion handler to notify when chunking is complete
     * @throws IllegalArgumentException if file is null, invalid, or handler is null
     * @throws IllegalStateException if the chunker has been closed
     */
    void chunkFileAsync(Path file, ChunkingOptions options,
                      CompletionHandler<ChunkingResult, Exception> handler);

    /**
     * Asynchronously chunks a file into fixed-size pieces using CompletableFuture pattern.
     * This method provides non-blocking file operations with future-based composition.
     *
     * @param file the file to chunk
     * @param options the chunking options
     * @return a CompletableFuture that completes with the chunking result
     * @throws IllegalArgumentException if file is null or invalid
     * @throws IllegalStateException if the chunker has been closed
     */
    CompletableFuture<ChunkingResult> chunkFileAsync(Path file, ChunkingOptions options);

    /**
     * Sets the async buffer pool for memory management.
     * If no pool is set, a default async pool will be used.
     *
     * @param asyncBufferPool the async buffer pool to use
     * @throws IllegalArgumentException if asyncBufferPool is null
     */
    void setAsyncBufferPool(AsyncByteBufferPool asyncBufferPool);

    /**
     * Gets the current async buffer pool.
     *
     * @return the current async buffer pool
     */
    AsyncByteBufferPool getAsyncBufferPool();

    /**
     * Sets the async chunk handler for processing chunks.
     * If no handler is set, a default handler will be used.
     *
     * @param asyncChunkHandler the async chunk handler to use
     * @throws IllegalArgumentException if asyncChunkHandler is null
     */
    void setAsyncChunkHandler(AsyncChunkHandler asyncChunkHandler);

    /**
     * Gets the current async chunk handler.
     *
     * @return the current async chunk handler
     */
    AsyncChunkHandler getAsyncChunkHandler();

    /**
     * Asynchronously gets statistics about the chunker.
     *
     * @return a CompletableFuture that completes with chunker statistics
     */
    CompletableFuture<String> getStatsAsync();

    /**
     * Checks if the chunker supports overlapping I/O operations.
     *
     * @return true if overlapping I/O is supported, false otherwise
     */
    default boolean supportsOverlappingIO() {
        return true;
    }

    /**
     * Checks if the chunker supports backpressure control.
     *
     * @return true if backpressure control is supported, false otherwise
     */
    default boolean supportsBackpressure() {
        return false;
    }

    /**
     * Closes the chunker and releases all resources asynchronously.
     *
     * @return a CompletableFuture that completes when all resources have been released
     */
    CompletableFuture<Void> closeAsync();

    /**
     * Checks if the chunker has been closed.
     *
     * @return true if closed, false otherwise
     */
    boolean isClosed();

    /**
     * Gets the current number of active chunking operations.
     *
     * @return the number of active operations
     */
    int getActiveOperations();

    /**
     * Gets the maximum number of concurrent chunking operations allowed.
     *
     * @return the maximum number of concurrent operations
     */
    int getMaxConcurrentOperations();

    /**
     * Sets the maximum number of concurrent chunking operations allowed.
     *
     * @param maxConcurrentOperations the maximum number of concurrent operations
     * @throws IllegalArgumentException if maxConcurrentOperations is not positive
     */
    void setMaxConcurrentOperations(int maxConcurrentOperations);
}