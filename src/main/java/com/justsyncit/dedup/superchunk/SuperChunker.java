package com.justsyncit.dedup.superchunk;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * Groups basic chunks into larger "SuperChunks" to optimize indexing and
 * lookup.
 * A SuperChunk represents a sequence of chunks.
 */
public class SuperChunker {

    private final int averageSuperChunkSize; // Target size in number of chunks or bytes? Let's say bytes.
    // Or simpler: Fixed number of chunks?
    // Let's use content-defined super-chunking too? Or keep it simple: Fixed count
    // of chunks (e.g. 10).
    // Better: variable size based on hash (like CDC but on chunk hashes).
    // For simplicity in this iteration: Fixed number of chunks to form a SuperChunk
    // feature,
    // OR rolling hash over chunk hashes.

    // Let's go with: Group N chunks.
    private static final int DEFAULT_CHUNKS_PER_SUPER = 16;

    public SuperChunker() {
        this.averageSuperChunkSize = DEFAULT_CHUNKS_PER_SUPER;
    }

    public int getTargetSize() {
        return averageSuperChunkSize;
    }

    public static class SuperChunk {
        private final String signature; // Hash of the concatenated chunk hashes
        private final List<String> childChunkHashes;
        private final int startIndex;

        public SuperChunk(String signature, List<String> childChunkHashes, int startIndex) {
            this.signature = signature;
            this.childChunkHashes = childChunkHashes;
            this.startIndex = startIndex;
        }

        public String getSignature() {
            return signature;
        }

        public List<String> getChildChunkHashes() {
            return childChunkHashes;
        }

        public int getStartIndex() {
            return startIndex;
        }
    }

    /**
     * Create super-chunks from a list of chunk hashes.
     */
    public List<SuperChunk> createSuperChunks(List<String> chunkHashes) {
        List<SuperChunk> superChunks = new ArrayList<>();
        if (chunkHashes == null || chunkHashes.isEmpty()) {
            return superChunks;
        }

        // Simple tiling for now: Every N chunks is a super chunk.
        // Enhance: Rolling hash on chunk hashes to find boundaries?
        // Let's stick to simple fixed grouping for "v1" unless robustness requires it.
        // Fixed grouping is sensitive to insertions (shift problem again).
        // Since we solved shift at CDC level, fixed grouping of chunks is still
        // sensitive if a chunk is inserted.
        // Ideally we should apply CDC logic on the stream of Chunk Hashes!
        // That is "Layer 2" CDC.

        // Let's implement variable grouping based on hash of chunk hash.
        // Boundary if hash(chunkHash) % DIVISOR == 0.

        int divisor = DEFAULT_CHUNKS_PER_SUPER;
        List<String> currentBatch = new ArrayList<>();
        int startIdx = 0;

        for (int i = 0; i < chunkHashes.size(); i++) {
            String h = chunkHashes.get(i);
            currentBatch.add(h);

            // "CDC" on hashes: check if this hash determines a boundary
            // We use hashCode() for speed, good enough for distribution
            if (h.hashCode() % divisor == 0 || currentBatch.size() >= divisor * 2) {
                // Boundary found or forced max size
                createAndAdd(superChunks, currentBatch, startIdx);
                currentBatch = new ArrayList<>();
                startIdx = i + 1;
            }
        }

        if (!currentBatch.isEmpty()) {
            createAndAdd(superChunks, currentBatch, startIdx);
        }

        return superChunks;
    }

    private void createAndAdd(List<SuperChunk> result, List<String> batch, int startIdx) {
        if (batch.isEmpty())
            return;

        // Compute super-hash: Hash of concatenated hashes
        StringBuilder sb = new StringBuilder();
        for (String s : batch)
            sb.append(s);
        String superHash = hash(sb.toString());

        result.add(new SuperChunk(superHash, new ArrayList<>(batch), startIdx));
    }

    private String hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            // Hex encode
            StringBuilder hexString = new StringBuilder();
            for (byte b : digest) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1)
                    hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
