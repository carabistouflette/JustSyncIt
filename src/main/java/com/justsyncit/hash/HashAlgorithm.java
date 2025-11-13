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

/**
 * Interface for hash algorithm implementations.
 * Provides a contract for different hash algorithms to follow.
 */
public interface HashAlgorithm {

    /**
     * Updates the hash with the provided data.
     *
     * @param data the data to add to the hash
     * @throws IllegalArgumentException if the data is null
     */
    void update(byte[] data);

    /**
     * Updates the hash with the provided data slice.
     *
     * @param data the data array containing the slice
     * @param offset the starting offset in the data array
     * @param length the number of bytes to hash
     * @throws IllegalArgumentException if data is null or offset/length are invalid
     */
    void update(byte[] data, int offset, int length);

    /**
     * Finalizes the hash computation and returns the result.
     * After calling this method, the algorithm cannot be used for further updates.
     *
     * @return the hash as a byte array
     */
    byte[] digest();

    /**
     * Resets the algorithm to its initial state, allowing reuse.
     */
    void reset();

    /**
     * Gets the name of the hash algorithm.
     *
     * @return the algorithm name (e.g., "BLAKE3", "SHA-256")
     */
    String getAlgorithmName();

    /**
     * Gets the hash length in bytes.
     *
     * @return the hash length in bytes
     */
    int getHashLength();
}