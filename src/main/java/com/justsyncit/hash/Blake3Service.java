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
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Service interface for BLAKE3 cryptographic hash operations.
 * Provides high-performance hashing with SIMD support for file integrity verification.
 *
 * <p>Implementations of this interface should be thread-safe and reusable across multiple threads.
 * The service maintains no state between method calls, allowing for safe concurrent usage.</p>
 *
 * <p>All hash methods return a 64-character hexadecimal string representing the 256-bit BLAKE3 hash.</p>
 *
 * @since 1.0.0
 */
public interface Blake3Service {

    /**
     * Hashes the entire content of a file using BLAKE3 algorithm.
     * This method is optimized for file I/O and uses appropriate buffering strategies.
     *
     * @param filePath the path to the file to hash, must not be null
     * @return the BLAKE3 hash as a hexadecimal string (64 characters for 256-bit hash)
     * @throws IOException if an I/O error occurs while reading the file
     * @throws IllegalArgumentException if the file path is null, invalid, or file doesn't exist
     * @throws SecurityException if security manager denies access to the file
     */
    String hashFile(Path filePath) throws IOException, HashingException;

    /**
     * Hashes a byte array using BLAKE3 algorithm.
     * This method is optimized for in-memory operations with minimal overhead.
     *
     * @param data the byte array to hash, must not be null
     * @return the BLAKE3 hash as a hexadecimal string (64 characters for 256-bit hash)
     * @throws IllegalArgumentException if the data is null
     * @throws HashingException if hashing operation fails due to internal errors
     */
    String hashBuffer(byte[] data) throws HashingException;

    /**
     * Hashes a byte array slice using BLAKE3 algorithm with specified offset and length.
     * This method avoids array copying for better performance with large arrays.
     *
     * @param data the byte array containing the data to hash, must not be null
     * @param offset the starting offset in the data array, must be non-negative and less than data.length
     * @param length the number of bytes to hash, must be non-negative and offset + length <= data.length
     * @return the BLAKE3 hash as a hexadecimal string (64 characters for 256-bit hash)
     * @throws IllegalArgumentException if data is null or offset/length are invalid
     * @throws HashingException if hashing operation fails due to internal errors
     */
    String hashBuffer(byte[] data, int offset, int length) throws HashingException;

    /**
     * Hashes the content of a ByteBuffer using BLAKE3 algorithm.
     * This method works with both direct and heap buffers efficiently.
     *
     * @param buffer the ByteBuffer to hash, must not be null
     * @return the BLAKE3 hash as a hexadecimal string (64 characters for 256-bit hash)
     * @throws IllegalArgumentException if the buffer is null
     * @throws HashingException if hashing operation fails due to internal errors
     */
    String hashBuffer(ByteBuffer buffer) throws HashingException;

    /**
     * Hashes the content of an InputStream using BLAKE3 algorithm.
     * The stream will be fully consumed but not closed. Implementations should use
     * appropriate buffering for optimal performance.
     *
     * @param inputStream the input stream to hash, must not be null
     * @return the BLAKE3 hash as a hexadecimal string (64 characters for 256-bit hash)
     * @throws IOException if an I/O error occurs while reading from the stream
     * @throws IllegalArgumentException if the inputStream is null
     * @throws HashingException if hashing operation fails due to internal errors
     */
    String hashStream(InputStream inputStream) throws IOException, HashingException;

    /**
     * Creates a new incremental hasher for large files or streaming data.
     * Each call returns a new, independent hasher instance that maintains its own state.
     *
     * @return a new Blake3IncrementalHasher instance
     * @throws HashingException if hasher creation fails due to internal errors
     */
    Blake3IncrementalHasher createIncrementalHasher() throws HashingException;

    /**
     * Creates a new incremental hasher with a key for keyed hashing operations.
     * Keyed hashing is useful for message authentication codes (MAC) and similar applications.
     *
     * @param key the 32-byte key for keyed hashing, must be exactly 32 bytes
     * @return a new Blake3IncrementalHasher instance configured with the key
     * @throws IllegalArgumentException if the key is null or not exactly 32 bytes
     * @throws HashingException if hasher creation fails due to internal errors
     */
    Blake3IncrementalHasher createKeyedIncrementalHasher(byte[] key) throws HashingException;

    /**
     * Hashes multiple files in parallel for improved performance on multi-core systems.
     * This method is optimized for batch operations and uses concurrent processing.
     *
     * @param filePaths the list of file paths to hash, must not be null or contain null elements
     * @return a CompletableFuture that completes with a list of hashes in the same order as input
     * @throws IllegalArgumentException if filePaths is null or contains null elements
     */
    CompletableFuture<List<String>> hashFilesParallel(List<Path> filePaths);

