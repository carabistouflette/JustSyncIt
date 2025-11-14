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

package com.justsyncit.network.server;

import com.justsyncit.network.protocol.ProtocolMessage;
import com.justsyncit.network.protocol.ProtocolHeader;
import com.justsyncit.network.protocol.MessageFactory;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents a client connection to TCP server.
 * Handles message framing, buffering, and asynchronous message processing.
 * Follows Single Responsibility Principle by focusing solely on client connection management.
 */
public class ClientConnection {

    /** The logger for this class. */
    private static final Logger logger = LoggerFactory.getLogger(ClientConnection.class);

    /** The socket channel. */
    private final SocketChannel socketChannel;
    /** The remote address. */
    private final SocketAddress remoteAddress;
    /** The pending writes. */
    private final ConcurrentLinkedQueue<ByteBuffer> pendingWrites;
    /** The writing flag. */
    private final AtomicBoolean writing;
    /** The closed flag. */
    private final AtomicBoolean closed;

    /** The read buffer. */
    private ByteBuffer readBuffer;
    /** The expected message size. */
    private int expectedMessageSize;
    /** The reading header flag. */
    private boolean readingHeader;

    /**
     * Creates a new client connection.
     *
     * @param socketChannel socket channel
     * @param remoteAddress remote address
     * @return a new ClientConnection instance
     * @throws IllegalArgumentException if socketChannel or remoteAddress is null
     */
    public static ClientConnection create(SocketChannel socketChannel, SocketAddress remoteAddress) {
        // Validate parameters before object creation
        if (socketChannel == null) {
            throw new IllegalArgumentException("SocketChannel cannot be null");
        }
        if (remoteAddress == null) {
            throw new IllegalArgumentException("RemoteAddress cannot be null");
        }
        return new ClientConnection(socketChannel, remoteAddress);
    }

    /**
     * Private constructor to enforce use of factory method.
     *
     * @param socketChannel socket channel (must not be null)
     * @param remoteAddress remote address (must not be null)
     */
    private ClientConnection(SocketChannel socketChannel, SocketAddress remoteAddress) {
        // Store reference to socket channel - this is an injected dependency
        this.socketChannel = socketChannel;
        this.remoteAddress = remoteAddress;
        this.pendingWrites = new ConcurrentLinkedQueue<>();
        this.writing = new AtomicBoolean(false);
        this.closed = new AtomicBoolean(false);
        this.readBuffer = ByteBuffer.allocate(
                com.justsyncit.network.protocol.ProtocolConstants.HEADER_SIZE);
        this.expectedMessageSize = -1;
        this.readingHeader = true;
    }

    /**
     * Processes received data from socket channel.
     *
     * @param dataBuffer buffer containing received data
     * @param messageHandler message handler
     * @throws IOException if an I/O error occurs
     */
    public void processReceivedData(ByteBuffer dataBuffer,
            Consumer<ProtocolMessage> messageHandler) throws IOException {
        if (closed.get()) {
            return;
        }

        // Copy received data to read buffer
        while (dataBuffer.hasRemaining()) {
            if (readingHeader) {
                // Fill header buffer
                int bytesToRead = Math.min(readBuffer.remaining(), dataBuffer.remaining());
                copyBuffer(dataBuffer, readBuffer, bytesToRead);

                if (!readBuffer.hasRemaining()) {
                    // Header is complete, parse it
                    readBuffer.flip();
                    ProtocolHeader header = ProtocolHeader.deserialize(readBuffer);

                    if (!header.isValid()) {
                        throw new IOException("Invalid protocol header received from " + remoteAddress);
                    }

                    expectedMessageSize = com.justsyncit.network.protocol.ProtocolConstants.HEADER_SIZE
                            + header.getPayloadLength();
                    readBuffer = ByteBuffer.allocate(header.getPayloadLength());
                    readingHeader = false;

                    // Reset for next header
                    readBuffer.clear();
                }
            } else {
                // Fill payload buffer
                int bytesToRead = Math.min(readBuffer.remaining(), dataBuffer.remaining());
                copyBuffer(dataBuffer, readBuffer, bytesToRead);

                if (!readBuffer.hasRemaining()) {
                    // Message is complete, parse it
                    readBuffer.flip();

                    // Create complete message buffer
                    ByteBuffer completeBuffer = ByteBuffer.allocate(expectedMessageSize);
                    completeBuffer.put(ProtocolHeader.deserialize(
                            ByteBuffer.wrap(completeBuffer.array(), 0,
                            com.justsyncit.network.protocol.ProtocolConstants.HEADER_SIZE)
                    ).serialize().array());
                    completeBuffer.put(readBuffer);
                    completeBuffer.flip();

                    try {
                        ProtocolMessage message = MessageFactory.deserializeMessage(completeBuffer);
                        messageHandler.accept(message);
                    } catch (Exception e) {
                        logger.error("Error parsing message from {}", remoteAddress, e);
                    }

                    // Reset for next message
                    readBuffer = ByteBuffer.allocate(com.justsyncit.network.protocol.ProtocolConstants.HEADER_SIZE);
                    expectedMessageSize = -1;
                    readingHeader = true;
                }
            }
        }
    }

