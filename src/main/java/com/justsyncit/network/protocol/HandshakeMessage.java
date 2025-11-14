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
 * Handshake message for connection establishment.
 * Contains protocol version, client identifier, and capabilities.
 */
public class HandshakeMessage extends AbstractProtocolMessage {

    /** The protocol version. */
    private final short protocolVersion;
    /** The client identifier. */
    private final String clientId;
    /** The client capabilities bitmask. */
    private final int capabilities;

    /**
     * Creates a new handshake message.
     *
     * @param protocolVersion the protocol version
     * @param clientId the client identifier
     * @param capabilities the client capabilities bitmask
     */
    public HandshakeMessage(short protocolVersion, String clientId, int capabilities) {
        super(MessageType.HANDSHAKE);
        this.protocolVersion = protocolVersion;
        this.clientId = Objects.requireNonNull(clientId, "clientId cannot be null");
        this.capabilities = capabilities;
    }

    /**
     * Creates a new handshake message with default protocol version.
     *
     * @param clientId the client identifier
     * @param capabilities the client capabilities bitmask
     */
    public HandshakeMessage(String clientId, int capabilities) {
        this(ProtocolConstants.PROTOCOL_VERSION, clientId, capabilities);
    }

    /**
     * Creates a handshake message from serialized data.
     *
     * @param buffer the byte buffer containing the serialized message
     * @param messageId the message ID
     * @return the deserialized handshake message
     */
    public static HandshakeMessage deserialize(ByteBuffer buffer, int messageId) {
        Objects.requireNonNull(buffer, "buffer cannot be null");

        short protocolVersion = buffer.getShort();
        String clientId = readString(buffer);
        int capabilities = buffer.getInt();

        return new HandshakeMessage(protocolVersion, clientId, capabilities, messageId);
    }

    /**
     * Creates a handshake message from serialized data with specified message ID.
     *
     * @param protocolVersion the protocol version
     * @param clientId the client identifier
     * @param capabilities the client capabilities bitmask
     * @param messageId the message ID
     */
    private HandshakeMessage(short protocolVersion, String clientId, int capabilities, int messageId) {
        super(MessageType.HANDSHAKE, ProtocolConstants.Flags.NONE, messageId);
        this.protocolVersion = protocolVersion;
        this.clientId = clientId;
        this.capabilities = capabilities;
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
     * Gets the client identifier.
     *
     * @return the client identifier
     */
    public String getClientId() {
        return clientId;
    }

    /**
     * Gets the client capabilities.
     *
     * @return the capabilities bitmask
     */
    public int getCapabilities() {
        return capabilities;
    }

    @Override
    public ByteBuffer serializePayload() {
        int payloadSize = getPayloadSize();
        ByteBuffer buffer = ByteBuffer.allocate(payloadSize);

        buffer.putShort(protocolVersion);
        writeString(buffer, clientId);
        buffer.putInt(capabilities);

        buffer.flip();
        return buffer;
    }

    @Override
    public int getPayloadSize() {
        return 2 + calculateStringSize(clientId) + 4; // version(2) + clientId + capabilities(4)
    }

    @Override
    public boolean isValid() {
        return super.isValid()
                && protocolVersion > 0
                && !clientId.isEmpty()
                && clientId.length() <= 256;
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

        HandshakeMessage that = (HandshakeMessage) obj;
        return protocolVersion == that.protocolVersion
                && capabilities == that.capabilities
                && clientId.equals(that.clientId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), protocolVersion, clientId, capabilities);
    }

    @Override
    public String toString() {
        return String.format("HandshakeMessage{version=%d, clientId='%s', capabilities=0x%08X, %s}",
                           protocolVersion, clientId, capabilities, super.toString());
    }
}