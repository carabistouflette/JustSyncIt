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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HexFormat;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Thread-safe buffer hashing implementation using BLAKE3 algorithm.
 * Follows Single Responsibility Principle by focusing only on buffer operations.
 * This implementation ensures thread safety through synchronization and provides
 * additional validation and error handling.
 */
public class Blake3BufferHasher implements BufferHasher {

    /** Logger for the buffer hasher. */
    private static final Logger logger = LoggerFactory.getLogger(Blake3BufferHasher.class);

    /** Hex format for hash string representation. */
    private static final HexFormat HEX_FORMAT = HexFormat.of();

    /** Maximum buffer size to prevent resource exhaustion attacks (100MB). */
    private static final int MAX_BUFFER_SIZE = 100 * 1024 * 1024;

    /** Hash algorithm instance. */
    private final HashAlgorithm hashAlgorithm;

    /** Lock for thread-safe operations on the hash algorithm. */
    private final ReentrantLock hashLock = new ReentrantLock();

    /**
     * Creates a new Blake3BufferHasher with the provided hash algorithm.
     *
     * @param hashAlgorithm the hash algorithm to use
     * @throws IllegalArgumentException if hashAlgorithm is null
     */
    public Blake3BufferHasher(HashAlgorithm hashAlgorithm) {
        if (hashAlgorithm == null) {
            throw new IllegalArgumentException("Hash algorithm cannot be null");
        }
        this.hashAlgorithm = hashAlgorithm;
    }

    @Override
    public String hashBuffer(byte[] data) throws HashingException {
        if (data == null) {
            throw new HashingException("Data cannot be null");
        }
        return hashBuffer(data, 0, data.length);
    }

    @Override
    public String hashBuffer(byte[] data, int offset, int length) throws HashingException {
        // Input validation
        if (data == null) {
            throw new IllegalArgumentException("Data cannot be null");
        }
        // Allow offset=0 for empty arrays, otherwise validate offset is within bounds
        if (data.length > 0 && (offset < 0 || offset >= data.length)) {
            throw new IllegalArgumentException("Offset must be between 0 and data.length-1");
        } else if (data.length == 0 && offset != 0) {
            throw new IllegalArgumentException("Offset must be 0 for empty arrays");
        }
        if (length < 0 || offset + length > data.length) {
            throw new IllegalArgumentException("Length must be valid for the given offset and data size");
        }
        // Allow zero-length hashing (empty files/buffers)
        if (length == 0) {
            // Return hash of empty data
            hashLock.lock();
            try {
                hashAlgorithm.reset();
                byte[] hash = hashAlgorithm.digest();
                return HEX_FORMAT.formatHex(hash);
            } catch (Exception e) {
                logger.error("Error hashing empty buffer", e);
                throw new HashingException("Failed to hash empty buffer", e);
            } finally {
                hashLock.unlock();
            }
        }
        if (length > MAX_BUFFER_SIZE) {
            throw new IllegalArgumentException("Buffer size exceeds maximum allowed size of " + MAX_BUFFER_SIZE + " bytes");
        }

        logger.trace("Hashing buffer of {} bytes (offset: {}, length: {})", data.length, offset, length);

        hashLock.lock();
        try {
            // Reset algorithm to ensure clean state
            hashAlgorithm.reset();
            
            // Update with data
            if (length == data.length && offset == 0) {
                // Full array optimization
                hashAlgorithm.update(data);
            } else {
                // Partial array hashing
                hashAlgorithm.update(data, offset, length);
            }
            
            // Generate hash
            byte[] hash = hashAlgorithm.digest();
            String result = HEX_FORMAT.formatHex(hash);
            
            logger.trace("Generated hash: {}", result);
            return result;
        } catch (IllegalStateException e) {
            logger.error("Hash algorithm in invalid state", e);
            throw new HashingException("Hash algorithm in invalid state", e);
        } catch (OutOfMemoryError e) {
            logger.error("Out of memory during hashing operation", e);
            throw new HashingException("Insufficient memory for hashing operation", e);
        } catch (Exception e) {
            logger.error("Error hashing buffer", e);
            throw new HashingException("Failed to hash buffer", e);
        } finally {
            hashLock.unlock();
        }
    }

    @Override
    public String getAlgorithmName() {
        return hashAlgorithm.getAlgorithmName();
    }

    @Override
    public int getHashLength() {
        return hashAlgorithm.getHashLength();
    }

    @Override
    public boolean isThreadSafe() {
        return true; // This implementation uses ReentrantLock for thread safety
    }

    @Override
    public boolean supportsPartialHashing() {
        return true; // This implementation supports partial hashing
    }
}