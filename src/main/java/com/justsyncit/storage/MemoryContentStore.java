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
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of ContentStore for testing and development.
 * Follows Liskov Substitution Principle by maintaining the same contract as FilesystemContentStore.
 * All methods behave consistently with the interface contracts.
 */
public final class MemoryContentStore extends AbstractContentStore {

    /** In-memory storage for chunks. */
    private final Map<String, byte[]> chunkStorage;
    /** Integrity verifier for hash verification. */
    private final IntegrityVerifier integrityVerifier;

    /**
     * Creates a new MemoryContentStore.
     *
     * @param integrityVerifier the integrity verifier to use
     * @throws IllegalArgumentException if integrityVerifier is null
     */
    public MemoryContentStore(IntegrityVerifier integrityVerifier) {
        if (integrityVerifier == null) {
            throw new IllegalArgumentException("Integrity verifier cannot be null");
        }
        this.chunkStorage = new ConcurrentHashMap<>();
        this.integrityVerifier = integrityVerifier;
    }

    @Override
    protected String doStoreChunk(byte[] data) throws IOException {
        // Calculate hash of the data
        String hash = integrityVerifier.calculateHash(data);

        // Check if chunk already exists
        if (chunkStorage.containsKey(hash)) {
            logger.debug("Chunk {} already exists in memory, skipping storage", hash);
            return hash;
        }

        // Store the chunk in memory
        chunkStorage.put(hash, data.clone()); // Defensive copy
        logger.debug("Stored chunk {} ({} bytes) in memory", hash, data.length);
        return hash;
    }

    @Override
    protected byte[] doRetrieveChunk(String hash) throws IOException, StorageIntegrityException {
        byte[] data = chunkStorage.get(hash);
        if (data == null) {
            logger.debug("Chunk {} not found in memory", hash);
            return new byte[0]; // Return empty array instead of null
        }

        // Verify integrity
        integrityVerifier.verifyIntegrity(data, hash);

        logger.debug("Retrieved chunk {} ({} bytes) from memory", hash, data.length);
        return data.clone(); // Defensive copy
    }

    @Override
    protected boolean doExistsChunk(String hash) throws IOException {
        return chunkStorage.containsKey(hash);
    }

    @Override
    protected long doGetChunkCount() throws IOException {
        return chunkStorage.size();
    }

    @Override
    protected long doGetTotalSize() throws IOException {
        long totalSize = 0;
        for (byte[] data : chunkStorage.values()) {
            totalSize += data.length;
        }
        return totalSize;
    }

    @Override
    protected long doGarbageCollect(Set<String> activeHashes) throws IOException {
        long removedCount = 0;
        Map<String, byte[]> toRemove = new HashMap<>();

        for (Map.Entry<String, byte[]> entry : chunkStorage.entrySet()) {
            String hash = entry.getKey();
            if (!activeHashes.contains(hash)) {
                toRemove.put(hash, entry.getValue());
                removedCount++;
            }
        }

        // Remove orphaned chunks
        for (String hash : toRemove.keySet()) {
            chunkStorage.remove(hash);
            logger.debug("Deleted orphaned chunk {} from memory", hash);
        }

        return removedCount;
    }

    @Override
    protected ContentStoreStats doGetStats() throws IOException {
        long chunkCount = doGetChunkCount();
        long totalSize = doGetTotalSize();
        long orphanedChunks = 0; // Would need additional tracking for accurate count

        return new ContentStoreStats(
                chunkCount,
                totalSize,
                1L, // Simplified ratio - would need more tracking for accurate calculation
                lastGcTime,
                orphanedChunks
        );
    }

    @Override
    protected void doClose() throws IOException {
        chunkStorage.clear();
        logger.info("Closed memory content store");
    }

    /**
     * Gets the current number of chunks in memory.
     * This method is specific to MemoryContentStore and not part of the interface.
     *
     * @return the number of chunks currently stored
     */
    public int getCurrentChunkCount() {
        return chunkStorage.size();
    }

    /**
     * Checks if the memory store is empty.
     * This method is specific to MemoryContentStore and not part of the interface.
     *
     * @return true if no chunks are stored, false otherwise
     */
    public boolean isEmpty() {
        return chunkStorage.isEmpty();
    }
}