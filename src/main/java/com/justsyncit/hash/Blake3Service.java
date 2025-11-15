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
import java.nio.file.Path;

/**
 * Service interface for BLAKE3 cryptographic hash operations.
 * Provides high-performance hashing with SIMD support for file integrity verification.
 */
public interface Blake3Service {

    /**
     * Hashes the entire content of a file using BLAKE3 algorithm.
     *
     * @param filePath the path to the file to hash
     * @return the BLAKE3 hash as a hexadecimal string (64 characters for 256-bit hash)
     * @throws IOException if an I/O error occurs while reading the file
     * @throws IllegalArgumentException if the file path is invalid or file doesn't exist
     */
    String hashFile(Path filePath) throws IOException;

    /**
     * Hashes a byte array using BLAKE3 algorithm.
     *
     * @param data the byte array to hash
     * @return the BLAKE3 hash as a hexadecimal string (64 characters for 256-bit hash)
     * @throws IllegalArgumentException if the data is null
     */
    String hashBuffer(byte[] data) throws HashingException;

    /**
     * Hashes the content of an InputStream using BLAKE3 algorithm.
     * The stream will be fully consumed but not closed.
     *
     * @param inputStream the input stream to hash
     * @return the BLAKE3 hash as a hexadecimal string (64 characters for 256-bit hash)
     * @throws IOException if an I/O error occurs while reading from the stream
     * @throws IllegalArgumentException if the inputStream is null
     */
    String hashStream(InputStream inputStream) throws IOException, HashingException;

    /**
     * Creates a new incremental hasher for large files or streaming data.
     *
     * @return a new Blake3IncrementalHasher instance
     */
    Blake3IncrementalHasher createIncrementalHasher() throws HashingException;

    /**
     * Interface for incremental BLAKE3 hashing.
     * Useful for hashing large files or streaming data without loading everything into memory.
     */
    interface Blake3IncrementalHasher {

        /**
         * Updates the hash with the provided data.
         *
         * @param data the data to add to the hash
         * @throws IllegalArgumentException if the data is null
         */
        void update(byte[] data);

        /**
         * Updates the hash with the provided data slice.
         *
         * @param data the data array containing the slice
         * @param offset the starting offset in the data array
         * @param length the number of bytes to hash
         * @throws IllegalArgumentException if data is null or offset/length are invalid
         */
        void update(byte[] data, int offset, int length);

        /**
         * Finalizes the hash computation and returns the result.
         * After calling this method, the hasher cannot be used for further updates.
         *
         * @return the BLAKE3 hash as a hexadecimal string (64 characters for 256-bit hash)
         */
        String digest() throws HashingException;

        /**
         * Resets the hasher to its initial state, allowing reuse.
         */
        void reset();
    }

    /**
     * Gets information about the BLAKE3 implementation and SIMD support.
     *
     * @return Blake3Info containing implementation details
     */
    Blake3Info getInfo();

    /**
     * Information about the BLAKE3 implementation.
     */
    interface Blake3Info {
        /**
         * @return the BLAKE3 version being used
         */
        String getVersion();

        /**
         * @return true if SIMD optimizations are available
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
    }
}