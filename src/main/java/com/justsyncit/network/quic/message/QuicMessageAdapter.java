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

package com.justsyncit.network.quic.message;

import com.justsyncit.network.protocol.ProtocolMessage;
import com.justsyncit.network.protocol.MessageFactory;

import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Adapter for adapting protocol messages to QUIC stream format.
 * Provides serialization/deserialization optimized for QUIC transport
 * with support for stream multiplexing and message framing.
 */
public class QuicMessageAdapter {

    /** Logger for message adapter operations. */
    private static final Logger logger = LoggerFactory.getLogger(QuicMessageAdapter.class);

    /** QUIC message frame type for protocol messages. */
    public static final int QUIC_FRAME_TYPE_PROTOCOL_MESSAGE = 0x01;

    /** QUIC message frame type for raw data. */
    public static final int QUIC_FRAME_TYPE_RAW_DATA = 0x02;

    /** QUIC message frame type for stream control. */
    public static final int QUIC_FRAME_TYPE_STREAM_CONTROL = 0x03;

    /** Message header size in bytes. */
    private static final int MESSAGE_HEADER_SIZE = 8;

    /**
     * Serializes a protocol message for QUIC transport.
     * Adds QUIC-specific framing around the standard protocol message.
     *
     * @param message the protocol message to serialize
     * @param streamId the QUIC stream ID
     * @return a ByteBuffer containing the serialized message
     */
    public static ByteBuffer serializeForQuic(ProtocolMessage message, long streamId) {
        try {
            // First serialize the standard protocol message
            ByteBuffer messageBuffer = message.serialize();

            // Create QUIC frame header
            ByteBuffer quicBuffer = ByteBuffer.allocate(MESSAGE_HEADER_SIZE + messageBuffer.remaining());

            // Write QUIC frame type
            quicBuffer.put((byte) QUIC_FRAME_TYPE_PROTOCOL_MESSAGE);

            // Write stream ID (variable length encoding)
            writeVariableLengthInteger(quicBuffer, streamId);

            // Write message length
            writeVariableLengthInteger(quicBuffer, messageBuffer.remaining());

            // Write the actual message data
            quicBuffer.put(messageBuffer);
            quicBuffer.flip();

            logger.debug("Serialized message {} for QUIC stream {}, total size: {} bytes",
                       message.getMessageType(), streamId, quicBuffer.remaining());

            return quicBuffer;
        } catch (Exception e) {
            logger.error("Failed to serialize message {} for QUIC stream {}",
                       message.getMessageType(), streamId, e);
            throw new RuntimeException("Failed to serialize message for QUIC", e);
        }
    }

    /**
     * Deserializes a protocol message from QUIC transport.
     * Extracts the standard protocol message from QUIC framing.
     *
     * @param quicBuffer the QUIC buffer containing the message
     * @return the deserialized protocol message, or null if deserialization fails
     */
    public static ProtocolMessage deserializeFromQuic(ByteBuffer quicBuffer) {
        try {
            if (quicBuffer.remaining() < MESSAGE_HEADER_SIZE) {
                logger.warn("Insufficient data for QUIC message header: {} bytes available, {} required",
                           quicBuffer.remaining(), MESSAGE_HEADER_SIZE);
                return null;
            }

            // Read frame type
            byte frameType = quicBuffer.get();
            if (frameType != QUIC_FRAME_TYPE_PROTOCOL_MESSAGE) {
                logger.debug("Unsupported QUIC frame type: 0x{}", String.format("%02x", frameType));
                return null;
            }

            // Read stream ID
            long streamId = readVariableLengthInteger(quicBuffer);

            // Read message length
            int messageLength = (int) readVariableLengthInteger(quicBuffer);

            if (quicBuffer.remaining() < messageLength) {
                logger.warn("Insufficient data for QUIC message: {} bytes available, {} required",
                           quicBuffer.remaining(), messageLength);
                return null;
            }

            // Extract message data
            byte[] messageData = new byte[messageLength];
            quicBuffer.get(messageData);
            ByteBuffer messageBuffer = ByteBuffer.wrap(messageData);

            // Deserialize the standard protocol message
            ProtocolMessage message = MessageFactory.deserializeMessage(messageBuffer);

            if (message != null) {
                logger.debug("Deserialized message {} from QUIC stream {}",
                           message.getMessageType(), streamId);
            }

            return message;
        } catch (Exception e) {
            logger.error("Failed to deserialize QUIC message", e);
            return null;
        }
    }

