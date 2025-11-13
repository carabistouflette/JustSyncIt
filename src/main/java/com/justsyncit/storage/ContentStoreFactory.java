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

import java.io.IOException;
import java.nio.file.Path;

/**
 * Factory for creating ContentStore instances.
 * Follows Dependency Inversion Principle by depending on abstractions rather than concrete classes.
 * Provides a clean interface for creating different types of content stores.
 */
public final class ContentStoreFactory {

    /** Logger instance. */
    private static final Logger logger = LoggerFactory.getLogger(ContentStoreFactory.class);

    /** Private constructor to prevent instantiation. */
    private ContentStoreFactory() {
        // Utility class
    }

    /**
     * Creates a filesystem-based content store with default components.
     *
     * @param storageDirectory the directory to store chunks in
     * @param blake3Service the BLAKE3 service for hashing
     * @return a new ContentStore instance
     * @throws IOException if the store cannot be created
     * @throws IllegalArgumentException if any parameter is null
     */
    public static ContentStore createFilesystemStore(Path storageDirectory, Blake3Service blake3Service)
            throws IOException {
        validateParameters(storageDirectory, blake3Service);

        logger.info("Creating filesystem content store at {}", storageDirectory);

        Path indexFile = storageDirectory.resolve("index.txt");
        ChunkIndex chunkIndex = FilesystemChunkIndex.create(storageDirectory, indexFile);

        return FilesystemContentStore.create(storageDirectory, chunkIndex, blake3Service);
    }

    /**
     * Creates a filesystem-based content store with custom components.
     *
     * @param storageDirectory the directory to store chunks in
     * @param chunkIndex the chunk index to use
     * @param integrityVerifier the integrity verifier to use
     * @param pathGenerator the path generator to use
     * @return a new ContentStore instance
     * @throws IOException if the store cannot be created
     * @throws IllegalArgumentException if any parameter is null
     */
    public static ContentStore createFilesystemStore(Path storageDirectory,
                                             ChunkIndex chunkIndex,
                                             IntegrityVerifier integrityVerifier,
                                             ChunkPathGenerator pathGenerator)
            throws IOException {
        validateParameters(storageDirectory, chunkIndex, integrityVerifier, pathGenerator);

        logger.info("Creating filesystem content store at {} with custom components", storageDirectory);

        return FilesystemContentStore.create(storageDirectory, chunkIndex, integrityVerifier, pathGenerator);
    }

    /**
     * Creates a memory-based content store for testing or temporary use.
     *
     * @param integrityVerifier the integrity verifier to use
     * @return a new ContentStore instance
     * @throws IllegalArgumentException if integrityVerifier is null
     */
    public static ContentStore createMemoryStore(IntegrityVerifier integrityVerifier) {
        if (integrityVerifier == null) {
            throw new IllegalArgumentException("Integrity verifier cannot be null");
        }

        logger.info("Creating memory content store");

        return new MemoryContentStore(integrityVerifier);
    }

    /**
     * Creates a memory-based content store with default BLAKE3 integrity verifier.
     *
     * @param blake3Service the BLAKE3 service for hashing
     * @return a new ContentStore instance
     * @throws IllegalArgumentException if blake3Service is null
     */
    public static ContentStore createMemoryStore(Blake3Service blake3Service) {
        if (blake3Service == null) {
            throw new IllegalArgumentException("BLAKE3 service cannot be null");
        }

        logger.info("Creating memory content store with BLAKE3 verifier");

        IntegrityVerifier integrityVerifier = new Blake3IntegrityVerifier(blake3Service);
        return new MemoryContentStore(integrityVerifier);
    }

    /**
     * Validates parameters for filesystem store creation.
     *
     * @param storageDirectory the storage directory
     * @param blake3Service the BLAKE3 service
     * @throws IllegalArgumentException if any parameter is null
     */
    private static void validateParameters(Path storageDirectory, Blake3Service blake3Service) {
        if (storageDirectory == null) {
            throw new IllegalArgumentException("Storage directory cannot be null");
        }
        if (blake3Service == null) {
            throw new IllegalArgumentException("BLAKE3 service cannot be null");
        }
    }

    /**
     * Validates parameters for filesystem store creation with custom components.
     *
     * @param storageDirectory the storage directory
     * @param chunkIndex the chunk index
     * @param integrityVerifier the integrity verifier
     * @param pathGenerator the path generator
     * @throws IllegalArgumentException if any parameter is null
     */
    private static void validateParameters(Path storageDirectory,
                                     ChunkIndex chunkIndex,
                                     IntegrityVerifier integrityVerifier,
                                     ChunkPathGenerator pathGenerator) {
        if (storageDirectory == null) {
            throw new IllegalArgumentException("Storage directory cannot be null");
        }
        if (chunkIndex == null) {
            throw new IllegalArgumentException("Chunk index cannot be null");
        }
        if (integrityVerifier == null) {
            throw new IllegalArgumentException("Integrity verifier cannot be null");
        }
        if (pathGenerator == null) {
            throw new IllegalArgumentException("Path generator cannot be null");
        }
    }
}