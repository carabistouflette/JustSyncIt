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
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;

/**
 * SHA-256 hash algorithm implementation.
 * This class implements the HashAlgorithm interface using Java's built-in SHA-256.
 * Note: This is currently used as a fallback until a true BLAKE3 implementation is available.
 *
 * <p><strong>Thread Safety:</strong></p>
 * This implementation is thread-safe and uses synchronization on the MessageDigest instance
 * to ensure thread-safe operations. Multiple threads can safely use the same instance.
 *
 * <p><strong>Resource Management:</strong></p>
 * This implementation properly manages resources and implements Closeable for cleanup.
 * The close() method is idempotent and can be called multiple times safely.
 */
public final class Sha256HashAlgorithm implements HashAlgorithm {

    /** MessageDigest instance for SHA-256. */
    private final MessageDigest digest;

    /** Flag to track if the digest has been finalized. */
    private boolean finalized = false;

    /** Flag to track if the instance has been closed. */
    private boolean closed = false;

    /** SHA-256 block size in bytes (64 bytes = 512 bits). */
    private static final int BLOCK_SIZE = 64;

    /** SHA-256 security level in bits (resistance to collision attacks). */
    private static final int SECURITY_LEVEL = 128;

    /**
     * Creates a new SHA-256 hash algorithm instance.
     * @throws HashingException if SHA-256 algorithm is not available
     */
    private Sha256HashAlgorithm() throws HashingException {
        try {
            this.digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new HashingException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Creates a new SHA-256 hash algorithm instance.
     * @return a new Sha256HashAlgorithm instance
     * @throws HashingException if SHA-256 algorithm is not available
     */
    public static Sha256HashAlgorithm create() throws HashingException {
        return new Sha256HashAlgorithm();
    }

    @Override
    public void update(byte[] data) {
        checkState();
        if (data == null) {
            throw new IllegalArgumentException("Data cannot be null");
        }
        // Empty arrays are valid input for hash algorithms
        synchronized (digest) {
            digest.update(data);
        }
    }

    @Override
    public void update(byte[] data, int offset, int length) {
        checkState();
        if (data == null) {
            throw new IllegalArgumentException("Data cannot be null");
        }
        if (offset < 0 || length < 0 || offset + length > data.length) {
            throw new IllegalArgumentException("Invalid offset or length: offset=" + offset +
                                              ", length=" + length + ", array length=" + data.length);
        }
        synchronized (digest) {
            digest.update(data, offset, length);
        }
    }

    @Override
    public byte[] digest() {
        checkState();
        synchronized (digest) {
            byte[] result = digest.digest();
            finalized = true;
            return result;
        }
    }

    @Override
    public void reset() {
        checkState();
        synchronized (digest) {
            digest.reset();
            finalized = false;
        }
    }

    @Override
    public String getAlgorithmName() {
        return "SHA-256";
    }

    @Override
    public int getHashLength() {
        return 32; // SHA-256 produces 256-bit hash = 32 bytes
    }

    @Override
    public Optional<Integer> getBlockSize() {
        return Optional.of(BLOCK_SIZE);
    }

    @Override
    public Optional<Integer> getSecurityLevel() {
        return Optional.of(SECURITY_LEVEL);
    }

    @Override
    public boolean isThreadSafe() {
        return true; // This implementation uses synchronization on the digest object
    }

    @Override
    public Sha256HashAlgorithm createInstance() throws HashingException {
        return create(); // Use the existing factory method
    }

    @Override
    public byte[] updateAndIntermediate(byte[] data) throws HashingException {
        checkState();
        if (data == null) {
            throw new IllegalArgumentException("Data cannot be null");
        }

        synchronized (digest) {
            // Create a clone of the current digest state
            MessageDigest clone;
            try {
                clone = (MessageDigest) digest.clone();
            } catch (CloneNotSupportedException e) {
                throw new HashingException("Failed to clone digest for intermediate hashing", e);
            }

            // Update the original digest
            digest.update(data);

            // Return the intermediate hash from the clone
            return clone.digest();
        }
    }

    @Override
    public void update(ByteBuffer buffer) throws HashingException {
        checkState();
        if (buffer == null) {
            throw new IllegalArgumentException("Buffer cannot be null");
        }

        synchronized (digest) {
            if (buffer.hasArray()) {
                // Direct array access for better performance
                int pos = buffer.position();
                int limit = buffer.limit();
                byte[] array = buffer.array();
                int offset = buffer.arrayOffset() + pos;
                int length = limit - pos;
                digest.update(array, offset, length);
                buffer.position(limit);
            } else {
                // Fallback for direct buffers
                byte[] temp = new byte[Math.min(buffer.remaining(), 8192)]; // Use reasonable chunk size
                while (buffer.hasRemaining()) {
                    int chunkSize = Math.min(buffer.remaining(), temp.length);
                    buffer.get(temp, 0, chunkSize);
                    digest.update(temp, 0, chunkSize);
                }
            }
        }
    }

    @Override
    public boolean verify(byte[] expectedHash) {
        checkState();
        if (expectedHash == null) {
            throw new IllegalArgumentException("Expected hash cannot be null");
        }

        if (expectedHash.length != getHashLength()) {
            throw new IllegalArgumentException("Expected hash length " + expectedHash.length +
                                             " does not match algorithm hash length " + getHashLength());
        }

        byte[] computedHash;
        synchronized (digest) {
            if (!finalized) {
                // Create a clone to compute the digest without affecting the current state
                try {
                    MessageDigest clone = (MessageDigest) digest.clone();
                    computedHash = clone.digest();
                } catch (CloneNotSupportedException e) {
                    throw new HashingException("Failed to clone digest for verification", e);
                }
            } else {
                computedHash = digest.digest(); // Already finalized, can reuse
            }
        }

        // Constant-time comparison to prevent timing attacks
        int result = 0;
        for (int i = 0; i < computedHash.length; i++) {
            result |= computedHash[i] ^ expectedHash[i];
        }
        return result == 0;
    }

    @Override
    public void close() throws IOException {
        // Clear sensitive data from memory
        if (!closed) {
            synchronized (digest) {
                try {
                    // Reset the digest to clear any internal state
                    digest.reset();
                } finally {
                    closed = true;
                }
            }
        }
    }

    /**
     * Checks if the algorithm is in a valid state for operations.
     * @throws IllegalStateException if the algorithm has been closed
     */
    private void checkState() {
        if (closed) {
            throw new IllegalStateException("HashAlgorithm has been closed and cannot be used");
        }
    }
}