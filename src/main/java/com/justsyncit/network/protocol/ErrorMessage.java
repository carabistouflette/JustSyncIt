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
 * Error message for reporting protocol errors.
 * Contains error code and optional error message.
 */
public class ErrorMessage extends AbstractProtocolMessage {

    /** The error code. */
    private final int errorCode;
    /** The error message. */
    private final String errorMessage;

    /**
     * Creates a new error message.
     *
     * @param errorCode the error code
     * @param errorMessage the error message (can be null or empty)
     */
    public ErrorMessage(int errorCode, String errorMessage) {
        super(MessageType.ERROR);
        this.errorCode = errorCode;
        this.errorMessage = errorMessage != null ? errorMessage : "";
    }

    /**
     * Creates a new error message with just an error code.
     *
     * @param errorCode the error code
     */
    public ErrorMessage(int errorCode) {
        this(errorCode, null);
    }

    /**
     * Creates an error message from serialized data.
     *
     * @param buffer the byte buffer containing the serialized message
     * @param messageId the message ID
     * @return the deserialized error message
     */
    public static ErrorMessage deserialize(ByteBuffer buffer, int messageId) {
        Objects.requireNonNull(buffer, "buffer cannot be null");

        int errorCode = buffer.getInt();
        String errorMessage = readString(buffer);

        return new ErrorMessage(errorCode, errorMessage, messageId);
    }

    /**
     * Creates an error message with specified message ID.
     *
     * @param errorCode the error code
     * @param errorMessage the error message
     * @param messageId the message ID
     */
    private ErrorMessage(int errorCode, String errorMessage, int messageId) {
        super(MessageType.ERROR, ProtocolConstants.Flags.RESPONSE, messageId);
        this.errorCode = errorCode;
        this.errorMessage = errorMessage != null ? errorMessage : "";
    }

    /**
     * Gets the error code.
     *
     * @return the error code
     */
    public int getErrorCode() {
        return errorCode;
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
     * Gets a human-readable description of the error code.
     *
     * @return the error description
     */
    public String getErrorDescription() {
        switch (errorCode) {
            case ProtocolConstants.ErrorCode.NONE:
                return "No error";
            case ProtocolConstants.ErrorCode.PROTOCOL_VERSION_MISMATCH:
                return "Protocol version mismatch";
            case ProtocolConstants.ErrorCode.INVALID_MESSAGE:
                return "Invalid message format";
            case ProtocolConstants.ErrorCode.FILE_NOT_FOUND:
                return "File not found";
            case ProtocolConstants.ErrorCode.ACCESS_DENIED:
                return "Access denied";
            case ProtocolConstants.ErrorCode.CHECKSUM_MISMATCH:
                return "Checksum mismatch";
            case ProtocolConstants.ErrorCode.TRANSFER_TIMEOUT:
                return "Transfer timeout";
            case ProtocolConstants.ErrorCode.INSUFFICIENT_SPACE:
                return "Insufficient storage space";
            case ProtocolConstants.ErrorCode.INTERNAL_ERROR:
                return "Internal server error";
            default:
                return "Unknown error code: " + errorCode;
        }
    }

    @Override
    public ByteBuffer serializePayload() {
        ByteBuffer buffer = ByteBuffer.allocate(getPayloadSize());
        buffer.putInt(errorCode);
        writeString(buffer, errorMessage);
        buffer.flip();
        return buffer;
    }

    @Override
    public int getPayloadSize() {
        return 4 + calculateStringSize(errorMessage); // errorCode(4) + errorMessage
    }

    @Override
    public boolean isValid() {
        return super.isValid() && errorCode >= 0;
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

        ErrorMessage that = (ErrorMessage) obj;
        return errorCode == that.errorCode
                && errorMessage.equals(that.errorMessage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), errorCode, errorMessage);
    }

    @Override
    public String toString() {
        return String.format("ErrorMessage{code=%d, description='%s', message='%s', %s}",
                           errorCode, getErrorDescription(), errorMessage, super.toString());
    }
}