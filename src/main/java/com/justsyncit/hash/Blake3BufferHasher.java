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

/**
 * Buffer hashing implementation using BLAKE3 algorithm.
 * Follows Single Responsibility Principle by focusing only on buffer operations.
 */
public class Blake3BufferHasher implements BufferHasher {

    /** Logger for the buffer hasher. */
    private static final Logger logger = LoggerFactory.getLogger(Blake3BufferHasher.class);

    /** Hex format for hash string representation. */
    private static final HexFormat HEX_FORMAT = HexFormat.of();

    /** Hash algorithm instance. */
    private final HashAlgorithm hashAlgorithm;

    /**
     * Creates a new Blake3BufferHasher with the provided hash algorithm.
     *
     * @param hashAlgorithm the hash algorithm to use
     */
    public Blake3BufferHasher(HashAlgorithm hashAlgorithm) {
        this.hashAlgorithm = hashAlgorithm;
    }

    @Override
    public String hashBuffer(byte[] data) throws HashingException {
        if (data == null) {
            throw new IllegalArgumentException("Data cannot be null");
        }

        logger.trace("Hashing buffer of {} bytes", data.length);

        try {
            hashAlgorithm.update(data);
            byte[] hash = hashAlgorithm.digest();
            String result = HEX_FORMAT.formatHex(hash);
            logger.trace("Generated hash: {}", result);
            hashAlgorithm.reset(); // Reset for next use
            return result;
        } catch (Exception e) {
            logger.error("Error hashing buffer", e);
            throw new HashingException("Failed to hash buffer", e);
        }
    }
}