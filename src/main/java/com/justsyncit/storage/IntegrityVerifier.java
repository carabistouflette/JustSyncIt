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

/**
 * Interface for verifying the integrity of stored chunks.
 * Different implementations can use different hashing algorithms or verification strategies.
 */
public interface IntegrityVerifier {

    /**
     * Verifies the integrity of data against its expected hash.
     *
     * @param data the data to verify
     * @param expectedHash the expected hash of the data
     * @throws StorageIntegrityException if the integrity verification fails
     * @throws IllegalArgumentException if data or expectedHash is null
     */
    void verifyIntegrity(byte[] data, String expectedHash) throws StorageIntegrityException;

    /**
     * Calculates the hash of the given data.
     *
     * @param data the data to hash
     * @return the hash of the data
     * @throws IllegalArgumentException if data is null
     */
    String calculateHash(byte[] data);

    /**
     * Validates that a hash is compatible with this verifier.
     *
     * @param hash the hash to validate
     * @throws IllegalArgumentException if hash is null or invalid
     */
    void validateHash(String hash);
}