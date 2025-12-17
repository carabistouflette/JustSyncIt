/*
 * JustSyncIt - Backup solution
 * Copyright (C) 2023 JustSyncIt Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.justsyncit.integrity.service;

import com.justsyncit.storage.ContentStore;
import com.justsyncit.storage.metadata.ChunkMetadata;
import com.justsyncit.storage.metadata.MetadataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/**
 * Service for performing background integrity checks and maintenance.
 * Scans all chunks to verify integrity (triggering self-healing if needed)
 * and ensures all chunks are protected by parity groups.
 */
public class IntegrityCheckService {
    private static final Logger logger = LoggerFactory.getLogger(IntegrityCheckService.class);

    private final ContentStore contentStore;
    private final MetadataService metadataService;
    private final ReedSolomonService reedSolomonService;

    // Parity configuration (should match RS service defaults)
    private static final int DATA_SHARDS = 4;

    /**
     * Creates a new IntegrityCheckService.
     *
     * @param contentStore       the content store (should be the
     *                           HealingContentStore)
     * @param metadataService    the metadata service
     * @param reedSolomonService the Reed-Solomon service for parity creation
     */
    public IntegrityCheckService(ContentStore contentStore,
            MetadataService metadataService,
            ReedSolomonService reedSolomonService) {
        this.contentStore = contentStore;
        this.metadataService = metadataService;
        this.reedSolomonService = reedSolomonService;
    }

    /**
     * Runs a full integrity check on all chunks.
     * Repairs corrupt chunks (via HealingContentStore) and creates missing parity
     * groups.
     *
     * @return Result of the integrity check
     */
    public IntegrityCheckResult runIntegrityCheck() {
        logger.info("Starting integrity check...");
        long startTime = System.currentTimeMillis();

        AtomicLong checkedCount = new AtomicLong(0);
        AtomicLong repairedCount = new AtomicLong(0); // Note: explicit repair tracking is hard if hidden in
                                                      // HealingStore, assume exceptions mean failure
        AtomicLong failedCount = new AtomicLong(0);
        AtomicLong parityGroupsCreated = new AtomicLong(0);

        List<String> pendingParityChunkHashes = new ArrayList<>();

        try (Stream<ChunkMetadata> chunks = metadataService.streamAllChunks()) {
            chunks.forEach(chunk -> {
                String hash = chunk.getHash();
                checkedCount.incrementAndGet();

                // 1. Verify Integrity (and implicitly repair)
                try {
                    // Retrieving the chunk forces an integrity check.
                    // If HealingContentStore is used, it will auto-repair.
                    // We don't keep the data to save memory.
                    contentStore.retrieveChunk(hash);
                } catch (Exception e) {
                    logger.error("Integrity check failed for chunk {}", hash, e);
                    failedCount.incrementAndGet();
                }

                // 2. Ensure Parity Coverage
                try {
                    if (metadataService.getChunkParityEntry(hash).isEmpty()) {
                        pendingParityChunkHashes.add(hash);

                        if (pendingParityChunkHashes.size() >= DATA_SHARDS) {
                            reedSolomonService.createParityGroup(new ArrayList<>(pendingParityChunkHashes));
                            parityGroupsCreated.incrementAndGet();
                            pendingParityChunkHashes.clear();
                        }
                    }
                } catch (IOException e) {
                    logger.error("Failed to check/create parity for chunk {}", hash, e);
                }
            });

            // Process remaining chunks for parity
            if (!pendingParityChunkHashes.isEmpty()) {
                try {
                    reedSolomonService.createParityGroup(new ArrayList<>(pendingParityChunkHashes));
                    parityGroupsCreated.incrementAndGet();
                    pendingParityChunkHashes.clear();
                } catch (IOException e) {
                    logger.error("Failed to create final parity group", e);
                }
            }

        } catch (IOException e) {
            logger.error("Failed to stream chunks for integrity check", e);
        }

        long duration = System.currentTimeMillis() - startTime;
        logger.info("Integrity check completed in {} ms. Checked: {}, Failed: {}, Parity Groups Created: {}",
                duration, checkedCount.get(), failedCount.get(), parityGroupsCreated.get());

        return new IntegrityCheckResult(checkedCount.get(), failedCount.get(), parityGroupsCreated.get(), duration);
    }

    public static class IntegrityCheckResult {
        public final long checkedChunks;
        public final long failedChunks;
        public final long parityGroupsCreated;
        public final long durationMs;

        public IntegrityCheckResult(long checkedChunks, long failedChunks, long parityGroupsCreated, long durationMs) {
            this.checkedChunks = checkedChunks;
            this.failedChunks = failedChunks;
            this.parityGroupsCreated = parityGroupsCreated;
            this.durationMs = durationMs;
        }
    }
}
