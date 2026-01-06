package com.justsyncit.network.protocol;

/**
 * Enumeration of message types for the JustSyncIt network protocol.
 * Each message type has a unique byte identifier for efficient binary encoding.
 */
public enum MessageType {
    /** Handshake message for connection establishment. */
    HANDSHAKE((byte) 0x01),

    /** Handshake response message. */
    HANDSHAKE_RESPONSE((byte) 0x02),

    /** File transfer request message. */
    FILE_TRANSFER_REQUEST((byte) 0x10),

    /** File transfer response message. */
    FILE_TRANSFER_RESPONSE((byte) 0x11),

    /** Chunk data message containing file chunk. */
    CHUNK_DATA((byte) 0x20),

    /** Chunk acknowledgment message. */
    CHUNK_ACK((byte) 0x21),

    /** Transfer completion message. */
    TRANSFER_COMPLETE((byte) 0x30),

    /** Error message. */
    ERROR((byte) 0xFF),

    /** Ping message for connection health check. */
    PING((byte) 0xFE),

    /** Pong response message. */
    PONG((byte) 0xFD);

    /** The byte value for this message type. */
    private final byte value;

    MessageType(byte value) {
        this.value = value;
    }

    /**
     * Gets the byte value for this message type.
     *
     * @return the byte value
     */
    public byte getValue() {
        return value;
    }

    /**
     * Gets the message type from its byte value.
     *
     * @param value the byte value
     * @return the corresponding message type
     * @throws IllegalArgumentException if the value is unknown
     */
    public static MessageType fromValue(byte value) {
        for (MessageType type : values()) {
            if (type.value == value) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown message type: " + value);
    }
}