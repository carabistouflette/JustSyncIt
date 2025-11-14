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
 * File transfer response message for accepting or rejecting file transfers.
 * Contains response status and additional information.
 */
public class FileTransferResponseMessage extends AbstractProtocolMessage {

    /** Whether the transfer is accepted. */
    private final boolean accepted;
    /** The reason for acceptance/rejection. */
    private final String reason;
    /** The offset to resume from. */
    private final long resumeOffset;
    /** The preferred chunk size. */
    private final int preferredChunkSize;

    /**
     * Creates a new file transfer response message accepting the transfer.
     *
     * @param preferredChunkSize the preferred chunk size
     */
    public FileTransferResponseMessage(int preferredChunkSize) {
        this(true, null, 0, preferredChunkSize);
    }

    /**
     * Creates a new file transfer response message rejecting the transfer.
     *
     * @param reason the reason for rejection
     */
    public FileTransferResponseMessage(String reason) {
        this(false, reason, 0, 0);
    }

    /**
     * Creates a new file transfer response message for resuming a transfer.
     *
     * @param resumeOffset the offset to resume from
     * @param preferredChunkSize the preferred chunk size
     */
    public FileTransferResponseMessage(long resumeOffset, int preferredChunkSize) {
        this(true, "Resume from offset " + resumeOffset, resumeOffset, preferredChunkSize);
    }

    /**
     * Creates a new file transfer response message.
     *
     * @param accepted whether the transfer is accepted
     * @param reason the reason for acceptance/rejection
     * @param resumeOffset the offset to resume from (0 for new transfer)
     * @param preferredChunkSize the preferred chunk size
     */
    private FileTransferResponseMessage(boolean accepted, String reason, long resumeOffset, int preferredChunkSize) {
        super(MessageType.FILE_TRANSFER_RESPONSE, ProtocolConstants.Flags.RESPONSE);
        this.accepted = accepted;
        this.reason = reason != null ? reason : "";
        this.resumeOffset = resumeOffset;
        this.preferredChunkSize = preferredChunkSize;
    }

    /**
     * Creates a file transfer response message from serialized data.
     *
     * @param buffer the byte buffer containing the serialized message
     * @param messageId the message ID
     * @return the deserialized file transfer response message
     */
    public static FileTransferResponseMessage deserialize(ByteBuffer buffer, int messageId) {
        Objects.requireNonNull(buffer, "buffer cannot be null");

        boolean accepted = buffer.get() != 0;
        String reason = readString(buffer);
        long resumeOffset = buffer.getLong();
        int preferredChunkSize = buffer.getInt();

        return new FileTransferResponseMessage(accepted, reason, resumeOffset, preferredChunkSize, messageId);
    }

    /**
     * Creates a file transfer response message with specified message ID.
     *
     * @param accepted whether the transfer is accepted
     * @param reason the reason for acceptance/rejection
     * @param resumeOffset the offset to resume from
     * @param preferredChunkSize the preferred chunk size
     * @param messageId the message ID
     */
    private FileTransferResponseMessage(boolean accepted, String reason, long resumeOffset,
                                       int preferredChunkSize, int messageId) {
        super(MessageType.FILE_TRANSFER_RESPONSE, ProtocolConstants.Flags.RESPONSE, messageId);
        this.accepted = accepted;
        this.reason = reason;
        this.resumeOffset = resumeOffset;
        this.preferredChunkSize = preferredChunkSize;
    }

    /**
     * Checks if the transfer is accepted.
     *
     * @return true if accepted, false otherwise
     */
    public boolean isAccepted() {
        return accepted;
    }

    /**
     * Gets the reason for the response.
     *
     * @return the reason
     */
    public String getReason() {
        return reason;
    }

    /**
     * Gets the resume offset.
     *
     * @return the resume offset in bytes
     */
    public long getResumeOffset() {
        return resumeOffset;
    }

    /**
     * Gets the preferred chunk size.
     *
     * @return the preferred chunk size in bytes
     */
    public int getPreferredChunkSize() {
        return preferredChunkSize;
    }

    /**
     * Checks if this is a resume response.
     *
     * @return true if this is a resume response, false otherwise
     */
    public boolean isResume() {
        return accepted && resumeOffset > 0;
    }

    @Override
    public ByteBuffer serializePayload() {
        ByteBuffer buffer = ByteBuffer.allocate(getPayloadSize());

        buffer.put((byte) (accepted ? 1 : 0));
        writeString(buffer, reason);
        buffer.putLong(resumeOffset);
        buffer.putInt(preferredChunkSize);

        buffer.flip();
        return buffer;
    }

    @Override
    public int getPayloadSize() {
        return 1 + calculateStringSize(reason) + 8 + 4; // accepted(1) + reason + resumeOffset(8) +
        // preferredChunkSize(4)
    }

    @Override
    public boolean isValid() {
        return super.isValid()
                && (!accepted || (preferredChunkSize > 0 && preferredChunkSize <= ProtocolConstants.MAX_CHUNK_SIZE))
                && resumeOffset >= 0
                && reason.length() <= 1024;
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

        FileTransferResponseMessage that = (FileTransferResponseMessage) obj;
        return accepted == that.accepted
                && resumeOffset == that.resumeOffset
                && preferredChunkSize == that.preferredChunkSize
                && reason.equals(that.reason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), accepted, reason, resumeOffset, preferredChunkSize);
    }

    @Override
    public String toString() {
        return String.format("FileTransferResponseMessage{accepted=%s, reason='%s', resumeOffset=%d, "
                           + "preferredChunkSize=%d, %s}",
                           accepted, reason, resumeOffset, preferredChunkSize, super.toString());
    }
}