package com.justsyncit.integrity.service;

import com.justsyncit.integrity.rs.ReedSolomon;
import com.justsyncit.storage.ChunkStorage;
import com.justsyncit.storage.StorageIntegrityException;
import com.justsyncit.storage.metadata.ChunkMetadata;
import com.justsyncit.storage.metadata.ChunkParityEntry;
import com.justsyncit.storage.metadata.MetadataService;
import com.justsyncit.storage.metadata.ParityGroupMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service orchestrating Reed-Solomon parity generation and data recovery.
 * Handles variable-sized chunks by zero-padding to the maximum size in the
 * group.
 */
public class ReedSolomonService {
    private static final Logger logger = LoggerFactory.getLogger(ReedSolomonService.class);

    private final MetadataService metadataService;
    private final ChunkStorage chunkStore;
    private final com.justsyncit.hash.Blake3Service blake3Service;
    private final int dataShards;
    private final int parityShards;

    /**
     * Creates a new ReedSolomonService.
     *
     * @param metadataService the metadata service
     * @param chunkStore      the chunk store
     * @param dataShards      number of data shards per group (k)
     * @param parityShards    number of parity shards per group (m)
     */
    public ReedSolomonService(MetadataService metadataService, ChunkStorage chunkStore,
            com.justsyncit.hash.Blake3Service blake3Service, int dataShards,
            int parityShards) {
        this.metadataService = metadataService;
        this.chunkStore = chunkStore;
        this.blake3Service = blake3Service;
        this.dataShards = dataShards;
        this.parityShards = parityShards;
    }

    /**
     * Creates a parity group for the given list of data chunks.
     * The input chunks must already be stored in the ChunkStore and
     * MetadataService.
     *
     * @param dataChunkHashes list of chunk hashes to protect. Must be of size equal
     *                        to or less than dataShards.
     *                        If less, the remaining slots are considered
     *                        zero-filled virtual chunks.
     * @return the ID of the created parity group
     * @throws IOException if an error occurs
     */
    public long createParityGroup(List<String> dataChunkHashes) throws IOException {
        if (dataChunkHashes.isEmpty()) {
            throw new IllegalArgumentException("Data chunks list cannot be empty");
        }
        if (dataChunkHashes.size() > dataShards) {
            throw new IllegalArgumentException("Too many chunks for parity group. Max: " + dataShards);
        }

        // 1. Fetch metadata to determine max size
        List<ChunkMetadata> metas = new ArrayList<>();
        long maxSize = 0;
        for (String hash : dataChunkHashes) {
            Optional<ChunkMetadata> m = metadataService.getChunkMetadata(hash);
            if (m.isEmpty()) {
                throw new IOException("Chunk not found in metadata: " + hash);
            }
            metas.add(m.get());
            if (m.get().getSize() > maxSize) {
                maxSize = m.get().getSize();
            }
        }

        // Ensure even size for alignment if needed, or just standard 4-byte align could
        // be good
        // RS code doesn't strictly require alignment but it's good practice.
        // Our RS impl works on bytes, so any size is fine.

        // 2. Load data into buffers
        byte[][] shards = new byte[dataShards + parityShards][];
        try {
            for (int i = 0; i < shards.length; i++) {
                shards[i] = ShardBufferPool.getInstance().acquire();
            }

            for (int i = 0; i < dataChunkHashes.size(); i++) {
                ChunkMetadata meta = metas.get(i);
                byte[] data;
                try {
                    data = chunkStore.retrieveChunk(meta.getHash()); // Assuming retrieving raw bytes
                } catch (StorageIntegrityException e) {
                    throw new IOException("Cannot create parity group: source chunk is corrupt: " + meta.getHash(), e);
                }
                if (data == null) {
                    throw new IOException("Chunk data missing from store: " + meta.getHash());
                }
                // Zero out the buffer first if we are reusing it.
                // We must ensure that any bytes between data.length and maxSize are 0,
                // as correct Reed-Solomon encoding depends on this padding being effectively
                // "null".
                if (data.length < maxSize) {
                    java.util.Arrays.fill(shards[i], data.length, (int) maxSize, (byte) 0);
                }

                System.arraycopy(data, 0, shards[i], 0, data.length);
            }

            // For parity shards (starting at dataShards), the RS encoder will overwrite
            // them.
            // We do not need to pre-clean them as long as we treat them as write-only
            // output.
            // (Backblaze RS implementation overwrites output shards).

            // 3. Compute Parity
            ReedSolomon rs = new ReedSolomon(dataShards, parityShards);
            rs.encodeParity(shards, (int) maxSize);

            // 4. Store Parity Chunks
            String algoId = "RS-" + dataShards + "-" + parityShards;
            long groupId = metadataService.createParityGroup(algoId);

            // Register data chunks
            for (int i = 0; i < dataChunkHashes.size(); i++) {
                metadataService.addChunkToParityGroup(groupId, dataChunkHashes.get(i), i, false);
            }

            // Store and register parity chunks
            for (int i = 0; i < parityShards; i++) {
                byte[] parityData = shards[dataShards + i]; // Parity starts at index k
                // We need to slice/copy only the relevant bytes for storage
                byte[] dataToStore = new byte[(int) maxSize];
                System.arraycopy(parityData, 0, dataToStore, 0, (int) maxSize);

                String parityHash = chunkStore.storeChunk(dataToStore);
                logger.debug("Created parity index {} hash {}", dataShards + i, parityHash);

                // Register parity chunk in metadata
                ChunkMetadata parityMeta = new ChunkMetadata(
                        parityHash,
                        dataToStore.length,
                        java.time.Instant.now(),
                        1, // Ref count 1 (referenced by parity group)
                        java.time.Instant.now());
                metadataService.upsertChunk(parityMeta);

                metadataService.addChunkToParityGroup(groupId, parityHash, dataShards + i, true);
            }

            return groupId;
        } finally {
            // Return buffers to pool
            for (byte[] shard : shards) {
                if (shard != null) {
                    ShardBufferPool.getInstance().release(shard);
                }
            }
        }
    }