    /**
     * Interface for incremental BLAKE3 hashing.
     * Useful for hashing large files or streaming data without loading everything into memory.
     *
     * <p>Instances of this interface are not thread-safe and should not be shared between threads.
     * Each instance maintains its own internal state for the hashing computation.</p>
     *
     * @since 1.0.0
     */
    interface Blake3IncrementalHasher {

        /**
         * Updates the hash with the provided data.
         * This method can be called multiple times to stream data to the hasher.
         *
         * @param data the data to add to the hash, must not be null
         * @throws IllegalArgumentException if the data is null
         * @throws IllegalStateException if the hasher has been finalized (digest() called)
         */
        void update(byte[] data);

        /**
         * Updates the hash with the provided data slice.
         * This method avoids array copying for better performance with large arrays.
         *
         * @param data the data array containing the slice, must not be null
         * @param offset the starting offset in the data array, must be non-negative and less than data.length
         * @param length the number of bytes to hash, must be non-negative and offset + length <= data.length
         * @throws IllegalArgumentException if data is null or offset/length are invalid
         * @throws IllegalStateException if the hasher has been finalized (digest() called)
         */
        void update(byte[] data, int offset, int length);

        /**
         * Updates the hash with data from a ByteBuffer.
         * This method efficiently handles both direct and heap buffers.
         *
         * @param buffer the ByteBuffer containing data to hash, must not be null
         * @throws IllegalArgumentException if the buffer is null
         * @throws IllegalStateException if the hasher has been finalized (digest() called)
         */
        void update(ByteBuffer buffer);

        /**
         * Finalizes the hash computation and returns the result.
         * After calling this method, the hasher cannot be used for further updates
         * unless reset() is called.
         *
         * @return the BLAKE3 hash as a hexadecimal string (64 characters for 256-bit hash)
         * @throws HashingException if hashing operation fails due to internal errors
         */
        String digest() throws HashingException;

        /**
         * Resets the hasher to its initial state, allowing reuse.
         * This method clears all internal state and allows the hasher to be used
         * for computing new hashes.
         */
        void reset();

        /**
         * Returns the current hash without finalizing the hasher.
         * This allows for intermediate hash values to be obtained for progress tracking
         * or verification purposes. The hasher can continue to be used after this call.
         *
         * @return the current BLAKE3 hash as a hexadecimal string (64 characters for 256-bit hash)
         * @throws HashingException if hashing operation fails due to internal errors
         */
        String peek() throws HashingException;

        /**
         * Returns the number of bytes that have been processed by this hasher.
         *
         * @return the total number of bytes processed so far
         */
        long getBytesProcessed();
    }

    /**
     * Gets information about the BLAKE3 implementation and SIMD support.
     *
     * @return Blake3Info containing implementation details
     */
    Blake3Info getInfo();

    /**
     * Verifies that the provided data matches the expected hash.
     * This is a convenience method that combines hashing and comparison.
     *
     * @param data the data to verify
     * @param expectedHash the expected hash as a hexadecimal string
     * @return true if the data hashes to the expected value, false otherwise
     * @throws IllegalArgumentException if data or expectedHash is null or invalid
     * @throws HashingException if hashing operation fails due to internal errors
     */
    boolean verify(byte[] data, String expectedHash) throws HashingException;

    /**
     * Information about the BLAKE3 implementation.
     * Provides details about the underlying implementation, performance characteristics,
     * and available optimizations.
     *
     * @since 1.0.0
     */
    interface Blake3Info {
        /**
         * @return the BLAKE3 version being used
         */
        String getVersion();

        /**
         * @return true if SIMD optimizations are available and enabled
         */
        boolean hasSimdSupport();

        /**
         * @return the SIMD instruction set being used (e.g., "AVX2", "AVX-512", "SSE4.1", "none")
         */
        String getSimdInstructionSet();

        /**
         * @return true if the implementation uses JNI bindings
         */
        boolean isJniImplementation();

        /**
         * @return the optimal buffer size for hashing operations in bytes
         */
        int getOptimalBufferSize();

        /**
         * @return true if the implementation supports concurrent hashing operations
         */
        boolean supportsConcurrentHashing();

        /**
         * @return the maximum number of threads that can be used effectively for parallel operations
         */
        int getMaxConcurrentThreads();
    }
}