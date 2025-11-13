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

import java.io.IOException;

/**
 * Interface for basic chunk storage operations.
 * Follows Interface Segregation Principle by focusing only on storage functionality.
 */
public interface ChunkStorage {

    /**
     * Stores a chunk of data and returns its hash.
     * If the chunk already exists (same hash), it will not be stored again.
     *
     * @param data the chunk data to store
     * @return the hash of the stored chunk
     * @throws IOException if an I/O error occurs during storage
     * @throws IllegalArgumentException if data is null or empty
     */
    String storeChunk(byte[] data) throws IOException;

    /**
     * Retrieves a chunk by its hash.
     * The integrity of the retrieved data is verified against the hash.
     *
     * @param hash the hash of the chunk to retrieve
     * @return the chunk data, or null if not found
     * @throws IOException if an I/O error occurs during retrieval
     * @throws StorageIntegrityException if the retrieved data fails integrity verification
     * @throws IllegalArgumentException if hash is null or invalid
     */
    byte[] retrieveChunk(String hash) throws IOException, StorageIntegrityException;

    /**
     * Checks if a chunk with the given hash exists in storage.
     *
     * @param hash the hash to check
     * @return true if the chunk exists, false otherwise
     * @throws IOException if an I/O error occurs during the check
     * @throws IllegalArgumentException if hash is null or invalid
     */
    boolean existsChunk(String hash) throws IOException;
}