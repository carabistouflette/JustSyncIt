/*
 * JustSyncIt - Backup solution
 * Copyright (C) 2023 JustSyncIt Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.justsyncit.storage;

import com.justsyncit.integrity.service.ReedSolomonService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Set;

/**
 * Decorator for ContentStore that adds self-healing capabilities using
 * Reed-Solomon error correction.
 * If a chunk fails integrity verification during retrieval, this store attempts
 * to repair it
 * before failing.
 */
public class HealingContentStore implements ContentStore {
    private static final Logger logger = LoggerFactory.getLogger(HealingContentStore.class);

    private final ContentStore delegate;
    private final ReedSolomonService reedSolomonService;

    /**
     * Creates a new HealingContentStore.
     *
     * @param delegate           the underlying content store
     * @param reedSolomonService the Reed-Solomon service for repair
     */
    public HealingContentStore(ContentStore delegate, ReedSolomonService reedSolomonService) {
        this.delegate = delegate;
        this.reedSolomonService = reedSolomonService;
    }

    @Override
    public String storeChunk(byte[] data) throws IOException {
        return delegate.storeChunk(data);
    }

    @Override
    public byte[] retrieveChunk(String hash) throws IOException, StorageIntegrityException {
        try {
            return delegate.retrieveChunk(hash);
        } catch (StorageIntegrityException e) {
            logger.warn("Chunk {} is corrupt, attempting repair...", hash);
            if (reedSolomonService.repairChunk(hash)) {
                logger.info("Chunk {} repaired successfully. Retrying retrieval.", hash);
                return delegate.retrieveChunk(hash);
            } else {
                logger.error("Failed to repair chunk {}.", hash);
                throw e;
            }
        }
    }

    @Override
    public boolean existsChunk(String hash) throws IOException {
        return delegate.existsChunk(hash);
    }

    @Override
    public void deleteChunk(String hash) throws IOException {
        delegate.deleteChunk(hash);
    }

    @Override
    public long getChunkCount() throws IOException {
        return delegate.getChunkCount();
    }

    @Override
    public long getTotalSize() throws IOException {
        return delegate.getTotalSize();
    }

    @Override
    public long garbageCollect(Set<String> activeHashes) throws IOException {
        return delegate.garbageCollect(activeHashes);
    }

    @Override
    public ContentStoreStats getStats() throws IOException {
        return delegate.getStats();
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }
}
