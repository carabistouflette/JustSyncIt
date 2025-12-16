package com.justsyncit.backup.cbt;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.BitSet;

/**
 * Represents a bitmap of changed blocks for a file.
 * Uses a BitSet to track 4KB blocks.
 * Supports RLE compression for storage efficiency.
 */
public class BlockBitmap {

    private static final int BLOCK_SIZE = 4096; // 4KB

    private final BitSet changedBlocks;
    private final long fileSize;
    private final int totalBlocks;

    /**
     * Creates a new BlockBitmap for a file of the given size.
     * All blocks are initially marked as unchanged (false).
     *
     * @param fileSize size of the file in bytes
     */
    public BlockBitmap(long fileSize) {
        if (fileSize < 0) {
            throw new IllegalArgumentException("File size cannot be negative");
        }
        this.fileSize = fileSize;
        this.totalBlocks = (int) Math.ceil((double) fileSize / BLOCK_SIZE);
        this.changedBlocks = new BitSet(totalBlocks);
    }

    /**
     * Private constructor for deserialization.
     */
    private BlockBitmap(long fileSize, BitSet changedBlocks) {
        this.fileSize = fileSize;
        this.totalBlocks = (int) Math.ceil((double) fileSize / BLOCK_SIZE);
        this.changedBlocks = changedBlocks;
    }

    /**
     * Marks a specific block as changed.
     *
     * @param blockIndex index of the block (0-based)
     */
    public void markChanged(int blockIndex) {
        checkBounds(blockIndex);
        changedBlocks.set(blockIndex);
    }

    /**
     * Marks a range of bytes as changed.
     * Calculates which blocks are affected and marks them.
     *
     * @param startOffset start offset in bytes
     * @param length      length in bytes
     */
    public void markRangeChanged(long startOffset, long length) {
        if (startOffset < 0 || length < 0 || startOffset + length > fileSize) {
            throw new IndexOutOfBoundsException(
                    "Invalid range: start=" + startOffset + ", length=" + length + ", fileSize=" + fileSize);
        }

        int startBlock = (int) (startOffset / BLOCK_SIZE);
        int endBlock = (int) ((startOffset + length - 1) / BLOCK_SIZE);

        changedBlocks.set(startBlock, endBlock + 1);
    }

    /**
     * Checks if a block is marked as changed.
     *
     * @param blockIndex index of the block
     * @return true if changed, false otherwise
     */
    public boolean isChanged(int blockIndex) {
        checkBounds(blockIndex);
        return changedBlocks.get(blockIndex);
    }

    /**
     * Marks all blocks as changed (full backup needed).
     */
    public void markAllChanged() {
        changedBlocks.set(0, totalBlocks);
    }

    /**
     * Clears pending changes.
     */
    public void clear() {
        changedBlocks.clear();
    }

    /**
     * Gets the total number of blocks.
     *
     * @return total blocks
     */
    public int getTotalBlocks() {
        return totalBlocks;
    }

    /**
     * Serializes this bitmap to a byte array using RLE compression.
     * Format:
     * [8 bytes] File Size
     * [4 bytes] Run Count
     * [Run items...]
     * Each run: [1 byte] boolean state (0/1), [4 bytes] length
     *
     * @return serialized bytes
     */
    public byte[] serialize() {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                DataOutputStream dos = new DataOutputStream(baos)) {

            dos.writeLong(fileSize);

            // Simple RLE encoding
            if (totalBlocks == 0) {
                dos.writeInt(0);
                return baos.toByteArray();
            }

            // Calculate runs first to write count (could be optimized)
            // Or just write runs until end. Let's write count for safety.
            // Actually, BitSet usually has sparse data, let's just write the longs from
            // BitSet?
            // Requirement says "RLE compression". Let's do a simple run-length
            // implementation.

            // To do this efficiently without two passes, we write to a temp buffer or just
            // trust the stream.
            // Let's use a temporary buffer for the runs.
            ByteArrayOutputStream runStream = new ByteArrayOutputStream();
            DataOutputStream runDos = new DataOutputStream(runStream);

            int runCount = 0;
            boolean currentState = changedBlocks.get(0);
            int currentRunLength = 0;

            for (int i = 0; i < totalBlocks; i++) {
                boolean state = changedBlocks.get(i);
                if (state == currentState) {
                    currentRunLength++;
                } else {
                    writeRun(runDos, currentState, currentRunLength);
                    runCount++;
                    currentState = state;
                    currentRunLength = 1;
                }
            }
            // Write final run
            writeRun(runDos, currentState, currentRunLength);
            runCount++;

            dos.writeInt(runCount);
            dos.write(runStream.toByteArray());

            return baos.toByteArray();

        } catch (IOException e) {
            throw new RuntimeException("Serialization failed, should not happen in memory", e);
        }
    }

    private void writeRun(DataOutputStream dos, boolean state, int length) throws IOException {
        dos.writeBoolean(state);
        dos.writeInt(length);
    }

    /**
     * Deserializes a bitmap from a byte array.
     *
     * @param data serialized data
     * @return BlockBitmap instance
     */
    public static BlockBitmap deserialize(byte[] data) {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
                DataInputStream dis = new DataInputStream(bais)) {

            long fileSize = dis.readLong();
            int runCount = dis.readInt();

            BitSet blocks = new BitSet();
            int currentBlockIndex = 0;

            for (int i = 0; i < runCount; i++) {
                boolean state = dis.readBoolean();
                int length = dis.readInt();

                if (state) {
                    blocks.set(currentBlockIndex, currentBlockIndex + length);
                }
                currentBlockIndex += length;
            }

            return new BlockBitmap(fileSize, blocks);

        } catch (IOException e) {
            throw new RuntimeException("Deserialization failed", e);
        }
    }

    private void checkBounds(int index) {
        if (index < 0 || index >= totalBlocks) {
            throw new IndexOutOfBoundsException(
                    "Block index " + index + " out of bounds for " + totalBlocks + " blocks");
        }
    }

    @Override
    public String toString() {
        return "BlockBitmap{size=" + fileSize + ", blocks=" + totalBlocks + ", changed=" + changedBlocks.cardinality()
                + "}";
    }
}
