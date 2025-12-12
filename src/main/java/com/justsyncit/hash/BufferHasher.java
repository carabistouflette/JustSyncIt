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

import java.nio.ByteBuffer;

/**
 * Interface for buffer hashing operations.
 * Follows Interface Segregation Principle by focusing only on buffer operations.
 *
 * <p>Implementations of this interface should be thread-safe unless otherwise documented.
 * The interface provides both simple and advanced hashing methods to accommodate
 * different use cases, from small buffers to large data streams.</p>
 *
 * <p><strong>Security Note:</strong> Implementations should use cryptographically secure
 * hash algorithms for security-sensitive applications. The interface itself does not
 * guarantee cryptographic properties - consult the specific implementation documentation.</p>
 */
public interface BufferHasher {

    /**
     * Hashes a byte array.
     *
     * @param data the byte array to hash
     * @return the hash as a hexadecimal string
     * @throws HashingException if hashing fails or data is null
     */
    String hashBuffer(byte[] data) throws HashingException;

    /**
     * Hashes a portion of a byte array using the specified offset and length.
     * This method provides more granular control over which bytes to hash.
     *
     * @param data the byte array to hash
     * @param offset the starting offset in the data array
     * @param length the number of bytes to hash
     * @return the hash as a hexadecimal string
     * @throws IllegalArgumentException if parameters are invalid (null data, invalid offset/length)
     * @throws HashingException if hashing fails
     */
    String hashBuffer(byte[] data, int offset, int length) throws HashingException;

    /**
     * Hashes the contents of a ByteBuffer. This method is more efficient for
     * large buffers as it can avoid copying data.
     *
     * @param buffer the ByteBuffer containing data to hash
     * @return the hash as a hexadecimal string
     * @throws IllegalArgumentException if buffer is null
     * @throws HashingException if hashing fails
     */
    default String hashBuffer(ByteBuffer buffer) throws HashingException {
        if (buffer == null) {
            throw new IllegalArgumentException("Buffer cannot be null");
        }

        if (buffer.hasArray()) {
            // Optimize for direct array access
            return hashBuffer(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining());
        } else {
            // Fallback for direct buffers
            byte[] data = new byte[buffer.remaining()];
            buffer.mark();
            buffer.get(data);
            buffer.reset();
            return hashBuffer(data);
        }
    }

    /**
     * Hashes multiple buffers and returns an array of hashes in the same order.
     * This method may be more efficient than hashing each buffer individually.
     *
     * @param buffers array of byte arrays to hash
     * @return array of hash strings, one for each input buffer
     * @throws IllegalArgumentException if buffers array or any element is null
     * @throws HashingException if hashing fails
     */
    default String[] hashBuffers(byte[][] buffers) throws HashingException {
        if (buffers == null) {
            throw new IllegalArgumentException("Buffers array cannot be null");
        }

        String[] results = new String[buffers.length];
        for (int i = 0; i < buffers.length; i++) {
            results[i] = hashBuffer(buffers[i]);
        }
        return results;
    }

    /**
     * Gets the name of the hash algorithm being used by this hasher.
     *
     * @return the algorithm name (e.g., "BLAKE3", "SHA-256")
     */
    String getAlgorithmName();

    /**
     * Gets the length of the hash produced by this algorithm in bytes.
     *
     * @return the hash length in bytes
     */
    int getHashLength();

    /**
     * Gets the length of the hash produced by this algorithm in hexadecimal characters.
     *
     * @return the hash length in hexadecimal characters (typically 2x the byte length)
     */
    default int getHashHexLength() {
        return getHashLength() * 2;
    }

    /**
     * Indicates whether this implementation is thread-safe.
     * The default implementation assumes thread-safety, but specific implementations
     * may override this if they are not thread-safe.
     *
     * @return true if the implementation is thread-safe, false otherwise
     */
    default boolean isThreadSafe() {
        return true;
    }

    /**
     * Indicates whether this hasher supports partial buffer hashing.
     *
     * @return true if partial hashing is supported, false otherwise
     */
    default boolean supportsPartialHashing() {
        return true;
    }
}