    /**
     * Serializes raw data for QUIC transport.
     * Used for large data transfers that don't fit in standard protocol messages.
     *
     * @param data the raw data to serialize
     * @param streamId the QUIC stream ID
     * @return a ByteBuffer containing the serialized data
     */
    public static ByteBuffer serializeRawData(byte[] data, long streamId) {
        try {
            ByteBuffer quicBuffer = ByteBuffer.allocate(MESSAGE_HEADER_SIZE + data.length);

            // Write QUIC frame type
            quicBuffer.put((byte) QUIC_FRAME_TYPE_RAW_DATA);

            // Write stream ID
            writeVariableLengthInteger(quicBuffer, streamId);

            // Write data length
            writeVariableLengthInteger(quicBuffer, data.length);

            // Write the actual data
            quicBuffer.put(data);
            quicBuffer.flip();

            logger.debug("Serialized raw data for QUIC stream {}, size: {} bytes",
                       streamId, data.length);

            return quicBuffer;
        } catch (Exception e) {
            logger.error("Failed to serialize raw data for QUIC stream {}", streamId, e);
            throw new RuntimeException("Failed to serialize raw data for QUIC", e);
        }
    }

    /**
     * Deserializes raw data from QUIC transport.
     *
     * @param quicBuffer the QUIC buffer containing the data
     * @return the deserialized data, or null if deserialization fails
     */
    public static byte[] deserializeRawData(ByteBuffer quicBuffer) {
        try {
            if (quicBuffer.remaining() < MESSAGE_HEADER_SIZE) {
                logger.warn("Insufficient data for QUIC raw data header: {} bytes available, {} required",
                           quicBuffer.remaining(), MESSAGE_HEADER_SIZE);
                return null;
            }

            // Read frame type
            byte frameType = quicBuffer.get();
            if (frameType != QUIC_FRAME_TYPE_RAW_DATA) {
                logger.debug("Unsupported QUIC frame type for raw data: 0x{}",
                           String.format("%02x", frameType));
                return null;
            }

            // Read stream ID (not needed for raw data)
            readVariableLengthInteger(quicBuffer);

            // Read data length
            int dataLength = (int) readVariableLengthInteger(quicBuffer);

            if (quicBuffer.remaining() < dataLength) {
                logger.warn("Insufficient data for QUIC raw data: {} bytes available, {} required",
                           quicBuffer.remaining(), dataLength);
                return null;
            }

            // Extract the data
            byte[] data = new byte[dataLength];
            quicBuffer.get(data);

            logger.debug("Deserialized raw data from QUIC, size: {} bytes", dataLength);
            return data;
        } catch (Exception e) {
            logger.error("Failed to deserialize QUIC raw data", e);
            return null;
        }
    }

    /**
     * Creates a stream control message for QUIC.
     * Used for stream management operations like opening/closing streams.
     *
     * @param streamId the stream ID
     * @param controlType the control type
     * @param data additional control data
     * @return a ByteBuffer containing the control message
     */
    public static ByteBuffer createStreamControl(long streamId, int controlType, byte[] data) {
        try {
            int dataSize = data != null ? data.length : 0;
            ByteBuffer quicBuffer = ByteBuffer.allocate(MESSAGE_HEADER_SIZE + dataSize);

            // Write QUIC frame type
            quicBuffer.put((byte) QUIC_FRAME_TYPE_STREAM_CONTROL);

            // Write stream ID
            writeVariableLengthInteger(quicBuffer, streamId);

            // Write control type
            writeVariableLengthInteger(quicBuffer, controlType);

            // Write data length
            writeVariableLengthInteger(quicBuffer, dataSize);

            // Write control data if present
            if (dataSize > 0) {
                quicBuffer.put(data);
            }

            quicBuffer.flip();

            logger.debug("Created stream control message for stream {}, type: {}, data size: {}",
                       streamId, controlType, dataSize);

            return quicBuffer;
        } catch (Exception e) {
            logger.error("Failed to create stream control message for stream {}", streamId, e);
            throw new RuntimeException("Failed to create stream control message", e);
        }
    }

