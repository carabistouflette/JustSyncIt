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
 * Transfer complete message for signaling successful file transfer completion.
 * Contains file information and final verification status.
 */
public class TransferCompleteMessage extends AbstractProtocolMessage {

    /** The file path for the transfer. */
    private final String filePath;
    /** The total bytes transferred. */
    private final long totalBytesTransferred;
    /** The total file size. */
    private final long totalFileSize;
    /** The final BLAKE3 hash. */
    private final String finalBlake3Hash;
    /** Whether verification was successful. */
    private final boolean verificationSuccessful;
    /** The error message, if any. */
    private final String errorMessage;

    /**
     * Creates a new transfer complete message for successful transfer.
     *
     * @param filePath the file path
     * @param totalBytesTransferred the total bytes transferred
     * @param totalFileSize the total file size
     * @param finalBlake3Hash the final BLAKE3 hash
     */
    public TransferCompleteMessage(String filePath, long totalBytesTransferred,
                                 long totalFileSize, String finalBlake3Hash) {
        super(MessageType.TRANSFER_COMPLETE, ProtocolConstants.Flags.RESPONSE);
        this.filePath = Objects.requireNonNull(filePath, "filePath cannot be null");
        this.totalBytesTransferred = totalBytesTransferred;
        this.totalFileSize = totalFileSize;
        this.finalBlake3Hash = Objects.requireNonNull(finalBlake3Hash, "finalBlake3Hash cannot be null");
        this.verificationSuccessful = true;
        this.errorMessage = "";
    }

    /**
     * Creates a new transfer complete message for failed transfer.
     *
     * @param filePath the file path
     * @param totalBytesTransferred the total bytes transferred
     * @param totalFileSize the total file size
     * @param errorMessage the error message
     */
    public TransferCompleteMessage(String filePath, long totalBytesTransferred,
                                 long totalFileSize, String errorMessage, boolean isFailure) {
        super(MessageType.TRANSFER_COMPLETE, ProtocolConstants.Flags.RESPONSE);
        this.filePath = Objects.requireNonNull(filePath, "filePath cannot be null");
        this.totalBytesTransferred = totalBytesTransferred;
        this.totalFileSize = totalFileSize;
        this.finalBlake3Hash = null;
        this.verificationSuccessful = false;
        this.errorMessage = Objects.requireNonNull(errorMessage, "errorMessage cannot be null");
    }

    /**
     * Creates a transfer complete message from serialized data.
     *
     * @param buffer the byte buffer containing the serialized message
     * @param messageId the message ID
     * @return the deserialized transfer complete message
     */
    public static TransferCompleteMessage deserialize(ByteBuffer buffer, int messageId) {
        Objects.requireNonNull(buffer, "buffer cannot be null");

        String filePath = readString(buffer);
        long totalBytesTransferred = buffer.getLong();
        long totalFileSize = buffer.getLong();
        boolean verificationSuccessful = buffer.get() != 0;
        String finalBlake3Hash = verificationSuccessful ? readString(buffer) : "";
        String errorMessage = verificationSuccessful ? "" : readString(buffer);

        return new TransferCompleteMessage(filePath, totalBytesTransferred, totalFileSize,
                                        finalBlake3Hash, verificationSuccessful, errorMessage, messageId);
    }

    /**
     * Creates a transfer complete message with specified message ID.
     *
     * @param filePath the file path
     * @param totalBytesTransferred the total bytes transferred
     * @param totalFileSize the total file size
     * @param finalBlake3Hash the final BLAKE3 hash
     * @param verificationSuccessful whether verification was successful
     * @param errorMessage the error message
     * @param messageId the message ID
     */
    private TransferCompleteMessage(String filePath, long totalBytesTransferred, long totalFileSize,
                               String finalBlake3Hash, boolean verificationSuccessful, String errorMessage,
                               int messageId) {
        super(MessageType.TRANSFER_COMPLETE, ProtocolConstants.Flags.RESPONSE, messageId);
        this.filePath = filePath;
        this.totalBytesTransferred = totalBytesTransferred;
        this.totalFileSize = totalFileSize;
        this.finalBlake3Hash = finalBlake3Hash;
        this.verificationSuccessful = verificationSuccessful;
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
     * Gets the total bytes transferred.
     *
     * @return the total bytes transferred
     */
    public long getTotalBytesTransferred() {
        return totalBytesTransferred;
    }

    /**
     * Gets the total file size.
     *
     * @return the total file size
     */
    public long getTotalFileSize() {
        return totalFileSize;
    }

    /**
     * Gets the final BLAKE3 hash.
     *
     * @return the final BLAKE3 hash
     */
    public String getFinalBlake3Hash() {
        return finalBlake3Hash;
    }

    /**
     * Checks if verification was successful.
     *
     * @return true if verification was successful, false otherwise
     */
    public boolean isVerificationSuccessful() {
        return verificationSuccessful;
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
     * Checks if the transfer was complete.
     *
     * @return true if transfer was complete, false otherwise
     */
    public boolean isTransferComplete() {
        return totalBytesTransferred >= totalFileSize;
    }

    /**
     * Gets the transfer progress as a percentage.
     *
     * @return the transfer progress (0.0 to 1.0)
     */
    public double getTransferProgress() {
        return totalFileSize > 0 ? (double) totalBytesTransferred / totalFileSize : 0.0;
    }

    @Override
    public ByteBuffer serializePayload() {
        ByteBuffer buffer = ByteBuffer.allocate(getPayloadSize());

        writeString(buffer, filePath);
        buffer.putLong(totalBytesTransferred);
        buffer.putLong(totalFileSize);
        buffer.put((byte) (verificationSuccessful ? 1 : 0));

        if (verificationSuccessful) {
            writeString(buffer, finalBlake3Hash);
        } else {
            writeString(buffer, errorMessage);
        }

        buffer.flip();
        return buffer;
    }

    @Override
    public int getPayloadSize() {
        int size = calculateStringSize(filePath) + 8 + 8 + 1; // filePath + bytesTransferred(8) + fileSize(8) +
        // verification(1)

        if (verificationSuccessful) {
            size += calculateStringSize(finalBlake3Hash);
        } else {
            size += calculateStringSize(errorMessage);
        }

        return size;
    }

    @Override
    public boolean isValid() {
        return super.isValid()
                && !filePath.isEmpty()
                && filePath.length() <= ProtocolConstants.MAX_PATH_LENGTH
                && totalBytesTransferred >= 0
                && totalFileSize > 0
                && totalFileSize <= ProtocolConstants.MAX_FILE_SIZE
                && (verificationSuccessful
                    ? (finalBlake3Hash != null && finalBlake3Hash.length() == 64)
                    : errorMessage.length() <= 1024);
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

        TransferCompleteMessage that = (TransferCompleteMessage) obj;
        return totalBytesTransferred == that.totalBytesTransferred
                && totalFileSize == that.totalFileSize
                && verificationSuccessful == that.verificationSuccessful
                && filePath.equals(that.filePath)
                && Objects.equals(finalBlake3Hash, that.finalBlake3Hash)
                && errorMessage.equals(that.errorMessage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), filePath, totalBytesTransferred, totalFileSize,
                          finalBlake3Hash, verificationSuccessful, errorMessage);
    }

    @Override
    public String toString() {
        return String.format("TransferCompleteMessage{path='%s', transferred=%d, total=%d, verified=%s, "
                           + "hash='%s', error='%s', %s}",
                           filePath, totalBytesTransferred, totalFileSize, verificationSuccessful,
                           finalBlake3Hash, errorMessage, super.toString());
    }
}