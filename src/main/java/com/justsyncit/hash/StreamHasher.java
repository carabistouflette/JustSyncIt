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

package com.justsyncit.hash;

import java.io.IOException;
import java.io.InputStream;

/**
 * Enhanced interface for stream hashing operations with comprehensive support
 * for progress reporting, range hashing, and performance optimization.
 *
 * <p>This interface follows the Interface Segregation Principle by focusing
 * specifically on stream operations while providing sufficient flexibility
 * for various use cases including large file processing and streaming data.</p>
 *
 * <p><strong>Thread Safety Requirements:</strong></p>
 * <ul>
 *   <li>Implementations MUST be thread-safe for concurrent hashing of different streams</li>
 *   <li>Individual stream hashing operations should not interfere with each other</li>
 *   <li>Implementations should document any thread safety limitations</li>
 * </ul>
 *
 * <p><strong>Resource Management:</strong></p>
 * <ul>
 *   <li>Input streams are never closed by implementations - callers retain ownership</li>
 *   <li>Implementations should properly manage internal resources (buffers, native handles)</li>
 *   <li>Large streams should be processed incrementally to avoid memory exhaustion</li>
 * </ul>
 *
 * <p><strong>Performance Considerations:</strong></p>
 * <ul>
 *   <li>Implementations should use appropriate buffer sizes for optimal I/O performance</li>
 *   <li>Memory usage should scale with buffer size, not stream size</li>
 *   <li>Consider CPU cache efficiency and SIMD optimizations when available</li>
 * </ul>
 */
public interface StreamHasher {

    /**
     * Hashes the entire content of an InputStream.
     * The stream will be fully consumed but not closed.
     *
     * @param inputStream the input stream to hash, must not be null
     * @return the hash as a hexadecimal string, never null
     * @throws IOException if an I/O error occurs while reading from the stream
     * @throws HashingException if a hashing error occurs
     * @throws IllegalArgumentException if the inputStream is null
     */
    String hashStream(InputStream inputStream) throws IOException, HashingException;

    /**
     * Hashes the content of an InputStream with progress reporting support.
     * The stream will be fully consumed but not closed.
     *
     * @param inputStream the input stream to hash, must not be null
     * @param progressListener optional listener for progress updates (can be null)
     * @return the hash as a hexadecimal string, never null
     * @throws IOException if an I/O error occurs while reading from the stream
     * @throws HashingException if a hashing error occurs
     * @throws IllegalArgumentException if the inputStream is null
     */
    String hashStream(InputStream inputStream, HashProgressListener progressListener)
            throws IOException, HashingException;

    /**
     * Hashes a specific portion of an InputStream.
     * The stream will be consumed from the specified offset but not closed.
     *
     * @param inputStream the input stream to hash, must not be null
     * @param offset the starting offset in the stream (bytes to skip), must be >= 0
     * @param length the number of bytes to hash (0 means until end of stream), must be >= 0
     * @return the hash as a hexadecimal string, never null
     * @throws IOException if an I/O error occurs while reading from the stream
     * @throws HashingException if a hashing error occurs
     * @throws IllegalArgumentException if parameters are invalid
     */
    String hashStreamRange(InputStream inputStream, long offset, long length)
            throws IOException, HashingException;

    /**
     * Hashes a specific portion of an InputStream with progress reporting support.
     * The stream will be consumed from the specified offset but not closed.
     *
     * @param inputStream the input stream to hash, must not be null
     * @param offset the starting offset in the stream (bytes to skip), must be >= 0
     * @param length the number of bytes to hash (0 means until end of stream), must be >= 0
     * @param progressListener optional listener for progress updates (can be null)
     * @return the hash as a hexadecimal string, never null
     * @throws IOException if an I/O error occurs while reading from the stream
     * @throws HashingException if a hashing error occurs
     * @throws IllegalArgumentException if parameters are invalid
     */
    String hashStreamRange(InputStream inputStream, long offset, long length,
                          HashProgressListener progressListener)
            throws IOException, HashingException;

    /**
     * Gets the hash algorithm name used by this hasher.
     *
     * @return the hash algorithm name (e.g., "BLAKE3", "SHA-256"), never null
     */
    String getAlgorithmName();

    /**
     * Gets the hash length in bytes for this hasher.
     *
     * @return the hash length in bytes, must be positive
     */
    int getHashLength();

    /**
     * Gets the buffer size used for streaming operations.
     * This can be useful for performance tuning and memory usage estimation.
     *
     * @return the buffer size in bytes, must be positive
     */
    int getBufferSize();

    /**
     * Gets the maximum allowed stream size for this hasher.
     * This helps prevent resource exhaustion attacks.
     *
     * @return the maximum stream size in bytes, must be positive
     */
    long getMaxStreamSize();

    /**
     * Checks if this hasher implementation is thread-safe.
     * Thread-safe implementations can be used concurrently by multiple threads
     * without external synchronization.
     *
     * @return true if the implementation is thread-safe, false otherwise
     */
    boolean isThreadSafe();

    /**
     * Gets the number of currently active hashing operations.
     * This can be useful for monitoring and resource management.
     *
     * @return the number of active operations, must be >= 0
     */
    long getActiveOperationsCount();

    /**
     * Interface for progress reporting during hashing operations.
     * Implementations should handle exceptions thrown by listener methods gracefully.
     */
    interface HashProgressListener {
        /**
         * Called periodically to report progress during hashing.
         *
         * @param bytesProcessed the number of bytes processed so far, must be >= 0
         */
        void onProgress(long bytesProcessed);

        /**
         * Called when hashing completes successfully.
         *
         * @param totalBytes the total number of bytes processed, must be >= 0
         * @param hash the computed hash, never null
         */
        void onComplete(long totalBytes, String hash);

        /**
         * Called when an error occurs during hashing.
         *
         * @param bytesProcessed the number of bytes processed before the error, must be >= 0
         * @param error the error that occurred, never null
         */
        void onError(long bytesProcessed, Throwable error);
    }
}