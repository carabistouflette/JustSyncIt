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

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for managing ByteBuffer pools with asynchronous operations.
 * Extends BufferPool to maintain compatibility with existing synchronous APIs.
 * Follows Interface Segregation Principle by providing focused async buffer management operations.
 */
public interface AsyncByteBufferPool extends BufferPool {

    /**
     * Asynchronously acquires a ByteBuffer of the specified size from the pool.
     * If no buffer of the exact size is available, a larger one may be returned.
     *
     * @param size the minimum buffer size required
     * @return a CompletableFuture that completes with a ByteBuffer with at least the specified capacity
     * @throws IllegalArgumentException if size is not positive
     */
    CompletableFuture<ByteBuffer> acquireAsync(int size);

    /**
     * Asynchronously releases a ByteBuffer back to the pool for reuse.
     * The buffer will be cleared and reset before being made available again.
     *
     * @param buffer the buffer to release
     * @return a CompletableFuture that completes when the buffer has been released
     * @throws IllegalArgumentException if buffer is null or not from this pool
     */
    CompletableFuture<Void> releaseAsync(ByteBuffer buffer);

    /**
     * Asynchronously clears all buffers from the pool and releases associated resources.
     * After calling this method, the pool should not be used.
     *
     * @return a CompletableFuture that completes when the pool has been cleared
     */
    CompletableFuture<Void> clearAsync();

    /**
     * Asynchronously gets the current number of available buffers in the pool.
     *
     * @return a CompletableFuture that completes with the number of available buffers
     */
    CompletableFuture<Integer> getAvailableCountAsync();

    /**
     * Asynchronously gets the total number of buffers managed by this pool (both available and in use).
     *
     * @return a CompletableFuture that completes with the total number of managed buffers
     */
    CompletableFuture<Integer> getTotalCountAsync();

    /**
     * Asynchronously gets the number of buffers currently in use.
     *
     * @return a CompletableFuture that completes with the number of buffers in use
     */
    CompletableFuture<Integer> getBuffersInUseAsync();

    /**
     * Asynchronously gets statistics about the buffer pool.
     *
     * @return a CompletableFuture that completes with pool statistics
     */
    CompletableFuture<String> getStatsAsync();
}