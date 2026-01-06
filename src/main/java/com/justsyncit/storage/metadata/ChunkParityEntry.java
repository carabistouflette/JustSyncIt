package com.justsyncit.storage.metadata;

/**
 * Represents a chunk's membership in a parity group.
 */
public final class ChunkParityEntry {
    private final long groupId;
    private final String chunkHash;
    private final int chunkIndex;
    private final boolean parity;

    public ChunkParityEntry(long groupId, String chunkHash, int chunkIndex, boolean parity) {
        this.groupId = groupId;
        this.chunkHash = chunkHash;
        this.chunkIndex = chunkIndex;
        this.parity = parity;
    }

    public long getGroupId() {
        return groupId;
    }

    public String getChunkHash() {
        return chunkHash;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public boolean isParity() {
        return parity;
    }

    @Override
    public String toString() {
        return "ChunkParity{group=" + groupId + ", hash=" + chunkHash + ", idx=" + chunkIndex + ", parity=" + parity
                + "}";
    }
}
