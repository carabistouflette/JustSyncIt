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

package com.justsyncit.network.client;

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
 * Represents a server connection from TCP client.
 * Handles message framing, buffering, and asynchronous message processing.
 * Follows Single Responsibility Principle by focusing solely on server connection management.
 */
public class ServerConnection {

    /** The logger for this class. */
    private static final Logger logger = LoggerFactory.getLogger(ServerConnection.class);

    /** The socket channel for communication. */
    private final SocketChannel socketChannel;
    /** The remote address of the server. */
    private final SocketAddress remoteAddress;
    /** Queue of pending write buffers. */
    private final ConcurrentLinkedQueue<ByteBuffer> pendingWrites;
    /** Flag indicating if a write operation is in progress. */
    private final AtomicBoolean writing;
    /** Flag indicating if the connection is closed. */
    private final AtomicBoolean closed;

    /** Buffer for reading incoming data. */
    private ByteBuffer readBuffer;
    /** Expected size of the current message being read. */
    private int expectedMessageSize;
    /** Flag indicating if we're currently reading a header. */
    private boolean readingHeader;

    /**
     * Creates a new server connection.
     *
     * @param socketChannel the socket channel
     * @param remoteAddress the remote address
     */
    /**
     * Creates a new server connection.
     *
     * @param socketChannel the socket channel
     * @param remoteAddress the remote address
     * @return a new ServerConnection instance
     * @throws IllegalArgumentException if parameters are null
     */
    public static ServerConnection create(SocketChannel socketChannel, SocketAddress remoteAddress) {
        // Validate parameters first before any field assignment
        if (socketChannel == null) {
            throw new IllegalArgumentException("SocketChannel cannot be null");
        }
        if (remoteAddress == null) {
            throw new IllegalArgumentException("RemoteAddress cannot be null");
        }

        return new ServerConnection(socketChannel, remoteAddress);
    }

    /**
     * Private constructor for ServerConnection.
     * Assumes all parameters are validated.
     */
    private ServerConnection(SocketChannel socketChannel, SocketAddress remoteAddress) {
        // Create defensive copy of mutable objects
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
     * Processes received data from the socket channel.
     *
     * @param dataBuffer the buffer containing received data
     * @param messageHandler the message handler
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
                    ByteBuffer headerBuffer = ByteBuffer.wrap(
                            completeBuffer.array(), 0,
                            com.justsyncit.network.protocol.ProtocolConstants.HEADER_SIZE
                    );
                    completeBuffer.put(ProtocolHeader.deserialize(headerBuffer).serialize().array());
                    completeBuffer.put(readBuffer);
                    completeBuffer.flip();

                    try {
                        ProtocolMessage message = MessageFactory.deserializeMessage(completeBuffer);
                        messageHandler.accept(message);
                    } catch (Exception e) {
                        logger.error("Error parsing message from {}", remoteAddress, e);
                    }

                    // Reset for next message
                    readBuffer = ByteBuffer.allocate(
                            com.justsyncit.network.protocol.ProtocolConstants.HEADER_SIZE);
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
     * Sends a message to the server.
     *
     * @param message the message to send
     * @return a CompletableFuture that completes when the message is sent
     */
    public CompletableFuture<Void> sendMessage(ProtocolMessage message) {
        if (closed.get()) {
            return CompletableFuture.failedFuture(
                new IOException("Connection is closed: " + remoteAddress)
            );
        }

        CompletableFuture<Void> future = new CompletableFuture<>();

        // Queue message for sending
        ByteBuffer messageBuffer = message.serialize();
        pendingWrites.offer(messageBuffer);

        // Try to start writing if not already writing
        if (writing.compareAndSet(false, true)) {
            try {
                writePendingData();
            } catch (IOException e) {
                future.completeExceptionally(e);
            }
        }

        return future;
    }

    /**
     * Writes pending data to the socket channel.
     */
    public void writePendingData() throws IOException {
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
                    writePendingData(); // Continue with next buffer
                } else {
                    writing.set(false);
                }
            } else if (bytesWritten == 0) {
                // No data could be written, stop writing for now
                writing.set(false);
            }
        }
    }

    /**
     * Gets the remote address.
     *
     * @return the remote address
     */
    public SocketAddress getRemoteAddress() {
        return remoteAddress;
    }

    /**
     * Checks if the connection is closed.
     *
     * @return true if closed, false otherwise
     */
    public boolean isClosed() {
        return closed.get();
    }

    /**
     * Closes the connection.
     */
    public CompletableFuture<Void> closeAsync() {
        if (closed.compareAndSet(false, true)) {
            CompletableFuture<Void> future = new CompletableFuture<>();
            try {
                socketChannel.close();
                pendingWrites.clear();
                logger.debug("Server connection closed: {}", remoteAddress);
                future.complete(null);
            } catch (IOException e) {
                logger.error("Error closing server connection: {}", remoteAddress, e);
                future.completeExceptionally(e);
            }
            return future;
        } else {
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Closes the connection.
     */
    public void close() {
        closeAsync().join();
    }
}