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
 * Factory interface for creating incremental hashers.
 * Follows Interface Segregation Principle by focusing only on factory operations.
 */
public interface IncrementalHasherFactory {

    /**
     * Creates a new incremental hasher for large files or streaming data.
     *
     * @return a new IncrementalHasher instance
     * @throws HashingException if hasher creation fails
     */
    IncrementalHasher createIncrementalHasher() throws HashingException;

    /**
     * Interface for incremental hashing operations.
     * Useful for hashing large files or streaming data without loading everything into memory.
     */
    interface IncrementalHasher {

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
         * After calling this method, the hasher cannot be used for further updates.
         *
         * @return the hash as a hexadecimal string
         */
        String digest() throws HashingException;

        /**
         * Resets the hasher to its initial state, allowing reuse.
         */
        void reset();
    }
}