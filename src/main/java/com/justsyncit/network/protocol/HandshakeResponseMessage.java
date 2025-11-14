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
 * Handshake response message for connection acknowledgment.
 * Contains server information and accepted protocol version.
 */
public class HandshakeResponseMessage extends AbstractProtocolMessage {

    /** The accepted protocol version. */
    private final short protocolVersion;
    /** The server identifier. */
    private final String serverId;
    /** The server capabilities bitmask. */
    private final int capabilities;
    /** The maximum chunk size supported. */
    private final int maxChunkSize;

    /**
     * Creates a new handshake response message.
     *
     * @param protocolVersion the accepted protocol version
     * @param serverId the server identifier
     * @param capabilities the server capabilities bitmask
     * @param maxChunkSize the maximum chunk size supported
     */
    public HandshakeResponseMessage(short protocolVersion, String serverId, int capabilities, int maxChunkSize) {
        super(MessageType.HANDSHAKE_RESPONSE, ProtocolConstants.Flags.RESPONSE);
        this.protocolVersion = protocolVersion;
        this.serverId = Objects.requireNonNull(serverId, "serverId cannot be null");
        this.capabilities = capabilities;
        this.maxChunkSize = maxChunkSize;
    }

    /**
     * Creates a handshake response message from serialized data.
     *
     * @param buffer the byte buffer containing the serialized message
     * @param messageId the message ID
     * @return the deserialized handshake response message
     */
    public static HandshakeResponseMessage deserialize(ByteBuffer buffer, int messageId) {
        Objects.requireNonNull(buffer, "buffer cannot be null");

        short protocolVersion = buffer.getShort();
        String serverId = readString(buffer);
        int capabilities = buffer.getInt();
        int maxChunkSize = buffer.getInt();

        return new HandshakeResponseMessage(protocolVersion, serverId, capabilities, maxChunkSize, messageId);
    }

    /**
     * Creates a handshake response message with specified message ID.
     *
     * @param protocolVersion the accepted protocol version
     * @param serverId the server identifier
     * @param capabilities the server capabilities bitmask
     * @param maxChunkSize the maximum chunk size supported
     * @param messageId the message ID
     */
    private HandshakeResponseMessage(short protocolVersion, String serverId, int capabilities,
                                 int maxChunkSize, int messageId) {
        super(MessageType.HANDSHAKE_RESPONSE, ProtocolConstants.Flags.RESPONSE, messageId);
        this.protocolVersion = protocolVersion;
        this.serverId = serverId;
        this.capabilities = capabilities;
        this.maxChunkSize = maxChunkSize;
    }

    /**
     * Gets the protocol version.
     *
     * @return the protocol version
     */
    public short getProtocolVersion() {
        return protocolVersion;
    }

    /**
     * Gets the server identifier.
     *
     * @return the server identifier
     */
    public String getServerId() {
        return serverId;
    }

    /**
     * Gets the server capabilities.
     *
     * @return the capabilities bitmask
     */
    public int getCapabilities() {
        return capabilities;
    }

    /**
     * Gets the maximum chunk size.
     *
     * @return the maximum chunk size in bytes
     */
    public int getMaxChunkSize() {
        return maxChunkSize;
    }

    @Override
    public ByteBuffer serializePayload() {
        ByteBuffer buffer = ByteBuffer.allocate(getPayloadSize());

        buffer.putShort(protocolVersion);
        writeString(buffer, serverId);
        buffer.putInt(capabilities);
        buffer.putInt(maxChunkSize);

        buffer.flip();
        return buffer;
    }

    @Override
    public int getPayloadSize() {
        return 2 + calculateStringSize(serverId) + 4 + 4; // version(2) + serverId + capabilities(4) + maxChunkSize(4)
    }

    @Override
    public boolean isValid() {
        return super.isValid()
                && protocolVersion > 0
                && !serverId.isEmpty()
                && serverId.length() <= 256
                && maxChunkSize > 0
                && maxChunkSize <= ProtocolConstants.MAX_CHUNK_SIZE;
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

        HandshakeResponseMessage that = (HandshakeResponseMessage) obj;
        return protocolVersion == that.protocolVersion
                && capabilities == that.capabilities
                && maxChunkSize == that.maxChunkSize
                && serverId.equals(that.serverId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), protocolVersion, serverId, capabilities, maxChunkSize);
    }

    @Override
    public String toString() {
        return String.format("HandshakeResponseMessage{version=%d, serverId='%s', capabilities=0x%08X, "
                           + "maxChunkSize=%d, %s}",
                           protocolVersion, serverId, capabilities, maxChunkSize, super.toString());
    }
}