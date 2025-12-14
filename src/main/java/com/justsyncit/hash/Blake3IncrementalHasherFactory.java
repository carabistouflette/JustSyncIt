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
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Factory for creating incremental hashers.
 * Note: Despite the class name containing "Blake3", this factory currently
 * supports SHA-256
 * as a fallback until a true BLAKE3 implementation is available.
 * Follows Single Responsibility Principle by focusing only on factory
 * operations.
 */
public class Blake3IncrementalHasherFactory implements IncrementalHasherFactory {

    /** Logger for the factory. */
    private static final Logger logger = LoggerFactory.getLogger(Blake3IncrementalHasherFactory.class);
    /** Hex format for hash string representation. */
    private static final HexFormat HEX_FORMAT = HexFormat.of();

    /** Hash algorithm prototype for creating new instances. */
    private final HashAlgorithm hashAlgorithm;

    /**
     * Creates a new Blake3IncrementalHasherFactory with the provided hash
     * algorithm.
     *
     * @param hashAlgorithm the hash algorithm to use as prototype
     * @throws IllegalArgumentException if hashAlgorithm is null
     */
    public Blake3IncrementalHasherFactory(HashAlgorithm hashAlgorithm) {
        this.hashAlgorithm = Objects.requireNonNull(hashAlgorithm, "Hash algorithm cannot be null");
        logger.debug("Created Blake3IncrementalHasherFactory with algorithm: {}",
                hashAlgorithm.getAlgorithmName());
    }

    @Override
    public IncrementalHasher createIncrementalHasher() throws HashingException {
        return IncrementalHasherImpl.create(hashAlgorithm);
    }

    @Override
    public IncrementalHasher createThreadSafeIncrementalHasher() throws HashingException {
        return IncrementalHasherImpl.create(hashAlgorithm);
    }

    @Override
    public String getAlgorithmName() {
        return hashAlgorithm.getAlgorithmName();
    }

    @Override
    public int getHashLength() {
        return hashAlgorithm.getHashLength();
    }

    /**
     * Thread-safe implementation of IncrementalHasher using the provided hash
     * algorithm.
     */
    private static class IncrementalHasherImpl implements IncrementalHasher {
        /** Hash algorithm instance. */
        private final HashAlgorithm hasher;
        /** Lock for thread safety. */
        private final ReentrantLock lock = new ReentrantLock();
        /** Flag indicating if the hasher has been finalized. */
        private volatile boolean finalized = false;

        /**
         * Creates a new IncrementalHasherImpl with provided hasher.
         *
         * @param hasher the hash algorithm instance
         * @throws IllegalArgumentException if hasher is null
         */
        private IncrementalHasherImpl(HashAlgorithm hasher) {
            this.hasher = Objects.requireNonNull(hasher, "Hasher cannot be null");
        }

        /**
         * Factory method to create a new IncrementalHasherImpl.
         * Creates a new instance to ensure thread safety and isolation.
         *
         * @param prototypeHashAlgorithm the hash algorithm to use as prototype
         * @return a new IncrementalHasherImpl instance
         * @throws HashingException         if creation fails
         * @throws IllegalArgumentException if prototypeHashAlgorithm is null
         */
        private static IncrementalHasher create(HashAlgorithm prototypeHashAlgorithm) throws HashingException {
            Objects.requireNonNull(prototypeHashAlgorithm, "Prototype hash algorithm cannot be null");

            HashAlgorithm newHasher;
            try {
                // Create a new instance based on the prototype type
                if (prototypeHashAlgorithm instanceof Sha256HashAlgorithm) {
                    newHasher = Sha256HashAlgorithm.create();
                    logger.debug("Created SHA-256 hasher instance");
                } else {
                    // Try to create a new instance using reflection for other implementations
                    // This allows for extensibility without modifying this code
                    try {
                        newHasher = (HashAlgorithm) prototypeHashAlgorithm.getClass()
                                .getMethod("create")
                                .invoke(null);
                        logger.debug("Created hasher instance using reflection: {}",
                                prototypeHashAlgorithm.getClass().getSimpleName());
                    } catch (Exception e) {
                        throw new HashingException(
                                "Failed to create hasher instance: " + prototypeHashAlgorithm.getClass().getSimpleName()
                                        + ". Ensure the class has a static create() method.",
                                e);
                    }
                }
            } catch (HashingException e) {
                throw new HashingException("Failed to create hash algorithm instance", e);
            } catch (Exception e) {
                throw new HashingException("Unexpected error creating hash algorithm instance", e);
            }

            return new IncrementalHasherImpl(newHasher);
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
            // Validate input parameters
            if (data == null) {
                throw new IllegalArgumentException("Data cannot be null");
            }
            if (offset < 0 || length < 0 || offset > data.length || offset + length > data.length) {
                throw new IllegalArgumentException(
                        "Invalid offset or length: offset=" + offset + ", length=" + length
                                + ", data.length=" + data.length);
            }

            lock.lock();
            try {
                if (finalized) {
                    throw new IllegalStateException("Hasher has been finalized and cannot be updated");
                }
                hasher.update(data, offset, length);
            } finally {
                lock.unlock();
            }
        }

        @Override
        public String digest() throws HashingException {
            lock.lock();
            try {
                if (finalized) {
                    throw new IllegalStateException("Hasher has already been finalized");
                }

                try {
                    byte[] hash = hasher.digest();
                    finalized = true;
                    String hexHash = HEX_FORMAT.formatHex(hash);
                    logger.debug("Generated hash with length: {}", hexHash.length());
                    return hexHash;
                } catch (Exception e) {
                    logger.error("Error finalizing hash", e);
                    throw new HashingException("Failed to finalize hash", e);
                }
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void reset() {
            lock.lock();
            try {
                hasher.reset();
                finalized = false;
                logger.debug("Hasher reset to initial state");
            } finally {
                lock.unlock();
            }
        }

        @Override
        public String getAlgorithmName() {
            return hasher.getAlgorithmName();
        }

        @Override
        public int getHashLength() {
            return hasher.getHashLength();
        }

        @Override
        public boolean isThreadSafe() {
            return true; // This implementation uses ReentrantLock for thread safety
        }

        @Override
        public boolean isFinalized() {
            return finalized;
        }

        @Override
        public void close() {
            lock.lock();
            try {
                if (hasher != null) {
                    try {
                        hasher.close();
                    } catch (Exception e) {
                        logger.warn("Error closing underlying hasher", e);
                    }
                }
            } finally {
                lock.unlock();
            }
        }
    }
}