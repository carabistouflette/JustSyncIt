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
 * Represents the protocol header for JustSyncIt network messages.
 * Provides serialization and deserialization of the binary header format.
 *
 * Header format (16 bytes total):
 * - Magic (4 bytes): 0x4A53544E ("JSTN")
 * - Version (2 bytes): Protocol version
 * - Message Type (1 byte): Message type identifier
 * - Flags (1 byte): Message flags
 * - Payload Length (4 bytes): Length of message payload
 * - Message ID (4 bytes): Unique message identifier
 */
public class ProtocolHeader {

    /** The magic number for protocol identification. */
    private final int magic;
    /** The protocol version. */
    private final short version;
    /** The message type. */
    private final MessageType messageType;
    /** The message flags. */
    private final byte flags;
    /** The payload length in bytes. */
    private final int payloadLength;
    /** The unique message identifier. */
    private final int messageId;

    /**
     * Creates a new protocol header.
     *
     * @param magic         the magic number
     * @param version       the protocol version
     * @param messageType   the message type
     * @param flags         the message flags
     * @param payloadLength the payload length in bytes
     * @param messageId     the unique message identifier
     */
    public ProtocolHeader(int magic, short version, MessageType messageType,
            byte flags, int payloadLength, int messageId) {
        this.magic = magic;
        this.version = version;
        this.messageType = Objects.requireNonNull(messageType, "messageType cannot be null");
        this.flags = flags;
        this.payloadLength = payloadLength;
        this.messageId = messageId;
    }

    /**
     * Creates a new protocol header with default values.
     *
     * @param messageType   the message type
     * @param payloadLength the payload length in bytes
     * @param messageId     the unique message identifier
     */
    public ProtocolHeader(MessageType messageType, int payloadLength, int messageId) {
        this(ProtocolConstants.PROTOCOL_MAGIC, ProtocolConstants.PROTOCOL_VERSION,
                messageType, ProtocolConstants.Flags.NONE, payloadLength, messageId);
    }

    /**
     * Creates a new protocol header with flags.
     *
     * @param messageType   the message type
     * @param flags         the message flags
     * @param payloadLength the payload length in bytes
     * @param messageId     the unique message identifier
     */
    public ProtocolHeader(MessageType messageType, byte flags, int payloadLength, int messageId) {
        this(ProtocolConstants.PROTOCOL_MAGIC, ProtocolConstants.PROTOCOL_VERSION,
                messageType, flags, payloadLength, messageId);
    }

    /**
     * Gets the magic number.
     *
     * @return the magic number
     */
    public int getMagic() {
        return magic;
    }

    /**
     * Gets the protocol version.
     *
     * @return the protocol version
     */
    public short getVersion() {
        return version;
    }

    /**
     * Gets the message type.
     *
     * @return the message type
     */
    public MessageType getMessageType() {
        return messageType;
    }

    /**
     * Gets the message flags.
     *
     * @return the message flags
     */
    public byte getFlags() {
        return flags;
    }

    /**
     * Gets the payload length.
     *
     * @return the payload length in bytes
     */
    public int getPayloadLength() {
        return payloadLength;
    }

    /**
     * Gets the message ID.
     *
     * @return the message ID
     */
    public int getMessageId() {
        return messageId;
    }

    /**
     * Serializes the header to a byte buffer.
     *
     * @return a byte buffer containing the serialized header
     */
    public ByteBuffer serialize() {
        ByteBuffer buffer = ByteBuffer.allocate(ProtocolConstants.HEADER_SIZE);

        buffer.putInt(magic);
        buffer.putShort(version);
        buffer.put(messageType.getValue());
        buffer.put(flags);
        buffer.putInt(payloadLength);
        buffer.putInt(messageId);

        buffer.flip();
        return buffer;
    }

    /**
     * Deserializes a header from a byte buffer.
     *
     * @param buffer the byte buffer containing the header data
     * @return the deserialized header
     * @throws IllegalArgumentException if the buffer is invalid or too small
     */
    public static ProtocolHeader deserialize(ByteBuffer buffer) {
        Objects.requireNonNull(buffer, "buffer cannot be null");

        if (buffer.remaining() < ProtocolConstants.HEADER_SIZE) {
            throw new IllegalArgumentException("Buffer too small for header");
        }

        int magic = buffer.getInt();
        short version = buffer.getShort();
        MessageType messageType = MessageType.fromValue(buffer.get());
        byte flags = buffer.get();
        int payloadLength = buffer.getInt();
        int messageId = buffer.getInt();

        return new ProtocolHeader(magic, version, messageType, flags, payloadLength, messageId);
    }

    /**
     * Deserializes a header from a byte array.
     *
     * @param data the byte array containing the header data
     * @return the deserialized header
     * @throws IllegalArgumentException if the data is invalid or too small
     */
    public static ProtocolHeader deserialize(byte[] data) {
        Objects.requireNonNull(data, "data cannot be null");

        if (data.length < ProtocolConstants.HEADER_SIZE) {
            throw new IllegalArgumentException("Data too small for header");
        }

        return deserialize(ByteBuffer.wrap(data));
    }

    /**
     * Validates the header for correctness.
     *
     * @return true if the header is valid, false otherwise
     */
    public boolean isValid() {
        return magic == ProtocolConstants.PROTOCOL_MAGIC
                && version == ProtocolConstants.PROTOCOL_VERSION
                && payloadLength >= 0;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        ProtocolHeader that = (ProtocolHeader) obj;
        return magic == that.magic
                && version == that.version
                && messageType == that.messageType
                && flags == that.flags
                && payloadLength == that.payloadLength
                && messageId == that.messageId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(magic, version, messageType, flags, payloadLength, messageId);
    }

    @Override
    public String toString() {
        return String.format("ProtocolHeader{magic=0x%08X, version=%d, type=%s, flags=0x%02X, payload=%d, id=%d}",
                magic, version, messageType, flags, payloadLength, messageId);
    }
}