    /**
     * Copies data from source buffer to destination buffer.
     */
    private void copyBuffer(ByteBuffer src, ByteBuffer dst, int bytesToRead) {
        byte[] temp = new byte[bytesToRead];
        src.get(temp);
        dst.put(temp);
    }

    /**
     * Sends a message to client.
     *
     * @param message message to send
     * @return a CompletableFuture that completes when message is sent
     */
    public CompletableFuture<Void> sendMessage(ProtocolMessage message) {
        if (closed.get()) {
            return CompletableFuture.failedFuture(
                new IOException("Connection is closed: " + remoteAddress));
        }

        CompletableFuture<Void> future = new CompletableFuture<>();

        // Queue message for sending
        ByteBuffer messageBuffer = message.serialize();
        pendingWrites.offer(messageBuffer);

        // Try to start writing if not already writing
        if (writing.compareAndSet(false, true)) {
            try {
                writePendingData(socketChannel);
                future.complete(null);
            } catch (IOException e) {
                future.completeExceptionally(e);
            }
        } else {
            // Writing is already in progress, message will be sent by current write operation
            // Complete future when message is eventually sent
            pendingWrites.forEach(buffer -> {
                if (buffer == messageBuffer) {
                    future.complete(null);
                }
            });
        }

        return future;
    }

    /**
     * Writes pending data to socket channel.
     */
    public void writePendingData(SocketChannel socketChannel) throws IOException {
        if (closed.get() || pendingWrites.isEmpty()) {
            writing.set(false);
            return;
        }

        ByteBuffer buffer = pendingWrites.peek();
        if (buffer != null) {
            int bytesWritten = socketChannel.write(buffer);

            if (bytesWritten > 0) {
                logger.debug("Wrote {} bytes to {}", bytesWritten, remoteAddress);
            }

            if (!buffer.hasRemaining()) {
                // Buffer is fully written, remove it
                pendingWrites.poll();

                // Continue with next buffer if available
                if (!pendingWrites.isEmpty()) {
                    writePendingData(socketChannel); // Continue with next buffer
                } else {
                    writing.set(false);
                }
            } else if (bytesWritten == 0) {
                // No data could be written, stop writing for now
                writing.set(false);
            }
        } else {
            writing.set(false);
        }
    }

    /**
     * Gets remote address.
     *
     * @return remote address
     */
    public SocketAddress getRemoteAddress() {
        return remoteAddress;
    }

    /**
     * Checks if connection is closed.
     *
     * @return true if closed, false otherwise
     */
    public boolean isClosed() {
        return closed.get();
    }

    /**
     * Closes connection.
     */
    public void close() {
        if (closed.compareAndSet(false, true)) {
            try {
                socketChannel.close();
                pendingWrites.clear();
                logger.debug("Client connection closed: {}", remoteAddress);
            } catch (IOException e) {
                logger.error("Error closing client connection: {}", remoteAddress, e);
            }
        }
    }
}