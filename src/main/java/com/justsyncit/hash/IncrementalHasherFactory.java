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

import java.io.Closeable;
import java.nio.ByteBuffer;
import java.util.Optional;

/**
 * Enhanced factory interface for creating incremental hashers with comprehensive
 * security, performance, and thread safety considerations.
 *
 * <p>This interface provides a contract for creating incremental hashers,
 * with clear requirements for thread safety, resource management, and error handling.</p>
 *
 * <p><strong>Thread Safety Requirements:</strong></p>
 * <ul>
 *   <li>Factory implementations MUST be thread-safe</li>
 *   <li>Created hasher instances MUST clearly document their thread safety guarantees</li>
 *   <li>Implementations SHOULD provide both thread-safe and non-thread-safe options</li>
 * </ul>
 *
 * <p><strong>Resource Management:</strong></p>
 * <ul>
 *   <li>Hasher instances that hold native resources MUST implement Closeable</li>
 *   <li>Close operations MUST be idempotent</li>
 *   <li>Resources SHOULD be released promptly when no longer needed</li>
 * </ul>
 *
 * <p><strong>Error Handling:</strong></p>
 * <ul>
 *   <li>IllegalArgumentException MUST be thrown for invalid input parameters</li>
 *   <li>IllegalStateException MUST be thrown when the hasher is in an invalid state</li>
 *   <li>HashingException SHOULD be thrown for hasher-specific errors</li>
 * </ul>
 *
 * <p><strong>Security Considerations:</strong></p>
 * <ul>
 *   <li>Sensitive data SHOULD be handled securely (e.g., zeroing buffers)</li>
 *   <li>Hash verification SHOULD use constant-time comparison</li>
 *   <li>Input validation MUST be performed to prevent injection attacks</li>
 * </ul>
 */
public interface IncrementalHasherFactory {

    /**
     * Creates a new incremental hasher for large files or streaming data.
     * The returned hasher instance should be used by a single thread unless
     * specifically documented as thread-safe.
     *
     * @return a new IncrementalHasher instance, never null
     * @throws HashingException if hasher creation fails
     */
    IncrementalHasher createIncrementalHasher() throws HashingException;

    /**
     * Creates a new thread-safe incremental hasher for concurrent use.
     * This method should return an instance that can be safely used by multiple
     * threads simultaneously without external synchronization.
     *
     * @return a new thread-safe IncrementalHasher instance, never null
     * @throws HashingException if thread-safe hasher creation fails
     * @throws UnsupportedOperationException if thread-safe hashing is not supported
     */
    default IncrementalHasher createThreadSafeIncrementalHasher() throws HashingException {
        throw new UnsupportedOperationException("Thread-safe hashing not supported by this factory");
    }

    /**
     * Gets the hash algorithm name used by hashers created by this factory.
     *
     * @return the hash algorithm name (e.g., "BLAKE3", "SHA-256"), never null
     */
    String getAlgorithmName();

    /**
     * Gets the hash length in bytes for hashers created by this factory.
     *
     * @return the hash length in bytes, must be positive
     */
    int getHashLength();

    /**
     * Enhanced interface for incremental hashing operations with comprehensive
     * security, performance, and resource management features.
     *
     * <p>Useful for hashing large files or streaming data without loading everything into memory.</p>
     *
     * <p><strong>Usage Pattern:</strong></p>
     * <pre>{@code
     * try (IncrementalHasher hasher = factory.createIncrementalHasher()) {
     *     hasher.update(dataChunk1);
     *     hasher.update(dataChunk2);
     *     String hash = hasher.digest();
     * }
     * }</pre>
     */
    interface IncrementalHasher extends Closeable {

        /**
         * Updates the hash with the provided data.
         *
         * @param data the data to add to the hash, must not be null
         * @throws IllegalArgumentException if the data is null
         * @throws IllegalStateException if the hasher has been finalized (digest called)
         * @throws HashingException if a hasher-specific error occurs
         */
        void update(byte[] data);

        /**
         * Updates the hash with the provided data slice.
         *
         * @param data the data array containing the slice, must not be null
         * @param offset the starting offset in the data array, must be >= 0 and < data.length
         * @param length the number of bytes to hash, must be >= 0 and offset + length <= data.length
         * @throws IllegalArgumentException if data is null or offset/length are invalid
         * @throws IllegalStateException if the hasher has been finalized (digest called)
         * @throws HashingException if a hasher-specific error occurs
         */
        void update(byte[] data, int offset, int length);

        /**
         * Updates the hash with data from a ByteBuffer.
         * This is a convenience method for working with NIO buffers.
         *
         * @param buffer the ByteBuffer containing data to hash, must not be null
         * @throws IllegalArgumentException if buffer is null
         * @throws IllegalStateException if the hasher has been finalized
         * @throws HashingException if a hasher-specific error occurs
         * @throws UnsupportedOperationException if the hasher doesn't support ByteBuffer operations
         */
        default void update(ByteBuffer buffer) throws HashingException {
            if (buffer == null) {
                throw new IllegalArgumentException("Buffer cannot be null");
            }

            if (buffer.hasArray()) {
                // Direct array access for better performance
                update(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining());
                buffer.position(buffer.limit());
            } else {
                // Fallback for direct buffers
                byte[] temp = new byte[Math.min(buffer.remaining(), 8192)];
                while (buffer.hasRemaining()) {
                    int bytesToRead = Math.min(temp.length, buffer.remaining());
                    buffer.get(temp, 0, bytesToRead);
                    update(temp, 0, bytesToRead);
                }
            }
        }