    /**
     * Writes a variable-length integer to the buffer.
     * Uses QUIC's variable-length integer encoding.
     *
     * @param buffer the buffer to write to
     * @param value the value to write
     */
    private static void writeVariableLengthInteger(ByteBuffer buffer, long value) {
        if (value < 64) {
            buffer.put((byte) (value & 0x3F));
        } else if (value < 16384) {
            buffer.put((byte) (0x40 | (value >> 8) & 0x3F));
            buffer.put((byte) (value & 0xFF));
        } else if (value < 1073741824) {
            buffer.put((byte) (0x80 | (value >> 24) & 0x3F));
            buffer.put((byte) ((value >> 16) & 0xFF));
            buffer.put((byte) ((value >> 8) & 0xFF));
            buffer.put((byte) (value & 0xFF));
        } else {
            buffer.put((byte) (0xC0 | (value >> 56) & 0x3F));
            buffer.put((byte) ((value >> 48) & 0xFF));
            buffer.put((byte) ((value >> 40) & 0xFF));
            buffer.put((byte) ((value >> 32) & 0xFF));
            buffer.put((byte) ((value >> 24) & 0xFF));
            buffer.put((byte) ((value >> 16) & 0xFF));
            buffer.put((byte) ((value >> 8) & 0xFF));
            buffer.put((byte) (value & 0xFF));
        }
    }

    /**
     * Reads a variable-length integer from the buffer.
     * Uses QUIC's variable-length integer encoding.
     *
     * @param buffer the buffer to read from
     * @return the read value
     */
    private static long readVariableLengthInteger(ByteBuffer buffer) {
        int firstByte = buffer.get() & 0xFF;
        long value;

        if ((firstByte & 0xC0) == 0x00) {
            // 1-byte encoding
            value = firstByte & 0x3F;
        } else if ((firstByte & 0xC0) == 0x40) {
            // 2-byte encoding
            value = ((firstByte & 0x3F) << 8) | (buffer.get() & 0xFF);
        } else if ((firstByte & 0xC0) == 0x80) {
            // 4-byte encoding
            value = ((firstByte & 0x3F) << 24)
                    | ((buffer.get() & 0xFF) << 16)
                    | ((buffer.get() & 0xFF) << 8)
                    | (buffer.get() & 0xFF);
        } else {
            // 8-byte encoding
            value = ((firstByte & 0x3F) << 56)
                    | ((buffer.get() & 0xFF) << 48)
                    | ((buffer.get() & 0xFF) << 40)
                    | ((buffer.get() & 0xFF) << 32)
                    | ((buffer.get() & 0xFF) << 24)
                    | ((buffer.get() & 0xFF) << 16)
                    | ((buffer.get() & 0xFF) << 8)
                    | (buffer.get() & 0xFF);
        }

        return value;
    }

    /**
     * Gets the maximum size for a serialized message.
     *
     * @param message the message to check
     * @return the maximum serialized size
     */
    public static int getMaxSerializedSize(ProtocolMessage message) {
        // Calculate maximum size including QUIC framing
        int messageSize = message.getTotalSize();
        int streamIdSize = 8; // Maximum stream ID size
        int lengthSize = 8; // Maximum length field size
        return MESSAGE_HEADER_SIZE + messageSize + streamIdSize + lengthSize;
    }

    /**
     * Checks if a buffer contains a complete QUIC message.
     *
     * @param buffer the buffer to check
     * @return true if a complete message is available
     */
    public static boolean hasCompleteMessage(ByteBuffer buffer) {
        if (buffer.remaining() < MESSAGE_HEADER_SIZE) {
            return false;
        }

        // Peek at the frame type and length without consuming
        buffer.mark();
        byte frameType = buffer.get();
        readVariableLengthInteger(buffer); // Skip stream ID
        int messageLength = (int) readVariableLengthInteger(buffer);
        buffer.reset();

        return buffer.remaining() >= MESSAGE_HEADER_SIZE + messageLength;
    }
}