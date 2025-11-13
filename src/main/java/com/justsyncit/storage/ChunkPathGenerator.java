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

import java.nio.file.Path;

/**
 * Interface for generating file paths for chunks based on their hash.
 * Different implementations can use different strategies for organizing chunks.
 */
public interface ChunkPathGenerator {

    /**
     * Generates the file path for a chunk based on its hash.
     *
     * @param storageDirectory the base storage directory
     * @param hash the chunk hash
     * @return the file path for the chunk
     * @throws IllegalArgumentException if hash is null or invalid
     */
    Path generatePath(Path storageDirectory, String hash);

    /**
     * Validates that a hash is compatible with this path generator.
     *
     * @param hash the hash to validate
     * @throws IllegalArgumentException if hash is null or invalid
     */
    void validateHash(String hash);
}