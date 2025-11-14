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
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Abstract base class for protocol messages.
 * Provides common functionality and message ID generation.
 */
public abstract class AbstractProtocolMessage implements ProtocolMessage {

    /** Generator for unique message IDs. */
    private static final AtomicInteger MESSAGE_ID_GENERATOR = new AtomicInteger(0);
    /** The type of this message. */
    private final MessageType messageType;
    /** The message flags. */
    private final byte flags;
    /** The unique message ID. */
    protected final int messageId;

    /**
     * Creates a new protocol message with the specified type and flags.
     *
     * @param messageType the message type
     * @param flags the message flags
     */
    protected AbstractProtocolMessage(MessageType messageType, byte flags) {
        this.messageType = Objects.requireNonNull(messageType, "messageType cannot be null");
        this.flags = flags;
        this.messageId = MESSAGE_ID_GENERATOR.incrementAndGet();
    }

    /**
     * Creates a new protocol message with the specified type and default flags.
     *
     * @param messageType the message type
     */
    protected AbstractProtocolMessage(MessageType messageType) {
        this(messageType, ProtocolConstants.Flags.NONE);
    }

    /**
     * Creates a new protocol message with the specified type, flags, and message ID.
     *
     * @param messageType the message type
     * @param flags the message flags
     * @param messageId the message ID
     */
    protected AbstractProtocolMessage(MessageType messageType, byte flags, int messageId) {
        this.messageType = Objects.requireNonNull(messageType, "messageType cannot be null");
        this.flags = flags;
        this.messageId = messageId;
    }

    @Override
    public MessageType getMessageType() {
        return messageType;
    }

    @Override
    public byte getFlags() {
        return flags;
    }

    @Override
    public int getMessageId() {
        return messageId;
    }

    @Override
    public boolean isValid() {
        return messageType != null && messageId >= 0;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        AbstractProtocolMessage that = (AbstractProtocolMessage) obj;
        return flags == that.flags
                && messageId == that.messageId
                && messageType == that.messageType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(messageType, flags, messageId);
    }

    @Override
    public String toString() {
        return String.format("%s{type=%s, flags=0x%02X, id=%d}",
                           getClass().getSimpleName(), messageType, flags, messageId);
    }

    /**
     * Utility method to write a string to a byte buffer with length prefix.
     *
     * @param buffer the byte buffer to write to
     * @param str the string to write
     */
    protected static void writeString(ByteBuffer buffer, String str) {
        Objects.requireNonNull(buffer, "buffer cannot be null");
        Objects.requireNonNull(str, "str cannot be null");

        byte[] strBytes = str.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        buffer.putInt(strBytes.length);
        buffer.put(strBytes);
    }

    /**
     * Utility method to read a string from a byte buffer with length prefix.
     *
     * @param buffer the byte buffer to read from
     * @return the read string
     */
    protected static String readString(ByteBuffer buffer) {
        Objects.requireNonNull(buffer, "buffer cannot be null");

        int length = buffer.getInt();
        if (length < 0) {
            throw new IllegalArgumentException("Invalid string length: " + length);
        }

        byte[] strBytes = new byte[length];
        buffer.get(strBytes);
        return new String(strBytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * Utility method to calculate the size of a string when encoded with length prefix.
     *
     * @param str the string to calculate size for
     * @return the encoded size in bytes
     */
    protected static int calculateStringSize(String str) {
        Objects.requireNonNull(str, "str cannot be null");
        byte[] strBytes = str.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return 4 + strBytes.length; // 4 bytes for length + string bytes
    }
}