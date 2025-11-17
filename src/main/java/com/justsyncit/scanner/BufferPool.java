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

import java.nio.ByteBuffer;

/**
 * Interface for managing ByteBuffer pools to optimize memory usage and performance.
 * Follows Interface Segregation Principle by providing focused buffer management operations.
 */
public interface BufferPool {

    /**
     * Acquires a ByteBuffer of the specified size from the pool.
     * If no buffer of the exact size is available, a larger one may be returned.
     *
     * @param size the minimum buffer size required
     * @return a ByteBuffer with at least the specified capacity
     * @throws IllegalArgumentException if size is not positive
     */
    ByteBuffer acquire(int size);

    /**
     * Releases a ByteBuffer back to the pool for reuse.
     * The buffer will be cleared and reset before being made available again.
     *
     * @param buffer the buffer to release
     * @throws IllegalArgumentException if buffer is null or not from this pool
     */
    void release(ByteBuffer buffer);

    /**
     * Clears all buffers from the pool and releases associated resources.
     * After calling this method, the pool should not be used.
     */
    void clear();

    /**
     * Gets the current number of available buffers in the pool.
     *
     * @return the number of available buffers
     */
    int getAvailableCount();

    /**
     * Gets the total number of buffers managed by this pool (both available and in use).
     *
     * @return the total number of managed buffers
     */
    int getTotalCount();

    /**
     * Gets the default buffer size for this pool.
     *
     * @return the default buffer size
     */
    int getDefaultBufferSize();
}