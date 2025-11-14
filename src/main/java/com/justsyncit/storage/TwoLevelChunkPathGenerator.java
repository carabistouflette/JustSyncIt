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

import com.justsyncit.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Implementation of ChunkPathGenerator that uses a two-level directory structure.
 * The first 2 characters of the hash become the subdirectory, and the rest become the filename.
 * This prevents too many files in a single directory and improves filesystem performance.
 */
public final class TwoLevelChunkPathGenerator implements ChunkPathGenerator {

    /** Logger instance. */
    private static final Logger logger = LoggerFactory.getLogger(TwoLevelChunkPathGenerator.class);

    /** Minimum hash length for this path generator. */
    private static final int MIN_HASH_LENGTH = 4;

    /**
     * Creates a new TwoLevelChunkPathGenerator.
     */
    public TwoLevelChunkPathGenerator() {
        // No initialization needed
    }

    @Override
    public Path generatePath(Path storageDirectory, String hash) throws ServiceException {
        validateHash(hash);

        // Use first 2 characters as subdirectory, rest as filename
        String subDir = hash.substring(0, 2);
        String fileName = hash.substring(2);
        Path chunkPath = storageDirectory.resolve(subDir).resolve(fileName);
        Path parentDir = chunkPath.getParent();

        // Ensure parent directories exist
        if (parentDir != null) {
            try {
                Files.createDirectories(parentDir);
            } catch (IOException e) {
                throw new ServiceException("Failed to create chunk directory: " + parentDir, e);
            }
        }

        logger.debug("Generated path {} for hash {}", chunkPath, hash);
        return chunkPath;
    }

    @Override
    public void validateHash(String hash) {
        if (hash == null || hash.trim().isEmpty()) {
            throw new IllegalArgumentException("Hash cannot be null or empty");
        }
        if (hash.length() < MIN_HASH_LENGTH) {
            throw new IllegalArgumentException("Hash must be at least " + MIN_HASH_LENGTH + " characters long");
        }
    }
}