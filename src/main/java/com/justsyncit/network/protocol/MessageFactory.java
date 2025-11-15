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
 * Factory for creating protocol messages from serialized data.
 * Handles deserialization of all supported message types.
 */
public final class MessageFactory {

    // Private constructor to prevent instantiation
    private MessageFactory() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Deserializes a complete protocol message from a byte buffer.
     *
     * @param buffer the byte buffer containing the complete message (header + payload)
     * @return the deserialized protocol message
     * @throws IllegalArgumentException if the message is invalid or unsupported
     */
    public static ProtocolMessage deserializeMessage(ByteBuffer buffer) {
        Objects.requireNonNull(buffer, "buffer cannot be null");

        if (buffer.remaining() < ProtocolConstants.HEADER_SIZE) {
            throw new IllegalArgumentException("Buffer too small for complete message");
        }

        // Extract header
        ByteBuffer headerBuffer = buffer.slice();
        headerBuffer.limit(ProtocolConstants.HEADER_SIZE);
        ProtocolHeader header = ProtocolHeader.deserialize(headerBuffer);

        if (!header.isValid()) {
            throw new IllegalArgumentException("Invalid protocol header: " + header);
        }

        // Extract payload
        buffer.position(buffer.position() + ProtocolConstants.HEADER_SIZE);
        if (buffer.remaining() < header.getPayloadLength()) {
            throw new IllegalArgumentException("Buffer too small for payload");
        }

        ByteBuffer payloadBuffer = buffer.slice();
        payloadBuffer.limit(header.getPayloadLength());

        // Create message based on type
        ProtocolMessage message = createMessage(header.getMessageType(), payloadBuffer, header.getMessageId());

        // Advance buffer position past the payload
        buffer.position(buffer.position() + header.getPayloadLength());

        return message;
    }

    /**
     * Deserializes a complete protocol message from a byte array.
     *
     * @param data the byte array containing the complete message
     * @return the deserialized protocol message
     * @throws IllegalArgumentException if the message is invalid or unsupported
     */
    public static ProtocolMessage deserializeMessage(byte[] data) {
        Objects.requireNonNull(data, "data cannot be null");
        return deserializeMessage(ByteBuffer.wrap(data));
    }

    /**
     * Creates a protocol message from header and payload.
     *
     * @param header the protocol header
     * @param payloadBuffer the payload buffer
     * @return the deserialized protocol message
     * @throws IllegalArgumentException if the message type is unsupported
     */
    public static ProtocolMessage createMessage(ProtocolHeader header, ByteBuffer payloadBuffer) {
        Objects.requireNonNull(header, "header cannot be null");
        Objects.requireNonNull(payloadBuffer, "payloadBuffer cannot be null");

        return createMessage(header.getMessageType(), payloadBuffer, header.getMessageId());
    }

    /**
     * Creates a protocol message of the specified type from payload data.
     *
     * @param messageType the message type
     * @param payloadBuffer the payload buffer
     * @param messageId the message ID
     * @return the deserialized protocol message
     * @throws IllegalArgumentException if the message type is unsupported
     */
    private static ProtocolMessage createMessage(MessageType messageType, ByteBuffer payloadBuffer, int messageId) {
        // Create a temporary header for messages that need it
        ProtocolHeader tempHeader = new ProtocolHeader(
                messageType,
                (byte) 0,
                messageId,
                payloadBuffer.remaining()
        );

        switch (messageType) {
            case HANDSHAKE:
                return HandshakeMessage.deserialize(payloadBuffer, tempHeader.getMessageId());

            case HANDSHAKE_RESPONSE:
                return HandshakeResponseMessage.deserialize(payloadBuffer, tempHeader.getMessageId());

            case FILE_TRANSFER_REQUEST:
                return FileTransferRequestMessage.deserialize(payloadBuffer, tempHeader.getMessageId());

            case FILE_TRANSFER_RESPONSE:
                return FileTransferResponseMessage.deserialize(payloadBuffer, tempHeader.getMessageId());

            case CHUNK_DATA:
                return ChunkDataMessage.deserialize(payloadBuffer, tempHeader.getMessageId());

            case CHUNK_ACK:
                return ChunkAckMessage.deserialize(payloadBuffer, tempHeader.getMessageId());

            case TRANSFER_COMPLETE:
                return TransferCompleteMessage.deserialize(payloadBuffer, tempHeader.getMessageId());

            case ERROR:
                return ErrorMessage.deserialize(payloadBuffer, tempHeader.getMessageId());

            case PING:
                return PingMessage.deserialize(payloadBuffer, tempHeader.getMessageId());

            case PONG:
                return PongMessage.deserialize(payloadBuffer, tempHeader.getMessageId());

            default:
                throw new IllegalArgumentException("Unsupported message type: " + messageType);
        }
    }

    /**
     * Creates a simple message with no payload.
     *
     * @param messageType the message type
     * @param messageId the message ID
     * @return the protocol message
     */
    public static ProtocolMessage createSimpleMessage(MessageType messageType, int messageId) {
        switch (messageType) {
            case PING:
                return new PingMessage(messageId);

            case PONG:
                return new PongMessage(messageId);

            default:
                throw new IllegalArgumentException("Unsupported simple message type: " + messageType);
        }
    }
}