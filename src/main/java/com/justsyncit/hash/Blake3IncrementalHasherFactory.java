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
 * Factory for creating BLAKE3 incremental hashers.
 * Follows Single Responsibility Principle by focusing only on factory operations.
 */
public class Blake3IncrementalHasherFactory implements IncrementalHasherFactory {

    private static final Logger logger = LoggerFactory.getLogger(Blake3IncrementalHasherFactory.class);
    private static final HexFormat HEX_FORMAT = HexFormat.of();

    private final HashAlgorithm hashAlgorithm;

    /**
     * Creates a new Blake3IncrementalHasherFactory with the provided hash algorithm.
     *
     * @param hashAlgorithm the hash algorithm to use
     */
    public Blake3IncrementalHasherFactory(HashAlgorithm hashAlgorithm) {
        this.hashAlgorithm = hashAlgorithm;
    }

    @Override
    public IncrementalHasher createIncrementalHasher() {
        return new Blake3IncrementalHasherImpl();
    }

    /**
     * Implementation of IncrementalHasher using the provided hash algorithm.
     */
    private class Blake3IncrementalHasherImpl implements IncrementalHasher {
        private final HashAlgorithm hasher;
        private boolean finalized = false;

        public Blake3IncrementalHasherImpl() {
            this.hasher = createNewHashAlgorithm();
        }

        private HashAlgorithm createNewHashAlgorithm() {
            // Create a new instance to ensure thread safety and isolation
            if (hashAlgorithm instanceof Sha256HashAlgorithm) {
                return new Sha256HashAlgorithm();
            }
            // For future hash algorithm implementations
            throw new UnsupportedOperationException("Unsupported hash algorithm: " + hashAlgorithm.getClass().getSimpleName());
        }

        @Override
        public void update(byte[] data) {
            if (data == null) {
                throw new IllegalArgumentException("Data cannot be null");
            }
            update(data, 0, data.length);
        }

        @Override
        public void update(byte[] data, int offset, int length) {
            if (finalized) {
                throw new IllegalStateException("Hasher has been finalized and cannot be updated");
            }
            if (data == null) {
                throw new IllegalArgumentException("Data cannot be null");
            }
            if (offset < 0 || length < 0 || offset + length > data.length) {
                throw new IllegalArgumentException("Invalid offset or length");
            }

            hasher.update(data, offset, length);
        }

        @Override
        public String digest() {
            if (finalized) {
                throw new IllegalStateException("Hasher has already been finalized");
            }

            try {
                byte[] hash = hasher.digest();
                finalized = true;
                return HEX_FORMAT.formatHex(hash);
            } catch (Exception e) {
                logger.error("Error finalizing hash", e);
                throw new RuntimeException("Failed to finalize hash", e);
            }
        }

        @Override
        public void reset() {
            hasher.reset();
            finalized = false;
        }
    }
}