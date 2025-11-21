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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for ProtocolMessage.
 */
public class ProtocolMessageTest {

    private ProtocolHeader header;
    private byte[] testData;

    @BeforeEach
    void setUp() {
        header = new ProtocolHeader(MessageType.HANDSHAKE, 100, 12345);
        testData = "test data".getBytes();
    }

    @Test
    void testProtocolHeader() {
        assertEquals(MessageType.HANDSHAKE, header.getMessageType());
        assertEquals(12345, header.getMessageId());
        assertEquals(100, header.getPayloadLength());
        assertEquals(ProtocolConstants.PROTOCOL_MAGIC, header.getMagic());
        assertEquals(ProtocolConstants.PROTOCOL_VERSION, header.getVersion());
        assertTrue(header.isValid());
    }

    @Test
    void testMessageType() {
        assertEquals("HANDSHAKE", MessageType.HANDSHAKE.name());
        assertEquals(0, MessageType.HANDSHAKE.ordinal());

        assertEquals("CHUNK_DATA", MessageType.CHUNK_DATA.name());
        assertEquals(4, MessageType.CHUNK_DATA.ordinal());

        assertEquals("CHUNK_ACK", MessageType.CHUNK_ACK.name());
        assertEquals(5, MessageType.CHUNK_ACK.ordinal());
    }

    @Test
    void testProtocolConstants() {
        assertEquals(0x4A53544E, ProtocolConstants.PROTOCOL_MAGIC);
        assertEquals(16, ProtocolConstants.HEADER_SIZE);
        assertEquals(1024 * 1024, ProtocolConstants.MAX_CHUNK_SIZE);
        assertEquals(30_000, ProtocolConstants.CONNECTION_TIMEOUT_MS);
        assertEquals(64 * 1024, ProtocolConstants.DEFAULT_CHUNK_SIZE);
    }

    @Test
    void testProtocolHeaderSerialization() {
        ByteBuffer serialized = header.serialize();
        ProtocolHeader deserialized = ProtocolHeader.deserialize(serialized);

        assertEquals(header, deserialized);
    }

    @Test
    void testProtocolHeaderOffsets() {
        assertEquals(0, ProtocolConstants.HeaderOffset.MAGIC);
        assertEquals(4, ProtocolConstants.HeaderOffset.VERSION);
        assertEquals(6, ProtocolConstants.HeaderOffset.MESSAGE_TYPE);
        assertEquals(7, ProtocolConstants.HeaderOffset.FLAGS);
        assertEquals(8, ProtocolConstants.HeaderOffset.PAYLOAD_LENGTH);
        assertEquals(12, ProtocolConstants.HeaderOffset.MESSAGE_ID);
    }

    @Test
    void testProtocolFlags() {
        assertEquals(0x00, ProtocolConstants.Flags.NONE);
        assertEquals(0x01, ProtocolConstants.Flags.COMPRESSED);
        assertEquals(0x02, ProtocolConstants.Flags.ENCRYPTED);
        assertEquals(0x04, ProtocolConstants.Flags.ACK_REQUIRED);
        assertEquals(0x08, ProtocolConstants.Flags.RESPONSE);
    }

    @Test
    void testErrorCodes() {
        assertEquals(0, ProtocolConstants.ErrorCode.NONE);
        assertEquals(1, ProtocolConstants.ErrorCode.PROTOCOL_VERSION_MISMATCH);
        assertEquals(2, ProtocolConstants.ErrorCode.INVALID_MESSAGE);
        assertEquals(3, ProtocolConstants.ErrorCode.FILE_NOT_FOUND);
        assertEquals(8, ProtocolConstants.ErrorCode.INTERNAL_ERROR);
    }

    @Test
    void testAbstractProtocolMessage() {
        TestProtocolMessage message = new TestProtocolMessage(MessageType.HANDSHAKE, testData);

        assertEquals(MessageType.HANDSHAKE, message.getMessageType());
        assertEquals(ProtocolConstants.Flags.NONE, message.getFlags());
        assertTrue(message.isValid());
        assertEquals(testData.length, message.getPayloadSize());
    }

    @Test
    void testProtocolMessageImplementation() {
        TestProtocolMessage message = new TestProtocolMessage(MessageType.HANDSHAKE, testData);

        assertEquals(MessageType.HANDSHAKE, message.getMessageType());
        assertEquals(ProtocolConstants.Flags.NONE, message.getFlags());
        assertTrue(message.isValid());
        assertEquals(testData.length, message.getPayloadSize());

        // Test serialization
        ByteBuffer payload = message.serializePayload();
        assertNotNull(payload);
        assertEquals(testData.length, payload.remaining());
    }

    /**
     * Test implementation of AbstractProtocolMessage for testing.
     */
    private static class TestProtocolMessage extends AbstractProtocolMessage {
        private final byte[] data;

        TestProtocolMessage(MessageType messageType, byte[] data) {
            super(messageType);
            this.data = data;
        }

        @Override
        public ByteBuffer serializePayload() {
            return ByteBuffer.wrap(data);
        }

        @Override
        public int getPayloadSize() {
            return data.length;
        }
    }
}