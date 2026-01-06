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
import com.justsyncit.hash.HashingException;
import com.justsyncit.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Filesystem-based implementation of ContentStore using Java NIO.
 * Provides content-addressable storage with automatic deduplication.
 * Follows Single Responsibility Principle by delegating to specialized
 * components.
 * Extends AbstractContentStore to follow Open/Closed Principle.
 */
public final class FilesystemContentStore extends AbstractContentStore {

    /** Logger instance. */
    private static final Logger logger = LoggerFactory.getLogger(FilesystemContentStore.class);

    /** The directory where chunks are stored. */
    private final Path storageDirectory;
    /** The chunk index for mapping hashes to paths. */
    private final ChunkIndex chunkIndex;
    /** The integrity verifier for hash verification. */
    private final IntegrityVerifier integrityVerifier;
    /** The path generator for chunk file paths. */
    private final ChunkPathGenerator pathGenerator;
    /** Atomic counter for total size to avoid O(N) scans. */
    private final AtomicLong totalSize;
    /** File to persist total size. */
    private final Path sizeFile;

    /**
     * Creates a new FilesystemContentStore.
     *
     * @param storageDirectory  the directory to store chunks in
     * @param chunkIndex        the chunk index to use
     * @param integrityVerifier the integrity verifier to use
     * @param pathGenerator     the path generator to use
     * @throws IOException if the storage cannot be initialized
     */
    private FilesystemContentStore(Path storageDirectory, ChunkIndex chunkIndex,
            IntegrityVerifier integrityVerifier,
            ChunkPathGenerator pathGenerator) throws IOException {
        this.storageDirectory = storageDirectory;
        this.chunkIndex = chunkIndex;
        this.integrityVerifier = integrityVerifier;
        this.pathGenerator = pathGenerator;
        this.sizeFile = storageDirectory.resolve("size.dat");

        // Create storage directory if it doesn't exist
        Files.createDirectories(storageDirectory);

        this.totalSize = new AtomicLong(0);
        if (Files.exists(sizeFile)) {
            try {
                long savedSize = java.nio.ByteBuffer.wrap(Files.readAllBytes(sizeFile)).getLong();
                totalSize.set(savedSize);
                logger.info("Loaded total size from persistence: {} bytes", savedSize);
            } catch (IOException e) {
                logger.warn("Failed to load size file, falling back to scan", e);
                startAsyncSizeCalculation();
            }
        } else {
            startAsyncSizeCalculation();
        }

        logger.info("Initialized filesystem content store at {}", storageDirectory);
    }

    private void startAsyncSizeCalculation() {
        // [PERF-001] Do NOT walk the filesystem on startup. It is too expensive.
        // If size.dat is missing, we initialize to 0 and accept the inaccuracy until
        // stats are rebuilt or updated.
        logger.warn("Total size unknown (size.dat missing). Initializing to 0 to avoid expensive filesystem scan.");
        totalSize.set(0);
        saveTotalSize();
    }

