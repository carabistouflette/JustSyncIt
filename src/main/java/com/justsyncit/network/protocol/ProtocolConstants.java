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

/**
 * Constants for the JustSyncIt network protocol.
 * Defines protocol version, header format, and other protocol-specific values.
 */
public final class ProtocolConstants {

    /** Protocol version for compatibility checking. */
    public static final short PROTOCOL_VERSION = 1;

    /** Protocol magic number for identification. */
    public static final int PROTOCOL_MAGIC = 0x4A53544E; // "JSTN" in hex

    /** Size of the protocol header in bytes. */
    public static final int HEADER_SIZE = 16;

    /** Maximum chunk size for file transfers (1MB). */
    public static final int MAX_CHUNK_SIZE = 1024 * 1024;

    /** Default chunk size for file transfers (64KB). */
    public static final int DEFAULT_CHUNK_SIZE = 64 * 1024;

    /** Maximum file path length in bytes. */
    public static final int MAX_PATH_LENGTH = 4096;

    /** Maximum file size supported (1TB). */
    public static final long MAX_FILE_SIZE = 1024L * 1024 * 1024 * 1024;

    /** Default socket buffer size for sending (256KB). */
    public static final int DEFAULT_SEND_BUFFER_SIZE = 256 * 1024;

    /** Default socket buffer size for receiving (256KB). */
    public static final int DEFAULT_RECEIVE_BUFFER_SIZE = 256 * 1024;

    /** Connection timeout in milliseconds (30 seconds). */
    public static final int CONNECTION_TIMEOUT_MS = 30_000;

    /** Read timeout in milliseconds (60 seconds). */
    public static final int READ_TIMEOUT_MS = 60_000;

    /** Maximum number of reconnection attempts. */
    public static final int MAX_RECONNECTION_ATTEMPTS = 5;

    /** Initial backoff delay in milliseconds (1 second). */
    public static final int INITIAL_BACKOFF_MS = 1_000;

    /** Maximum backoff delay in milliseconds (30 seconds). */
    public static final int MAX_BACKOFF_MS = 30_000;

    /** Ping interval in milliseconds (30 seconds). */
    public static final int PING_INTERVAL_MS = 30_000;

    /** Ping timeout in milliseconds (10 seconds). */
    public static final int PING_TIMEOUT_MS = 10_000;

    /** Protocol header field offsets. */
    public static final class HeaderOffset {
        /** Magic number offset (4 bytes). */
        public static final int MAGIC = 0;

        /** Protocol version offset (2 bytes). */
        public static final int VERSION = 4;

        /** Message type offset (1 byte). */
        public static final int MESSAGE_TYPE = 6;

        /** Flags offset (1 byte). */
        public static final int FLAGS = 7;

        /** Payload length offset (4 bytes). */
        public static final int PAYLOAD_LENGTH = 8;

        /** Message ID offset (4 bytes). */
        public static final int MESSAGE_ID = 12;
    }

    /** Protocol flag bits. */
    public static final class Flags {
        /** No flags set. */
        public static final byte NONE = 0x00;

        /** Message is compressed. */
        public static final byte COMPRESSED = 0x01;

        /** Message is encrypted. */
        public static final byte ENCRYPTED = 0x02;

        /** Message requires acknowledgment. */
        public static final byte ACK_REQUIRED = 0x04;

        /** Message is a response to another message. */
        public static final byte RESPONSE = 0x08;
    }

    /** Error codes. */
    public static final class ErrorCode {
        /** No error. */
        public static final int NONE = 0;

        /** Protocol version mismatch. */
        public static final int PROTOCOL_VERSION_MISMATCH = 1;

        /** Invalid message format. */
        public static final int INVALID_MESSAGE = 2;

        /** File not found. */
        public static final int FILE_NOT_FOUND = 3;

        /** Access denied. */
        public static final int ACCESS_DENIED = 4;

        /** Checksum mismatch. */
        public static final int CHECKSUM_MISMATCH = 5;

        /** Transfer timeout. */
        public static final int TRANSFER_TIMEOUT = 6;

        /** Insufficient storage space. */
        public static final int INSUFFICIENT_SPACE = 7;

        /** Internal server error. */
        public static final int INTERNAL_ERROR = 8;
    }

    // Private constructor to prevent instantiation
    private ProtocolConstants() {
        throw new UnsupportedOperationException("Utility class");
    }
}