package com.justsyncit.network.protocol;

import java.nio.ByteBuffer;

/**
 * Ping message for connection health check.
 * Contains a timestamp for round-trip time measurement.
 */
public class PingMessage extends AbstractProtocolMessage {

    /** The timestamp of the ping message. */
    private final long timestamp;

    /**
     * Creates a new ping message with current timestamp.
     */
    public PingMessage() {
        super(MessageType.PING);
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * Creates a new ping message with specified timestamp.
     *
     * @param timestamp the timestamp
     */
    public PingMessage(long timestamp) {
        super(MessageType.PING);
        this.timestamp = timestamp;
    }

    /**
     * Creates a new ping message with specified message ID.
     *
     * @param messageId the message ID
     */
    public PingMessage(int messageId) {
        super(MessageType.PING, ProtocolConstants.Flags.NONE, messageId);
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * Creates a ping message from serialized data.
     *
     * @param buffer    the byte buffer containing the serialized message
     * @param messageId the message ID
     * @return the deserialized ping message
     */
    public static PingMessage deserialize(ByteBuffer buffer, int messageId) {
        long timestamp = buffer.getLong();
        return new PingMessage(timestamp, messageId);
    }

    /**
     * Creates a ping message with specified timestamp and message ID.
     *
     * @param timestamp the timestamp
     * @param messageId the message ID
     */
    private PingMessage(long timestamp, int messageId) {
        super(MessageType.PING, ProtocolConstants.Flags.NONE, messageId);
        this.timestamp = timestamp;
    }

    /**
     * Gets the timestamp.
     *
     * @return the timestamp
     */
    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public ByteBuffer serializePayload() {
        ByteBuffer buffer = ByteBuffer.allocate(getPayloadSize());
        buffer.putLong(timestamp);
        buffer.flip();
        return buffer;
    }

    @Override
    public int getPayloadSize() {
        return 8; // timestamp (8 bytes)
    }

    @Override
    public boolean isValid() {
        return super.isValid() && timestamp > 0;
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

        PingMessage that = (PingMessage) obj;
        return timestamp == that.timestamp;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(super.hashCode(), timestamp);
    }

    @Override
    public String toString() {
        return String.format("PingMessage{timestamp=%d, %s}", timestamp, super.toString());
    }
}