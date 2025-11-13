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

package com.justsyncit.storage;

import com.justsyncit.hash.Blake3Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory for creating IntegrityVerifier instances.
 * Follows Dependency Inversion Principle by depending on abstractions rather than concrete classes.
 * Provides a clean interface for creating different types of integrity verifiers.
 */
public final class IntegrityVerifierFactory {

    /** Logger instance. */
    private static final Logger logger = LoggerFactory.getLogger(IntegrityVerifierFactory.class);

    /** Private constructor to prevent instantiation. */
    private IntegrityVerifierFactory() {
        // Utility class
    }

    /**
     * Creates a BLAKE3-based integrity verifier.
     *
     * @param blake3Service the BLAKE3 service for hashing
     * @return a new IntegrityVerifier instance
     * @throws IllegalArgumentException if blake3Service is null
     */
    public static IntegrityVerifier createBlake3Verifier(Blake3Service blake3Service) {
        if (blake3Service == null) {
            throw new IllegalArgumentException("BLAKE3 service cannot be null");
        }

        logger.info("Creating BLAKE3 integrity verifier");

        return new Blake3IntegrityVerifier(blake3Service);
    }

    /**
     * Creates a no-op integrity verifier for testing purposes.
     * This verifier doesn't actually verify integrity - it just returns the input hash.
     * WARNING: This should only be used for testing!
     *
     * @return a new IntegrityVerifier instance that doesn't verify integrity
     */
    public static IntegrityVerifier createNoOpVerifier() {
        logger.warn("Creating no-op integrity verifier for testing purposes only");

        return new NoOpIntegrityVerifier();
    }

    /**
     * No-op implementation of IntegrityVerifier for testing purposes.
     * This implementation doesn't actually verify integrity.
     */
    private static final class NoOpIntegrityVerifier implements IntegrityVerifier {

        @Override
        public void verifyIntegrity(byte[] data, String expectedHash) throws StorageIntegrityException {
            // No-op - always passes verification
            // WARNING: This is unsafe and should only be used for testing!
        }

        @Override
        public String calculateHash(byte[] data) {
            // Return a simple hash for testing
            // In a real implementation, this would use a proper hash algorithm
            return "test_hash_" + data.length;
        }

        @Override
        public void validateHash(String hash) {
            if (hash == null || hash.trim().isEmpty()) {
                throw new IllegalArgumentException("Hash cannot be null or empty");
            }
        }
    }
}