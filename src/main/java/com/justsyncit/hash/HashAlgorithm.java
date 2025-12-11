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
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Enhanced interface for hash algorithm implementations with comprehensive
 * security, performance, and thread safety considerations.
 *
 * <p>This interface provides a contract for different hash algorithms to follow,
 * with clear requirements for thread safety, resource management, and error handling.</p>
 *
 * <p><strong>Thread Safety Requirements:</strong></p>
 * <ul>
 *   <li>Implementations MUST clearly document their thread safety guarantees</li>
 *   <li>If not thread-safe, implementations MUST provide a createInstance() method for concurrent use</li>
 *   <li>Thread-safe implementations MUST use appropriate synchronization mechanisms</li>
 * </ul>
 *
 * <p><strong>Resource Management:</strong></p>
 * <ul>
 *   <li>Implementations that hold native resources MUST implement Closeable</li>
 *   <li>Close operations MUST be idempotent</li>
 *   <li>Resources SHOULD be released promptly when no longer needed</li>
 * </ul>
 *
 * <p><strong>Error Handling:</strong></p>
 * <ul>
 *   <li>IllegalArgumentException MUST be thrown for invalid input parameters</li>
 *   <li>IllegalStateException MUST be thrown when the algorithm is in an invalid state</li>
 *   <li>HashingException SHOULD be thrown for algorithm-specific errors</li>
 * </ul>
 *
 * <p><strong>Security Considerations:</strong></p>
 * <ul>
 *   <li>Sensitive data SHOULD be handled securely (e.g., zeroing buffers)</li>
 *   <li>Implementations SHOULD be resistant to timing attacks where applicable</li>
 *   <li>Input validation MUST be performed to prevent injection attacks</li>
 * </ul>
 */
public interface HashAlgorithm extends Closeable {

    /**
     * Updates the hash with the provided data.
     *
     * @param data the data to add to the hash, must not be null
     * @throws IllegalArgumentException if the data is null
     * @throws IllegalStateException if the algorithm has been finalized (digest called)
     * @throws HashingException if an algorithm-specific error occurs
     */
    void update(byte[] data);

    /**
     * Updates the hash with the provided data slice.
     *
     * @param data the data array containing the slice, must not be null
     * @param offset the starting offset in the data array, must be >= 0 and < data.length
     * @param length the number of bytes to hash, must be >= 0 and offset + length <= data.length
     * @throws IllegalArgumentException if data is null or offset/length are invalid
     * @throws IllegalStateException if the algorithm has been finalized (digest called)
     * @throws HashingException if an algorithm-specific error occurs
     */
    void update(byte[] data, int offset, int length);

    /**
     * Finalizes the hash computation and returns the result.
     * After calling this method, the algorithm cannot be used for further updates
     * unless reset() is called.
     *
     * @return the hash as a byte array, never null
     * @throws IllegalStateException if the algorithm has already been finalized
     * @throws HashingException if an algorithm-specific error occurs
     */
    byte[] digest();

    /**
     * Resets the algorithm to its initial state, allowing reuse.
     * This method clears all internal state and allows the algorithm to be used
     * for new hash computations.
     *
     * @throws HashingException if reset fails due to algorithm-specific issues
     */
    void reset();

    /**
     * Gets the name of the hash algorithm.
     *
     * @return the algorithm name (e.g., "BLAKE3", "SHA-256"), never null
     */
    String getAlgorithmName();

    /**
     * Gets the hash length in bytes.
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
     * Checks if this implementation is thread-safe.
     * Thread-safe implementations can be used concurrently by multiple threads
     * without external synchronization.
     *
     * @return true if the implementation is thread-safe, false otherwise
     */
    boolean isThreadSafe();

    /**
     * Creates a new instance of this algorithm with the same configuration.
     * This method is required for non-thread-safe implementations to enable
     * concurrent use by creating separate instances for each thread.
     *
     * @return a new instance of the hash algorithm, never null
     * @throws HashingException if instance creation fails
     * @throws UnsupportedOperationException if instance creation is not supported
     */
    default HashAlgorithm createInstance() throws HashingException {
        throw new UnsupportedOperationException("Instance creation not supported by this implementation");
    }

    /**
     * Verifies if the computed hash matches an expected hash.
     * This method should perform constant-time comparison to prevent timing attacks.
     *
     * @param expectedHash the expected hash value, must not be null
     * @return true if the hashes match, false otherwise
     * @throws IllegalArgumentException if expectedHash is null or invalid length
     * @throws IllegalStateException if the algorithm has not been finalized
     * @throws HashingException if verification fails due to algorithm-specific issues
     */
    default boolean verify(byte[] expectedHash) {
        if (expectedHash == null) {
            throw new IllegalArgumentException("Expected hash cannot be null");
        }
        
        byte[] computedHash = digest();
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
     * Gets the hash as a hexadecimal string.
     * This is a convenience method that combines digest() with hex encoding.
     *
     * @return the hash as a hexadecimal string, never null
     * @throws IllegalStateException if the algorithm has already been finalized
     * @throws HashingException if an algorithm-specific error occurs
     */
    default String digestHex() {
        byte[] hash = digest();
        return HexFormat.of().formatHex(hash);
    }

    /**
     * Updates the hash with the provided data and returns the current hash state
     * without finalizing the computation. This is useful for incremental hashing
     * scenarios where intermediate hash values are needed.
     *
     * @param data the data to add to the hash, must not be null
     * @return the current hash state as a byte array, never null
     * @throws IllegalArgumentException if the data is null
     * @throws IllegalStateException if the algorithm has been finalized
     * @throws HashingException if an algorithm-specific error occurs
     * @throws UnsupportedOperationException if the algorithm doesn't support intermediate hashing
     */
    default byte[] updateAndIntermediate(byte[] data) throws HashingException {
        throw new UnsupportedOperationException("Intermediate hashing not supported by this implementation");
    }

    /**
     * Updates the hash with data from a ByteBuffer.
     * This is a convenience method for working with NIO buffers.
     *
     * @param buffer the ByteBuffer containing data to hash, must not be null
     * @throws IllegalArgumentException if buffer is null
     * @throws IllegalStateException if the algorithm has been finalized
     * @throws HashingException if an algorithm-specific error occurs
     */
    default void update(ByteBuffer buffer) throws HashingException {
        if (buffer == null) {
            throw new IllegalArgumentException("Buffer cannot be null");
        }
        
        if (buffer.hasArray()) {
            // Direct array access for better performance
            update(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining());
        } else {
            // Fallback for direct buffers
            byte[] temp = new byte[buffer.remaining()];
            buffer.get(temp);
            update(temp);
        }
    }

    /**
     * Closes this algorithm and releases any resources it may be holding.
     * Implementations should be idempotent - calling close multiple times should have no effect.
     * After closing, the algorithm cannot be used for further operations.
     *
     * @throws IOException if an error occurs while closing resources
     */
    @Override
    default void close() throws IOException {
        // Default implementation does nothing - algorithms without resources can skip this
    }
}