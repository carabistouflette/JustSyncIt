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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory for creating ChunkPathGenerator instances.
 * Follows Dependency Inversion Principle by depending on abstractions rather than concrete classes.
 * Provides a clean interface for creating different types of path generators.
 */
public final class ChunkPathGeneratorFactory {

    /** Logger instance. */
    private static final Logger logger = LoggerFactory.getLogger(ChunkPathGeneratorFactory.class);

    /** Private constructor to prevent instantiation. */
    private ChunkPathGeneratorFactory() {
        // Utility class
    }

    /**
     * Creates a two-level chunk path generator.
     * Uses the first 2 characters of the hash as subdirectory and the rest as filename.
     *
     * @return a new ChunkPathGenerator instance
     */
    public static ChunkPathGenerator createTwoLevelGenerator() {
        logger.info("Creating two-level chunk path generator");

        return new TwoLevelChunkPathGenerator();
    }

    /**
     * Creates a flat chunk path generator.
     * Uses the entire hash as the filename without subdirectories.
     * WARNING: This may cause performance issues with large numbers of chunks.
     *
     * @return a new ChunkPathGenerator instance
     */
    public static ChunkPathGenerator createFlatGenerator() {
        logger.info("Creating flat chunk path generator");

        return new FlatChunkPathGenerator();
    }

    /**
     * Creates a single-level chunk path generator.
     * Uses the first character of the hash as subdirectory and the rest as filename.
     *
     * @return a new ChunkPathGenerator instance
     */
    public static ChunkPathGenerator createSingleLevelGenerator() {
        logger.info("Creating single-level chunk path generator");

        return new SingleLevelChunkPathGenerator();
    }

    /**
     * Flat implementation of ChunkPathGenerator for testing purposes.
     * Uses the entire hash as the filename without subdirectories.
     */
    private static final class FlatChunkPathGenerator implements ChunkPathGenerator {

        @Override
        public java.nio.file.Path generatePath(java.nio.file.Path storageDirectory, String hash) {
            validateHash(hash);

            return storageDirectory.resolve(hash);
        }

        @Override
        public void validateHash(String hash) {
            if (hash == null || hash.trim().isEmpty()) {
                throw new IllegalArgumentException("Hash cannot be null or empty");
            }
        }
    }

    /**
     * Single-level implementation of ChunkPathGenerator.
     * Uses the first character of the hash as subdirectory and the rest as filename.
     */
    private static final class SingleLevelChunkPathGenerator implements ChunkPathGenerator {

        @Override
        public java.nio.file.Path generatePath(java.nio.file.Path storageDirectory, String hash) {
            validateHash(hash);

            String subDir = hash.substring(0, 1);
            String fileName = hash.substring(1);
            java.nio.file.Path chunkPath = storageDirectory.resolve(subDir).resolve(fileName);
            java.nio.file.Path parentDir = chunkPath.getParent();

            // Ensure parent directories exist
            if (parentDir != null) {
                try {
                    java.nio.file.Files.createDirectories(parentDir);
                } catch (java.io.IOException e) {
                    throw new RuntimeException(
                            "Failed to create chunk directory: " + parentDir,
                            e);
                }
            }

            return chunkPath;
        }

        @Override
        public void validateHash(String hash) {
            if (hash == null || hash.trim().isEmpty()) {
                throw new IllegalArgumentException("Hash cannot be null or empty");
            }
            if (hash.length() < 2) {
                throw new IllegalArgumentException(
                        "Hash must be at least 2 characters long for single-level generator");
            }
        }
    }
}