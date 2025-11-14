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

/**
 * Interface for protocol messages in the JustSyncIt network protocol.
 * All protocol messages must implement this interface for serialization and deserialization.
 */
public interface ProtocolMessage {

    /**
     * Gets the message type.
     *
     * @return the message type
     */
    MessageType getMessageType();

    /**
     * Gets the message flags.
     *
     * @return the message flags
     */
    byte getFlags();

    /**
     * Gets the message ID.
     *
     * @return the message ID
     */
    int getMessageId();

    /**
     * Serializes the message payload to a byte buffer.
     * This does not include the protocol header, only the payload.
     *
     * @return a byte buffer containing the serialized payload
     */
    ByteBuffer serializePayload();

    /**
     * Gets the total size of the message including header and payload.
     *
     * @return the total message size in bytes
     */
    default int getTotalSize() {
        return ProtocolConstants.HEADER_SIZE + getPayloadSize();
    }

    /**
     * Gets the size of the message payload.
     *
     * @return the payload size in bytes
     */
    int getPayloadSize();

    /**
     * Creates a protocol header for this message.
     *
     * @return the protocol header
     */
    default ProtocolHeader createHeader() {
        return new ProtocolHeader(getMessageType(), getFlags(), getPayloadSize(), getMessageId());
    }

    /**
     * Serializes the complete message including header and payload.
     *
     * @return a byte buffer containing the complete serialized message
     */
    default ByteBuffer serialize() {
        ByteBuffer headerBuffer = createHeader().serialize();
        ByteBuffer payloadBuffer = serializePayload();

        ByteBuffer completeBuffer = ByteBuffer.allocate(getTotalSize());
        completeBuffer.put(headerBuffer);
        completeBuffer.put(payloadBuffer);
        completeBuffer.flip();

        return completeBuffer;
    }

    /**
     * Validates the message for correctness.
     *
     * @return true if the message is valid, false otherwise
     */
    boolean isValid();
}