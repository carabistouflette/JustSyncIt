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

package com.justsyncit.network.protocol;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;

/**
 * Chunk data message containing a file chunk.
 * Contains chunk metadata and the actual chunk data.
 */
public class ChunkDataMessage extends AbstractProtocolMessage {

    /** The file path for the chunk. */
    private final String filePath;
    /** The offset of the chunk in the file. */
    private final long chunkOffset;
    /** The size of the chunk in bytes. */
    private final int chunkSize;
    /** The total size of the file. */
    private final long totalFileSize;
    /** The BLAKE3 hash of the chunk. */
    private final String chunkHash;
    /** The chunk data. */
    private final byte[] chunkData;

    /**
     * Creates a new chunk data message.
     *
     * @param filePath the file path
     * @param chunkOffset the offset of this chunk in the file
     * @param chunkSize the size of this chunk
     * @param totalFileSize the total file size
     * @param chunkHash the BLAKE3 hash of this chunk
     * @param chunkData the chunk data
     */
    public ChunkDataMessage(String filePath, long chunkOffset, int chunkSize, long totalFileSize,
                           String chunkHash, byte[] chunkData) {
        super(MessageType.CHUNK_DATA, ProtocolConstants.Flags.ACK_REQUIRED);
        this.filePath = Objects.requireNonNull(filePath, "filePath cannot be null");
        this.chunkOffset = chunkOffset;
        this.chunkSize = chunkSize;
        this.totalFileSize = totalFileSize;
        this.chunkHash = Objects.requireNonNull(chunkHash, "chunkHash cannot be null");
        this.chunkData = Objects.requireNonNull(chunkData, "chunkData cannot be null");
    }

    /**
     * Creates a chunk data message from serialized data.
     *
     * @param buffer the byte buffer containing the serialized message
     * @param messageId the message ID
     * @return the deserialized chunk data message
     */
    public static ChunkDataMessage deserialize(ByteBuffer buffer, int messageId) {
        Objects.requireNonNull(buffer, "buffer cannot be null");

        String filePath = readString(buffer);
        long chunkOffset = buffer.getLong();
        int chunkSize = buffer.getInt();
        long totalFileSize = buffer.getLong();
        String chunkHash = readString(buffer);

        byte[] chunkData = new byte[chunkSize];
        buffer.get(chunkData);

        return new ChunkDataMessage(filePath, chunkOffset, chunkSize, totalFileSize, chunkHash, chunkData, messageId);
    }

    /**
     * Creates a chunk data message with specified message ID.
     *
     * @param filePath the file path
     * @param chunkOffset the offset of this chunk in the file
     * @param chunkSize the size of this chunk
     * @param totalFileSize the total file size
     * @param chunkHash the BLAKE3 hash of this chunk
     * @param chunkData the chunk data
     * @param messageId the message ID
     */
    private ChunkDataMessage(String filePath, long chunkOffset, int chunkSize, long totalFileSize,
                            String chunkHash, byte[] chunkData, int messageId) {
        super(MessageType.CHUNK_DATA, ProtocolConstants.Flags.ACK_REQUIRED, messageId);
        this.filePath = filePath;
        this.chunkOffset = chunkOffset;
        this.chunkSize = chunkSize;
        this.totalFileSize = totalFileSize;
        this.chunkHash = chunkHash;
        this.chunkData = chunkData;
    }

    /**
     * Gets the file path.
     *
     * @return the file path
     */
    public String getFilePath() {
        return filePath;
    }

    /**
     * Gets the chunk offset.
     *
     * @return the chunk offset in bytes
     */
    public long getChunkOffset() {
        return chunkOffset;
    }

    /**
     * Gets the chunk size.
     *
     * @return the chunk size in bytes
     */
    public int getChunkSize() {
        return chunkSize;
    }

    /**
     * Gets the total file size.
     *
     * @return the total file size in bytes
     */
    public long getTotalFileSize() {
        return totalFileSize;
    }

    /**
     * Gets the chunk hash.
     *
     * @return the BLAKE3 hash of this chunk
     */
    public String getChunkHash() {
        return chunkHash;
    }

    /**
     * Gets the chunk data.
     *
     * @return the chunk data
     */
    public byte[] getChunkData() {
        return chunkData.clone(); // Return a copy for safety
    }

    /**
     * Checks if this is the last chunk of the file.
     *
     * @return true if this is the last chunk, false otherwise
     */
    public boolean isLastChunk() {
        return chunkOffset + chunkSize >= totalFileSize;
    }

    @Override
    public ByteBuffer serializePayload() {
        int payloadSize = getPayloadSize();
        ByteBuffer buffer = ByteBuffer.allocate(payloadSize);

        writeString(buffer, filePath);
        buffer.putLong(chunkOffset);
        buffer.putInt(chunkSize);
        buffer.putLong(totalFileSize);
        writeString(buffer, chunkHash);
        buffer.put(chunkData);

        buffer.flip();
        return buffer;
    }

    @Override
    public int getPayloadSize() {
        return calculateStringSize(filePath) + 8 + 4 + 8 + calculateStringSize(chunkHash) + chunkData.length;
        // filePath + chunkOffset(8) + chunkSize(4) + totalFileSize(8) + chunkHash + chunkData
    }

    @Override
    public boolean isValid() {
        return super.isValid()
                && !filePath.isEmpty()
                && filePath.length() <= ProtocolConstants.MAX_PATH_LENGTH
                && chunkOffset >= 0
                && chunkSize > 0
                && chunkSize <= ProtocolConstants.MAX_CHUNK_SIZE
                && totalFileSize > 0
                && totalFileSize <= ProtocolConstants.MAX_FILE_SIZE
                && chunkHash.length() == 64 // BLAKE3 hash is 64 hex characters
                && chunkData != null
                && chunkData.length == chunkSize
                && chunkOffset + chunkSize <= totalFileSize;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        if (!super.equals(obj)) {
            return false;
        }

        ChunkDataMessage that = (ChunkDataMessage) obj;
        return chunkOffset == that.chunkOffset
                && chunkSize == that.chunkSize
                && totalFileSize == that.totalFileSize
                && filePath.equals(that.filePath)
                && chunkHash.equals(that.chunkHash)
                && Arrays.equals(chunkData, that.chunkData);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(super.hashCode(), filePath, chunkOffset, chunkSize, totalFileSize, chunkHash);
        result = 31 * result + Arrays.hashCode(chunkData);
        return result;
    }

    @Override
    public String toString() {
        return String.format("ChunkDataMessage{path='%s', offset=%d, size=%d, totalSize=%d, hash='%s', "
                           + "dataLength=%d, %s}",
                           filePath, chunkOffset, chunkSize, totalFileSize, chunkHash,
                           chunkData.length, super.toString());
    }
}