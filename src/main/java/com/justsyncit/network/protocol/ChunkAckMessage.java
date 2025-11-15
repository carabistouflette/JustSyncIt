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
import java.util.Objects;

/**
 * Chunk acknowledgment message for confirming chunk receipt.
 * Contains chunk information and verification status.
 */
public class ChunkAckMessage extends AbstractProtocolMessage {

    /** The file path for the chunk. */
    private final String filePath;
    /** The offset of the chunk in the file. */
    private final long chunkOffset;
    /** The size of the chunk in bytes. */
    private final int chunkSize;
    /** Whether the checksum is valid. */
    private final boolean checksumValid;
    /** The error message, if any. */
    private final String errorMessage;

    /**
     * Creates a new chunk acknowledgment message for successful receipt.
     *
     * @param filePath the file path
     * @param chunkOffset the chunk offset
     * @param chunkSize the chunk size
     */
    public ChunkAckMessage(String filePath, long chunkOffset, int chunkSize) {
        this(filePath, chunkOffset, chunkSize, true, null);
    }

    /**
     * Creates a new chunk acknowledgment message for failed verification.
     *
     * @param filePath the file path
     * @param chunkOffset the chunk offset
     * @param chunkSize the chunk size
     * @param errorMessage the error message
     */
    public ChunkAckMessage(String filePath, long chunkOffset, int chunkSize, String errorMessage) {
        this(filePath, chunkOffset, chunkSize, false, errorMessage);
    }

    /**
     * Creates a new chunk acknowledgment message.
     *
     * @param filePath the file path
     * @param chunkOffset the chunk offset
     * @param chunkSize the chunk size
     * @param checksumValid whether the checksum is valid
     * @param errorMessage the error message (null if successful)
     */
    private ChunkAckMessage(String filePath, long chunkOffset, int chunkSize,
                           boolean checksumValid, String errorMessage) {
        super(MessageType.CHUNK_ACK, ProtocolConstants.Flags.RESPONSE);
        this.filePath = Objects.requireNonNull(filePath, "filePath cannot be null");
        this.chunkOffset = chunkOffset;
        this.chunkSize = chunkSize;
        this.checksumValid = checksumValid;
        this.errorMessage = errorMessage != null ? errorMessage : "";
    }

    /**
     * Creates a chunk acknowledgment message from serialized data.
     *
     * @param buffer the byte buffer containing the serialized message
     * @param messageId the message ID
     * @return the deserialized chunk acknowledgment message
     */
    public static ChunkAckMessage deserialize(ByteBuffer buffer, int messageId) {
        Objects.requireNonNull(buffer, "buffer cannot be null");

        String filePath = readString(buffer);
        long chunkOffset = buffer.getLong();
        int chunkSize = buffer.getInt();
        boolean checksumValid = buffer.get() != 0;
        String errorMessage = readString(buffer);

        return new ChunkAckMessage(filePath, chunkOffset, chunkSize, checksumValid, errorMessage, messageId);
    }

    /**
     * Creates a chunk acknowledgment message with specified message ID.
     *
     * @param filePath the file path
     * @param chunkOffset the chunk offset
     * @param chunkSize the chunk size
     * @param checksumValid whether the checksum is valid
     * @param errorMessage the error message
     * @param messageId the message ID
     */
    private ChunkAckMessage(String filePath, long chunkOffset, int chunkSize,
                           boolean checksumValid, String errorMessage, int messageId) {
        super(MessageType.CHUNK_ACK, ProtocolConstants.Flags.RESPONSE, messageId);
        this.filePath = filePath;
        this.chunkOffset = chunkOffset;
        this.chunkSize = chunkSize;
        this.checksumValid = checksumValid;
        this.errorMessage = errorMessage;
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
     * Checks if the checksum is valid.
     *
     * @return true if the checksum is valid, false otherwise
     */
    public boolean isChecksumValid() {
        return checksumValid;
    }

    /**
     * Gets the error message.
     *
     * @return the error message
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * Checks if this is a successful acknowledgment.
     *
     * @return true if successful, false otherwise
     */
    public boolean isSuccess() {
        return checksumValid && errorMessage.isEmpty();
    }

    @Override
    public ByteBuffer serializePayload() {
        ByteBuffer buffer = ByteBuffer.allocate(getPayloadSize());

        writeString(buffer, filePath);
        buffer.putLong(chunkOffset);
        buffer.putInt(chunkSize);
        buffer.put((byte) (checksumValid ? 1 : 0));
        writeString(buffer, errorMessage);

        buffer.flip();
        return buffer;
    }

    @Override
    public int getPayloadSize() {
        return calculateStringSize(filePath) + 8 + 4 + 1 + calculateStringSize(errorMessage);
        // filePath + chunkOffset(8) + chunkSize(4) + checksumValid(1) + errorMessage
    }

    @Override
    public boolean isValid() {
        return super.isValid()
                && !filePath.isEmpty()
                && filePath.length() <= ProtocolConstants.MAX_PATH_LENGTH
                && chunkOffset >= 0
                && chunkSize > 0
                && chunkSize <= ProtocolConstants.MAX_CHUNK_SIZE
                && errorMessage.length() <= 1024;
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

        ChunkAckMessage that = (ChunkAckMessage) obj;
        return chunkOffset == that.chunkOffset
                && chunkSize == that.chunkSize
                && checksumValid == that.checksumValid
                && filePath.equals(that.filePath)
                && errorMessage.equals(that.errorMessage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), filePath, chunkOffset, chunkSize, checksumValid, errorMessage);
    }

    @Override
    public String toString() {
        return String.format("ChunkAckMessage{path='%s', offset=%d, size=%d, checksumValid=%s, error='%s', %s}",
                           filePath, chunkOffset, chunkSize, checksumValid, errorMessage, super.toString());
    }
}