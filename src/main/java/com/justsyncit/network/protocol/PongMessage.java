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
 * Pong response message for connection health check.
 * Contains the original ping timestamp for round-trip time measurement.
 */
public class PongMessage extends AbstractProtocolMessage {

    /** The original ping timestamp. */
    private final long originalTimestamp;
    /** The response timestamp. */
    private final long responseTimestamp;

    /**
     * Creates a new pong message with current response timestamp.
     *
     * @param originalTimestamp the original ping timestamp
     */
    public PongMessage(long originalTimestamp) {
        super(MessageType.PONG, ProtocolConstants.Flags.RESPONSE);
        this.originalTimestamp = originalTimestamp;
        this.responseTimestamp = System.currentTimeMillis();
    }

    /**
     * Creates a new pong message with specified message ID.
     *
     * @param originalTimestamp the original ping timestamp
     * @param messageId the message ID
     */
    public PongMessage(long originalTimestamp, int messageId) {
        super(MessageType.PONG, ProtocolConstants.Flags.RESPONSE, messageId);
        this.originalTimestamp = originalTimestamp;
        this.responseTimestamp = System.currentTimeMillis();
    }

    /**
     * Creates a pong message from serialized data.
     *
     * @param buffer the byte buffer containing the serialized message
     * @param messageId the message ID
     * @return the deserialized pong message
     */
    public static PongMessage deserialize(ByteBuffer buffer, int messageId) {
        long originalTimestamp = buffer.getLong();
        long responseTimestamp = buffer.getLong();
        return new PongMessage(originalTimestamp, responseTimestamp, messageId);
    }

    /**
     * Creates a pong message with specified timestamps and message ID.
     *
     * @param originalTimestamp the original ping timestamp
     * @param responseTimestamp the response timestamp
     * @param messageId the message ID
     */
    private PongMessage(long originalTimestamp, long responseTimestamp, int messageId) {
        super(MessageType.PONG, ProtocolConstants.Flags.RESPONSE, messageId);
        this.originalTimestamp = originalTimestamp;
        this.responseTimestamp = responseTimestamp;
    }

    /**
     * Gets the original ping timestamp.
     *
     * @return the original timestamp
     */
    public long getOriginalTimestamp() {
        return originalTimestamp;
    }

    /**
     * Gets the response timestamp.
     *
     * @return the response timestamp
     */
    public long getResponseTimestamp() {
        return responseTimestamp;
    }

    /**
     * Calculates the round-trip time.
     *
     * @return the round-trip time in milliseconds
     */
    public long getRoundTripTime() {
        return responseTimestamp - originalTimestamp;
    }

    @Override
    public ByteBuffer serializePayload() {
        ByteBuffer buffer = ByteBuffer.allocate(getPayloadSize());
        buffer.putLong(originalTimestamp);
        buffer.putLong(responseTimestamp);
        buffer.flip();
        return buffer;
    }

    @Override
    public int getPayloadSize() {
        return 16; // originalTimestamp(8) + responseTimestamp(8)
    }

    @Override
    public boolean isValid() {
        return super.isValid() && originalTimestamp > 0 && responseTimestamp > 0;
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

        PongMessage that = (PongMessage) obj;
        return originalTimestamp == that.originalTimestamp
                && responseTimestamp == that.responseTimestamp;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(super.hashCode(), originalTimestamp, responseTimestamp);
    }

    @Override
    public String toString() {
        return String.format("PongMessage{original=%d, response=%d, rtt=%d, %s}",
                           originalTimestamp, responseTimestamp, getRoundTripTime(), super.toString());
    }
}