        /**
         * Finalizes the hash computation and returns the result.
         * After calling this method, the hasher cannot be used for further updates
         * unless reset() is called.
         *
         * @return the hash as a hexadecimal string, never null
         * @throws IllegalStateException if the hasher has already been finalized
         * @throws HashingException if hash finalization fails
         */
        String digest() throws HashingException;

        /**
         * Finalizes the hash computation and returns the result as a byte array.
         * After calling this method, the hasher cannot be used for further updates
         * unless reset() is called.
         *
         * @return the hash as a byte array, never null
         * @throws IllegalStateException if the hasher has already been finalized
         * @throws HashingException if hash finalization fails
         */
        default byte[] digestBytes() throws HashingException {
            String hexHash = digest();
            byte[] result = new byte[hexHash.length() / 2];
            for (int i = 0; i < result.length; i++) {
                int index = i * 2;
                result[i] = (byte) Integer.parseInt(hexHash.substring(index, index + 2), 16);
            }
            return result;
        }

        /**
         * Gets the current hash state without finalizing the computation.
         * This is useful for incremental hashing scenarios where intermediate
         * hash values are needed.
         *
         * @return the current hash state as a hexadecimal string, never null
         * @throws HashingException if intermediate hashing fails
         * @throws UnsupportedOperationException if the hasher doesn't support intermediate hashing
         */
        default String getIntermediateHash() throws HashingException {
            throw new UnsupportedOperationException("Intermediate hashing not supported by this implementation");
        }

        /**
         * Verifies if the computed hash matches an expected hash.
         * This method should perform constant-time comparison to prevent timing attacks.
         *
         * @param expectedHash the expected hash value as hexadecimal string, must not be null
         * @return true if the hashes match, false otherwise
         * @throws IllegalArgumentException if expectedHash is null or invalid length
         * @throws IllegalStateException if the hasher has not been finalized
         * @throws HashingException if verification fails due to hasher-specific issues
         */
        default boolean verify(String expectedHash) {
            if (expectedHash == null) {
                throw new IllegalArgumentException("Expected hash cannot be null");
            }

            String computedHash = digest();
            if (computedHash.length() != expectedHash.length()) {
                return false;
            }

            // Constant-time comparison to prevent timing attacks
            int result = 0;
            for (int i = 0; i < computedHash.length(); i++) {
                result |= computedHash.charAt(i) ^ expectedHash.charAt(i);
            }
            return result == 0;
        }

        /**
         * Verifies if the computed hash matches an expected hash.
         * This method should perform constant-time comparison to prevent timing attacks.
         *
         * @param expectedHash the expected hash value as byte array, must not be null
         * @return true if the hashes match, false otherwise
         * @throws IllegalArgumentException if expectedHash is null or invalid length
         * @throws IllegalStateException if the hasher has not been finalized
         * @throws HashingException if verification fails due to hasher-specific issues
         */
        default boolean verify(byte[] expectedHash) {
            if (expectedHash == null) {
                throw new IllegalArgumentException("Expected hash cannot be null");
            }

            byte[] computedHash = digestBytes();
            if (computedHash.length != expectedHash.length) {
                return false;
            }

            // Constant-time comparison to prevent timing attacks
            int result = 0;
            for (int i = 0; i < computedHash.length; i++) {
                result |= computedHash[i] ^ expectedHash[i];
            }
            return result == 0;
        }

        /**
         * Resets the hasher to its initial state, allowing reuse.
         * This method clears all internal state and allows the hasher to be used
         * for new hash computations.
         *
         * @throws HashingException if reset fails due to hasher-specific issues
         */
        void reset();

        /**
         * Gets the name of the hash algorithm used by this hasher.
         *
         * @return the algorithm name (e.g., "BLAKE3", "SHA-256"), never null
         */
        String getAlgorithmName();

        /**
         * Gets the hash length in bytes for this hasher.
         *
         * @return the hash length in bytes, must be positive
         */
        int getHashLength();

        /**
         * Gets the block size used by the algorithm in bytes.
         * This is important for performance optimization and padding calculations.
         *
         * @return the block size in bytes, empty if not applicable to the algorithm
         */
        default Optional<Integer> getBlockSize() {
            return Optional.empty();
        }

        /**
         * Gets the security level of the algorithm in bits.
         * This represents the resistance to collision attacks.
         *
         * @return the security level in bits, empty if not applicable
         */
        default Optional<Integer> getSecurityLevel() {
            return Optional.empty();
        }

        /**
         * Checks if this hasher implementation is thread-safe.
         * Thread-safe implementations can be used concurrently by multiple threads
         * without external synchronization.
         *
         * @return true if the implementation is thread-safe, false otherwise
         */
        boolean isThreadSafe();

        /**
         * Checks if the hasher has been finalized.
         *
         * @return true if finalized, false otherwise
         */
        boolean isFinalized();

        /**
         * Closes this hasher and releases any resources it may be holding.
         * Implementations should be idempotent - calling close multiple times should have no effect.
         * After closing, the hasher cannot be used for further operations.
         *
         * @throws HashingException if an error occurs while closing resources
         */
        @Override
        default void close() {
            // Default implementation does nothing - hashers without resources can skip this
        }
    }
}