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
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Enhanced interface for file hashing operations with comprehensive error handling,
 * thread safety guarantees, and performance optimization features.
 *
 * <p>Implementations of this interface must be thread-safe and should handle resource
 * management properly. The interface provides both synchronous and asynchronous methods
 * for file hashing, along with progress reporting and cancellation capabilities.</p>
 *
 * <p>All implementations should validate file paths to prevent security vulnerabilities
 * and should enforce reasonable resource limits to prevent resource exhaustion attacks.</p>
 *
 * <p>This interface follows the Interface Segregation Principle by providing focused
 * methods for different use cases while maintaining a cohesive API.</p>
 */
public interface FileHasher {

    /**
     * Hashes the entire content of a file synchronously.
     *
     * @param filePath the path to the file to hash, must not be null
     * @return the hash as a hexadecimal string, never null
     * @throws IOException if an I/O error occurs while reading the file
     * @throws IllegalArgumentException if the file path is invalid or file doesn't exist
     * @throws SecurityException if the file path violates security constraints
     * @throws HashingException if hashing fails due to algorithm-specific issues
     */
    String hashFile(Path filePath) throws IOException, IllegalArgumentException, SecurityException, HashingException;

    /**
     * Hashes the entire content of a file asynchronously.
     *
     * @param filePath the path to the file to hash, must not be null
     * @return a CompletableFuture that completes with the hash as a hexadecimal string
     * @throws IllegalArgumentException if the file path is invalid
     * @throws SecurityException if the file path violates security constraints
     */
    CompletableFuture<String> hashFileAsync(Path filePath) throws IllegalArgumentException, SecurityException;

    /**
     * Hashes a specific portion of a file.
     *
     * @param filePath the path to the file to hash, must not be null
     * @param offset the starting offset in bytes, must be non-negative
     * @param length the number of bytes to hash, must be positive
     * @return the hash as a hexadecimal string, never null
     * @throws IOException if an I/O error occurs while reading the file
     * @throws IllegalArgumentException if any parameter is invalid
     * @throws SecurityException if the file path violates security constraints
     * @throws HashingException if hashing fails due to algorithm-specific issues
     */
    String hashFileRange(Path filePath, long offset, long length)
            throws IOException, IllegalArgumentException, SecurityException, HashingException;

    /**
     * Hashes a file with progress reporting.
     *
     * @param filePath the path to the file to hash, must not be null
     * @param progressCallback consumer that receives progress updates (0.0 to 1.0), may be null
     * @return the hash as a hexadecimal string, never null
     * @throws IOException if an I/O error occurs while reading the file
     * @throws IllegalArgumentException if the file path is invalid
     * @throws SecurityException if the file path violates security constraints
     * @throws HashingException if hashing fails due to algorithm-specific issues
     */
    String hashFileWithProgress(Path filePath, Consumer<Double> progressCallback)
            throws IOException, IllegalArgumentException, SecurityException, HashingException;

    /**
     * Validates a file against an expected hash.
     *
     * @param filePath the path to the file to validate, must not be null
     * @param expectedHash the expected hash value as a hexadecimal string, must not be null
     * @return true if the file hash matches the expected hash, false otherwise
     * @throws IOException if an I/O error occurs while reading the file
     * @throws IllegalArgumentException if any parameter is invalid
     * @throws SecurityException if the file path violates security constraints
     * @throws HashingException if hashing fails due to algorithm-specific issues
     */
    boolean validateFileHash(Path filePath, String expectedHash)
            throws IOException, IllegalArgumentException, SecurityException, HashingException;

    /**
     * Gets the hash algorithm used by this hasher.
     *
     * @return the hash algorithm, never null
     */
    HashAlgorithm getHashAlgorithm();

    /**
     * Gets the maximum file size that this hasher can process.
     *
     * @return the maximum file size in bytes, or empty if no limit is set
     */
    Optional<Long> getMaxFileSize();

    /**
     * Gets the buffer size used for streaming operations.
     *
     * @return the buffer size in bytes
     */
    int getBufferSize();

    /**
     * Checks if this hasher supports cancellation of long-running operations.
     *
     * @return true if cancellation is supported, false otherwise
     */
    boolean supportsCancellation();

    /**
     * Creates a new hashing context that can be used for incremental hashing.
     * This is useful for hashing large files in chunks or for streaming scenarios.
     *
     * @return a new hashing context, never null
     */
    HashingContext createHashingContext();

    /**
     * Closes this hasher and releases any resources it may be holding.
     * Implementations should be idempotent - calling close multiple times should have no effect.
     *
     * @throws IOException if an I/O error occurs while closing resources
     */
    void close() throws IOException;

    /**
     * Interface for incremental hashing contexts.
     * Implementations must be thread-safe if they are to be used by multiple threads.
     */
    interface HashingContext extends AutoCloseable {

        /**
         * Updates the hash with the provided data.
         *
         * @param data the data to add to the hash, must not be null
         * @throws IllegalArgumentException if data is null
         * @throws HashingException if hashing fails due to algorithm-specific issues
         */
        void update(byte[] data) throws IllegalArgumentException, HashingException;

        /**
         * Updates the hash with the provided data slice.
         *
         * @param data the data array containing the slice, must not be null
         * @param offset the starting offset in the data array, must be valid
         * @param length the number of bytes to hash, must be valid
         * @throws IllegalArgumentException if any parameter is invalid
         * @throws HashingException if hashing fails due to algorithm-specific issues
         */
        void update(byte[] data, int offset, int length) throws IllegalArgumentException, HashingException;

        /**
         * Finalizes the hash computation and returns the result.
         * After calling this method, the context cannot be used for further updates.
         *
         * @return the hash as a hexadecimal string, never null
         * @throws HashingException if hashing fails due to algorithm-specific issues
         */
        String digest() throws HashingException;

        /**
         * Resets the context to its initial state, allowing reuse.
         *
         * @throws HashingException if reset fails due to algorithm-specific issues
         */
        void reset() throws HashingException;

        /**
         * Gets the hash algorithm used by this context.
         *
         * @return the hash algorithm, never null
         */
        HashAlgorithm getHashAlgorithm();

        /**
         * Closes this context and releases any resources it may be holding.
         * Implementations should be idempotent.
         *
         * @throws IOException if an I/O error occurs while closing resources
         */
        @Override
        void close() throws IOException;
    }
}