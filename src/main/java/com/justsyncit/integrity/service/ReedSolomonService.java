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
    public ReedSolomonService(MetadataService metadataService, ChunkStorage chunkStore, int dataShards,
            int parityShards) {
        this.metadataService = metadataService;
        this.chunkStore = chunkStore;
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
        // We use arrays for the RS codec
        byte[][] shards = new byte[dataShards + parityShards][(int) maxSize];

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
            System.arraycopy(data, 0, shards[i], 0, data.length);
            // Remaining bytes in shards[i] are already 0
        }

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
            // We store parity chunks as regular chunks?
            // Yes, storing them allows deduplication if parity happens to be identical
            // (unlikely but possible)
            // and leverages existing storage mechanism.
            // We need to calculate hash for parity chunk
            // Wait, chunkStore.storeChunk calculates hash.

            // NOTE: Store parity chunk. It returns a hash.
            // In a real system, we might mark these as "system" chunks to avoid garbage
            // collection if they are not referenced by a file.
            // But here we rely on existing mechanisms. Parity chunks are referenced by
            // chunk_parity table.
            // Ideally we should increment ref count or have GC know about chunk_parity.

            String parityHash = chunkStore.storeChunk(parityData);
            System.out.println(
                    "DEBUG: createParityGroup - Created parity index " + (dataShards + i) + " hash " + parityHash);

            // Register parity chunk in metadata
            ChunkMetadata parityMeta = new ChunkMetadata(
                    parityHash,
                    parityData.length,
                    java.time.Instant.now(),
                    1, // Ref count 1 (referenced by parity group)
                    java.time.Instant.now());
            metadataService.upsertChunk(parityMeta);

            metadataService.addChunkToParityGroup(groupId, parityHash, dataShards + i, true);
        }

        return groupId;
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
                System.out.println("DEBUG: repairChunk - group meta missing");
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

            byte[][] shards = new byte[dataShards + parityShards][(int) maxSize];
            boolean[] shardPresent = new boolean[dataShards + parityShards];

            for (ChunkParityEntry e : allEntries) {
                int idx = e.getChunkIndex();
                boolean isTarget = e.getChunkHash().equals(missingChunkHash);
                System.out.println("DEBUG: repairChunk - processing index " + idx + " hash " + e.getChunkHash()
                        + " isTarget=" + isTarget);

                if (!isTarget) {
                    byte[] data;
                    try {
                        data = chunkStore.retrieveChunk(e.getChunkHash());
                        if (data != null) {
                            System.arraycopy(data, 0, shards[idx], 0, data.length);
                            shardPresent[idx] = true;
                            System.out
                                    .println("DEBUG: repairChunk - Loaded " + data.length + " bytes for index " + idx);
                        }
                    } catch (StorageIntegrityException ex) {
                        logger.warn("Sibling chunk {} is corrupt, treating as missing for repair.", e.getChunkHash());
                    }
                }
            }

            int presentCount = 0;
            for (boolean p : shardPresent)
                if (p)
                    presentCount++;

            System.out.println("DEBUG: repairChunk - present shards: " + presentCount + " needed: " + dataShards);

            if (presentCount < dataShards) {
                logger.error("Not enough shards to recover chunk {}. Need {}, have {}.", missingChunkHash, dataShards,
                        presentCount);
                return false;
            }

            // 3. Decode
            ReedSolomon rs = new ReedSolomon(dataShards, parityShards);
            rs.decodeMissing(shards, shardPresent, (int) maxSize);

            // 4. Save recovered chunk
            int targetIdx = targetEntry.getChunkIndex();
            byte[] recoveredData = shards[targetIdx];

            Optional<ChunkMetadata> targetMeta = metadataService.getChunkMetadata(missingChunkHash);
            if (targetMeta.isPresent()) {
                int originalSize = (int) targetMeta.get().getSize();
                if (originalSize < maxSize) {
                    byte[] truncated = new byte[originalSize];
                    System.arraycopy(recoveredData, 0, truncated, 0, originalSize);
                    recoveredData = truncated;
                }
            }

            // Important: We must delete the corrupt chunk first to ensure we overwrite it,
            // bypassing any deduplication checks in the content store.
            chunkStore.deleteChunk(missingChunkHash);

            String recoveredHash = chunkStore.storeChunk(recoveredData);
            System.out.println("DEBUG: repairChunk - stored recovered data. Hash: " + recoveredHash + " Expected: "
                    + missingChunkHash);

            logger.info("Successfully repaired chunk {}", missingChunkHash);
            return true;

        } catch (Exception e) {
            logger.error("Failed to repair chunk " + missingChunkHash, e);
            e.printStackTrace();
            System.out.println("DEBUG: repairChunk - exception: " + e.getMessage());
            return false;
        }
    }
}
