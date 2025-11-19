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

    /** Logger for the factory. */
    private static final Logger logger = LoggerFactory.getLogger(Blake3IncrementalHasherFactory.class);
    /** Hex format for hash string representation. */
    private static final HexFormat HEX_FORMAT = HexFormat.of();

    /** Hash algorithm instance. */
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
    public IncrementalHasher createIncrementalHasher() throws HashingException {
        return Blake3IncrementalHasherImpl.create(hashAlgorithm);
    }

    /**
     * Implementation of IncrementalHasher using the provided hash algorithm.
     */
    private static class Blake3IncrementalHasherImpl implements IncrementalHasher {
        /** Hash algorithm instance. */
        private final HashAlgorithm hasher;
        /** Flag indicating if the hasher has been finalized. */
        private boolean finalized = false;

        /** Creates a new Blake3IncrementalHasherImpl with provided hasher. */
        private Blake3IncrementalHasherImpl(HashAlgorithm hasher) {
            this.hasher = hasher;
        }

        /**
         * Factory method to create a new Blake3IncrementalHasherImpl.
         * @param prototypeHashAlgorithm the hash algorithm to use as prototype
         * @return a new instance or throws RuntimeException if creation fails
         */
        private static IncrementalHasher create(HashAlgorithm prototypeHashAlgorithm) throws HashingException {
            HashAlgorithm newHasher;
            // Create a new instance to ensure thread safety and isolation
            if (prototypeHashAlgorithm instanceof Sha256HashAlgorithm) {
                try {
                    newHasher = Sha256HashAlgorithm.create();
                } catch (HashingException e) {
                    throw new HashingException("Failed to create SHA-256 algorithm instance", e);
                }
            } else {
                // For future hash algorithm implementations
                throw new UnsupportedOperationException(
                    "Unsupported hash algorithm: " + prototypeHashAlgorithm.getClass().getSimpleName());
            }
            return new Blake3IncrementalHasherImpl(newHasher);
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
            if (offset < 0 || length < 0 || offset > data.length || offset + length > data.length) {
                throw new IllegalArgumentException("Invalid offset or length: offset=" + offset + ", length=" + length + ", data.length=" + data.length);
            }

            hasher.update(data, offset, length);
        }

        @Override
        public String digest() throws HashingException {
            if (finalized) {
                throw new IllegalStateException("Hasher has already been finalized");
            }

            try {
                byte[] hash = hasher.digest();
                finalized = true;
                return HEX_FORMAT.formatHex(hash);
            } catch (Exception e) {
                logger.error("Error finalizing hash", e);
                throw new HashingException("Failed to finalize hash", e);
            }
        }

        @Override
        public void reset() {
            hasher.reset();
            finalized = false;
        }
    }
}