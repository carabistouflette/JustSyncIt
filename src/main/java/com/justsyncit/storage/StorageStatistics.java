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
 * Interface for storage statistics and monitoring.
 * Follows Interface Segregation Principle by focusing only on statistics functionality.
 */
public interface StorageStatistics {

    /**
     * Gets the total number of chunks stored.
     *
     * @return the number of stored chunks
     * @throws IOException if an I/O error occurs
     */
    long getChunkCount() throws IOException;

    /**
     * Gets the total storage size in bytes.
     *
     * @return the total storage size
     * @throws IOException if an I/O error occurs
     */
    long getTotalSize() throws IOException;

    /**
     * Gets statistics about the content store.
     *
     * @return storage statistics
     * @throws IOException if an I/O error occurs
     */
    ContentStoreStats getStats() throws IOException;
}