    /**
     * Attempts to repair a missing chunk using its parity group.
     *
     * @param missingChunkHash the hash of the chunk to repair
     * @return true if repair was successful
     */
    public boolean repairChunk(String missingChunkHash) {
        try {
            Optional<ChunkParityEntry> entryOpt = metadataService.getChunkParityEntry(missingChunkHash);
            if (entryOpt.isEmpty()) {
                logger.warn("Chunk {} is not part of any parity group.", missingChunkHash);
                return false;
            }
            ChunkParityEntry targetEntry = entryOpt.get();
            long groupId = targetEntry.getGroupId();

            List<ChunkParityEntry> allEntries = metadataService.getChunksInParityGroup(groupId);

            Optional<ParityGroupMetadata> groupMeta = metadataService.getParityGroup(groupId);
            if (groupMeta.isEmpty()) {
                logger.warn("Parity group metadata missing for group {}", groupId);
                return false;
            }

            long maxSize = 0;
            ChunkMetadata[] metas = new ChunkMetadata[dataShards + parityShards];

            for (ChunkParityEntry e : allEntries) {
                Optional<ChunkMetadata> m = metadataService.getChunkMetadata(e.getChunkHash());
                if (m.isPresent()) {
                    metas[e.getChunkIndex()] = m.get();
                    if (m.get().getSize() > maxSize)
                        maxSize = m.get().getSize();
                }
            }

            byte[][] shards = new byte[dataShards + parityShards][];
            boolean[] shardPresent = new boolean[dataShards + parityShards];

            try {
                for (int i = 0; i < shards.length; i++) {
                    shards[i] = ShardBufferPool.getInstance().acquire();
                }

                for (ChunkParityEntry e : allEntries) {
                    int idx = e.getChunkIndex();
                    boolean isTarget = e.getChunkHash().equals(missingChunkHash);
                    logger.trace("Processing index {} hash {} isTarget={}", idx, e.getChunkHash(), isTarget);

                    if (!isTarget) {
                        byte[] data;
                        try {
                            data = chunkStore.retrieveChunk(e.getChunkHash());
                            if (data != null) {
                                // Zero out padding if reusing buffer
                                if (data.length < maxSize) {
                                    java.util.Arrays.fill(shards[idx], data.length, (int) maxSize, (byte) 0);
                                }
                                System.arraycopy(data, 0, shards[idx], 0, data.length);
                                shardPresent[idx] = true;
                                logger.trace("Loaded {} bytes for index {}", data.length, idx);
                            }
                        } catch (StorageIntegrityException ex) {
                            logger.warn("Sibling chunk {} is corrupt, treating as missing for repair.",
                                    e.getChunkHash());
                        }
                    }
                }

                int presentCount = 0;
                for (boolean p : shardPresent)
                    if (p)
                        presentCount++;

                logger.debug("Present shards: {} needed: {}", presentCount, dataShards);

                if (presentCount < dataShards) {
                    logger.error("Not enough shards to recover chunk {}. Need {}, have {}.", missingChunkHash,
                            dataShards,
                            presentCount);
                    return false;
                }

                // 3. Decode
                ReedSolomon rs = new ReedSolomon(dataShards, parityShards);
                rs.decodeMissing(shards, shardPresent, (int) maxSize);

                // 4. Verify & Save recovered chunk
                int targetIdx = targetEntry.getChunkIndex();
                byte[] recoveredBuffer = shards[targetIdx];

                // Extract actual data
                byte[] recoveredData = new byte[(int) maxSize];
                System.arraycopy(recoveredBuffer, 0, recoveredData, 0, (int) maxSize);

                Optional<ChunkMetadata> targetMeta = metadataService.getChunkMetadata(missingChunkHash);
                if (targetMeta.isPresent()) {
                    int originalSize = (int) targetMeta.get().getSize();
                    if (originalSize < maxSize) {
                        byte[] truncated = new byte[originalSize];
                        System.arraycopy(recoveredData, 0, truncated, 0, originalSize);
                        recoveredData = truncated;
                    }
                }

                try {
                    if (!blake3Service.verify(recoveredData, missingChunkHash)) {
                        logger.error(
                                "Healing verification failed! Recovered data hash DOES NOT match expected hash {}.",
                                missingChunkHash);
                        return false;
                    }
                } catch (Exception e) {
                    logger.error("Error during healing verification for {}", missingChunkHash, e);
                    return false;
                }

                // Important: We must delete the corrupt chunk first to ensure we overwrite it,
                // bypassing any deduplication checks in the content store.
                // NOW SAFE: Verified that we have the CORRECT data.
                chunkStore.deleteChunk(missingChunkHash);

                String recoveredHash = chunkStore.storeChunk(recoveredData);
                logger.debug("Stored recovered data. Hash: {} Expected: {}", recoveredHash, missingChunkHash);

                if (!recoveredHash.equals(missingChunkHash)) {
                    logger.error("Post-storage verification failed. Stored hash {} != Expected {}", recoveredHash,
                            missingChunkHash);
                    return false;
                }

                logger.info("Successfully repaired chunk {}", missingChunkHash);
                return true;
            } finally {
                for (byte[] shard : shards) {
                    if (shard != null) {
                        ShardBufferPool.getInstance().release(shard);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Failed to repair chunk " + missingChunkHash, e);
            // Stack trace logged above with logger.error
            return false;
        }
    }
}
