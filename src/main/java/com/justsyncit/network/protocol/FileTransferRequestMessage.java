/*
 * JustSyncIt - Backup solution
 * Copyright (C) 2023 JustSyncIt Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.justsyncit.network.protocol;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Objects;

/**
 * File transfer request message.
 * Contains file metadata for initiating a file transfer.
 */
public class FileTransferRequestMessage extends AbstractProtocolMessage {

    /** The file path for the transfer. */
    private final String filePath;
    /** The total file size in bytes. */
    private final long fileSize;
    /** The last modified timestamp in epoch milliseconds. */
    private final long lastModified;
    /** The BLAKE3 hash of the file. */
    private final String blake3Hash;
    /** The preferred chunk size for transfer. */
    private final int chunkSize;
    /** The content of the file. */
    private final String compressionType;

    /**
     * Creates a new file transfer request message.
     *
     * @param filePath     the file path
     * @param fileSize     the file size in bytes
     * @param lastModified the last modified timestamp (epoch milliseconds)
     * @param blake3Hash   the BLAKE3 hash of the file
     * @param chunkSize    the preferred chunk size for transfer
     */
    public FileTransferRequestMessage(String filePath, long fileSize, long lastModified,
            String blake3Hash, int chunkSize) {
        this(filePath, fileSize, lastModified, blake3Hash, chunkSize, "NONE");
    }

    /**
     * Creates a new file transfer request message with compression.
     *
     * @param filePath        the file path
     * @param fileSize        the file size in bytes
     * @param lastModified    the last modified timestamp (epoch milliseconds)
     * @param blake3Hash      the BLAKE3 hash of the file
     * @param chunkSize       the preferred chunk size for transfer
     * @param compressionType the compression type (e.g. "ZSTD", "NONE")
     */
    public FileTransferRequestMessage(String filePath, long fileSize, long lastModified,
            String blake3Hash, int chunkSize, String compressionType) {
        super(MessageType.FILE_TRANSFER_REQUEST, ProtocolConstants.Flags.ACK_REQUIRED);
        this.filePath = Objects.requireNonNull(filePath, "filePath cannot be null");
        this.fileSize = fileSize;
        this.lastModified = lastModified;
        this.blake3Hash = Objects.requireNonNull(blake3Hash, "blake3Hash cannot be null");
        this.chunkSize = chunkSize;
        this.compressionType = Objects.requireNonNull(compressionType, "compressionType cannot be null");
    }

    /**
     * Creates a file transfer request message from serialized data.
     *
     * @param buffer    the byte buffer containing the serialized message
     * @param messageId the message ID
     * @return the deserialized file transfer request message
     */
    public static FileTransferRequestMessage deserialize(ByteBuffer buffer, int messageId) {
        Objects.requireNonNull(buffer, "buffer cannot be null");

        String filePath = readString(buffer);
        long fileSize = buffer.getLong();
        long lastModified = buffer.getLong();
        String blake3Hash = readString(buffer);
        int chunkSize = buffer.getInt();

        // Handle backward compatibility or new field
        String compressionType = "NONE";
        if (buffer.hasRemaining()) {
            try {
                compressionType = readString(buffer);
            } catch (Exception e) {
                // Could not read compression type, assume NONE or end of buffer
            }
        }

        return new FileTransferRequestMessage(filePath, fileSize, lastModified, blake3Hash, chunkSize, compressionType,
                messageId);
    }

    /**
     * Creates a file transfer request message with specified message ID.
     *
     * @param filePath        the file path
     * @param fileSize        the file size in bytes
     * @param lastModified    the last modified timestamp (epoch milliseconds)
     * @param blake3Hash      the BLAKE3 hash of the file
     * @param chunkSize       the preferred chunk size for transfer
     * @param compressionType the compression type
     * @param messageId       the message ID
     */
    private FileTransferRequestMessage(String filePath, long fileSize, long lastModified,
            String blake3Hash, int chunkSize, String compressionType, int messageId) {
        super(MessageType.FILE_TRANSFER_REQUEST, ProtocolConstants.Flags.ACK_REQUIRED, messageId);
        this.filePath = filePath;
        this.fileSize = fileSize;
        this.lastModified = lastModified;
        this.blake3Hash = blake3Hash;
        this.chunkSize = chunkSize;
        this.compressionType = compressionType;
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
     * Gets the file size.
     *
     * @return the file size in bytes
     */
    public long getFileSize() {
        return fileSize;
    }

    /**
     * Gets the last modified timestamp.
     *
     * @return the last modified timestamp (epoch milliseconds)
     */
    public long getLastModified() {
        return lastModified;
    }

    /**
     * Gets the last modified time as an Instant.
     *
     * @return the last modified time as an Instant
     */
    public Instant getLastModifiedInstant() {
        return Instant.ofEpochMilli(lastModified);
    }

    /**
     * Gets the BLAKE3 hash.
     *
     * @return the BLAKE3 hash
     */
    public String getBlake3Hash() {
        return blake3Hash;
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
     * Gets the compression type.
     * 
     * @return the compression type
     */
    public String getCompressionType() {
        return compressionType;
    }

    @Override
    public ByteBuffer serializePayload() {
        int payloadSize = getPayloadSize();
        ByteBuffer buffer = ByteBuffer.allocate(payloadSize);

        writeString(buffer, filePath);
        buffer.putLong(fileSize);
        buffer.putLong(lastModified);
        writeString(buffer, blake3Hash);
        buffer.putInt(chunkSize);
        writeString(buffer, compressionType);

        buffer.flip();
        return buffer;
    }

    @Override
    public int getPayloadSize() {
        return calculateStringSize(filePath) + 8 + 8 + calculateStringSize(blake3Hash) + 4
                + calculateStringSize(compressionType);
    }

    @Override
    public boolean isValid() {
        return super.isValid()
                && !filePath.isEmpty()
                && filePath.length() <= ProtocolConstants.MAX_PATH_LENGTH
                && fileSize >= 0
                && fileSize <= ProtocolConstants.MAX_FILE_SIZE
                && lastModified >= 0
                && blake3Hash.length() == 64 // BLAKE3 hash is 64 hex characters
                && chunkSize > 0
                && chunkSize <= ProtocolConstants.MAX_CHUNK_SIZE
                && compressionType != null;
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

        FileTransferRequestMessage that = (FileTransferRequestMessage) obj;
        return fileSize == that.fileSize
                && lastModified == that.lastModified
                && chunkSize == that.chunkSize
                && filePath.equals(that.filePath)
                && blake3Hash.equals(that.blake3Hash)
                && compressionType.equals(that.compressionType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), filePath, fileSize, lastModified, blake3Hash, chunkSize, compressionType);
    }

    @Override
    public String toString() {
        return String.format("FileTransferRequestMessage{path='%s', size=%d, lastModified=%d, hash='%s', "
                + "chunkSize=%d, compression='%s', %s}",
                filePath, fileSize, lastModified, blake3Hash, chunkSize, compressionType, super.toString());
    }
}