    private void saveTotalSize() {
        try {
            byte[] bytes = java.nio.ByteBuffer.allocate(8).putLong(totalSize.get()).array();
            Files.write(sizeFile, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
        } catch (IOException e) {
            logger.warn("Failed to save total size persistence", e);
        }
    }

    /**
     * Creates a new FilesystemContentStore with default components.
     *
     * @param storageDirectory directory to store chunks in
     * @param chunkIndex       chunk index to use
     * @param blake3Service    BLAKE3 service for hashing
     * @return a new FilesystemContentStore instance
     * @throws IOException if storage cannot be initialized
     */
    public static FilesystemContentStore create(Path storageDirectory, ChunkIndex chunkIndex,
            Blake3Service blake3Service) throws IOException {
        IntegrityVerifier integrityVerifier = new Blake3IntegrityVerifier(blake3Service);
        ChunkPathGenerator pathGenerator = new TwoLevelChunkPathGenerator();
        return new FilesystemContentStore(storageDirectory, chunkIndex, integrityVerifier, pathGenerator);
    }

    /**
     * Creates a new FilesystemContentStore with custom components.
     *
     * @param storageDirectory  directory to store chunks in
     * @param chunkIndex        chunk index to use
     * @param integrityVerifier integrity verifier to use
     * @param pathGenerator     path generator to use
     * @return a new FilesystemContentStore instance
     * @throws IOException if storage cannot be initialized
     */
    public static FilesystemContentStore create(Path storageDirectory, ChunkIndex chunkIndex,
            IntegrityVerifier integrityVerifier,
            ChunkPathGenerator pathGenerator) throws IOException {
        return new FilesystemContentStore(storageDirectory, chunkIndex, integrityVerifier, pathGenerator);
    }

    @Override
    protected String doStoreChunk(byte[] data) throws IOException {
        // Calculate hash of the data
        String hash;
        try {
            hash = integrityVerifier.calculateHash(data);
        } catch (HashingException e) {
            throw new IOException("Failed to calculate hash for chunk", e);
        }

        // Check if chunk already exists
        lock.readLock().lock();
        try {
            if (chunkIndex.containsChunk(hash)) {
                logger.debug("Chunk {} already exists, skipping storage", hash);
                return hash;
            }
        } finally {
            lock.readLock().unlock();
        }

        // Store the chunk
        lock.writeLock().lock();
        try {
            // Double-check after acquiring write lock
            if (chunkIndex.containsChunk(hash)) {
                return hash;
            }

            Path chunkPath;
            try {
                chunkPath = pathGenerator.generatePath(storageDirectory, hash);
            } catch (ServiceException e) {
                throw new IOException("Failed to generate path for chunk", e);
            }

            logger.trace("Storing chunk {} to {}", hash, chunkPath);
            // Write chunk to file (overwrite if exists to handle repair/corruption cases)
            // Durability is handled by replication and filesystem journaling.
            Files.write(chunkPath, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);

            // Update stats
            totalSize.addAndGet(data.length);
            saveTotalSize(); // Persist size change

            // Add to index (or update)
            chunkIndex.putChunk(hash, chunkPath);

            logger.debug("Stored chunk {} ({} bytes) at {}", hash, data.length, chunkPath);
            return hash;

        } catch (IOException e) {
            // Clean up partial write if it exists
            Path chunkPath;
            try {
                chunkPath = pathGenerator.generatePath(storageDirectory, hash);
            } catch (ServiceException pathException) {
                // If we can't generate the path, we can't clean up the file
                logger.warn("Failed to generate path for cleanup: {}", pathException.getMessage());
                throw e;
            }
            try {
                Files.deleteIfExists(chunkPath);
            } catch (IOException cleanupException) {
                logger.warn("Failed to cleanup partial chunk file: {}", cleanupException.getMessage());
            }
            throw e;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    protected byte[] doRetrieveChunk(String hash) throws IOException, StorageIntegrityException {
        lock.readLock().lock();
        try {
            Path chunkPath = chunkIndex.getChunkPath(hash);
            if (chunkPath == null) {
                logger.warn("Chunk {} not found in index", hash);
                return null;
            }

            logger.trace("Retrieving chunk {} from {}", hash, chunkPath);
            if (!Files.exists(chunkPath)) {
                logger.warn("Chunk {} found in index but file missing at {}", hash, chunkPath);
                // Remove from index since file is missing
                try {
                    chunkIndex.removeChunk(hash);
                } catch (IOException e) {
                    logger.error("Failed to remove missing chunk from index: {}", e.getMessage());
                }
                return null;
            }
            byte[] data = Files.readAllBytes(chunkPath);

            // Verify integrity
            integrityVerifier.verifyIntegrity(data, hash);

            logger.debug("Retrieved chunk {} ({} bytes)", hash, data.length);
            return data;

        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    protected boolean doExistsChunk(String hash) throws IOException {
        lock.readLock().lock();
        try {
            return chunkIndex.containsChunk(hash);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    protected void doDeleteChunk(String hash) throws IOException {
        // We already hold the write lock from AbstractContentStore.deleteChunk

        Path chunkPath = chunkIndex.getChunkPath(hash);
        if (chunkPath != null) {
            long size = Files.exists(chunkPath) ? Files.size(chunkPath) : 0L;
            Files.deleteIfExists(chunkPath);
            chunkIndex.removeChunk(hash);
            totalSize.addAndGet(-size);
            saveTotalSize();
        }
    }

    @Override
    protected long doGetChunkCount() throws IOException {
        lock.readLock().lock();
        try {
            return chunkIndex.getChunkCount();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    protected long doGetTotalSize() throws IOException {
        return totalSize.get();
    }

    @Override
    protected long doGarbageCollect(Set<String> activeHashes) throws IOException {
        lock.writeLock().lock();
        try {
            Set<String> allHashes = chunkIndex.getAllHashes();
            long removedCount = 0;

            for (String hash : allHashes) {
                if (!activeHashes.contains(hash)) {
                    Path chunkPath = chunkIndex.getChunkPath(hash);
                    if (chunkPath != null) {
                        try {
                            long size = Files.exists(chunkPath) ? Files.size(chunkPath) : 0L;
                            Files.deleteIfExists(chunkPath);
                            chunkIndex.removeChunk(hash);
                            totalSize.addAndGet(-size);
                            removedCount++;
                            logger.debug("Deleted orphaned chunk {}", hash);
                        } catch (IOException e) {
                            logger.warn("Failed to delete orphaned chunk {}: {}", hash, e.getMessage());
                        }
                    }
                }
            }

            return removedCount;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    protected ContentStoreStats doGetStats() throws IOException {
        lock.readLock().lock();
        try {
            long chunkCount = chunkIndex.getChunkCount();
            long totalSize = doGetTotalSize();
            long orphanedChunks = 0; // Would need additional tracking for accurate count

            return new ContentStoreStats(
                    chunkCount,
                    totalSize,
                    1L, // Simplified ratio - would need more tracking for accurate calculation
                    lastGcTime,
                    orphanedChunks);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    protected void doClose() throws IOException {
        chunkIndex.close();
        logger.info("Closed filesystem content store");
    }

    /**
     * Gets the integrity verifier used by this store.
     *
     * @return the integrity verifier
     */
    public IntegrityVerifier getIntegrityVerifier() {
        return integrityVerifier;
    }

    /**
     * Gets the path to a chunk file.
     * Useful for integrity testing and low-level access.
     *
     * @param hash the chunk hash
     * @return the path to the chunk file, or null if not indexed
     * @throws IOException if index access fails
     */
    public Path getChunkPath(String hash) throws IOException {
        lock.readLock().lock();
        try {
            return chunkIndex.getChunkPath(hash);
        } finally {
            lock.readLock().unlock();
        }
    }
}