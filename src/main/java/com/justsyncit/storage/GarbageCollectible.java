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
import java.util.Set;

/**
 * Interface for garbage collection functionality.
 * Follows Interface Segregation Principle by focusing only on garbage collection.
 */
public interface GarbageCollectible {

    /**
     * Performs garbage collection to remove orphaned chunks.
     * Orphaned chunks are those not referenced in the provided active hashes set.
     *
     * @param activeHashes set of hashes that are currently referenced
     * @return the number of chunks removed during garbage collection
     * @throws IOException if an I/O error occurs during garbage collection
     * @throws IllegalArgumentException if activeHashes is null
     */
    long garbageCollect(Set<String> activeHashes) throws IOException;
}