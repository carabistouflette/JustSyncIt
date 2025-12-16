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

import com.justsyncit.network.connection.Connection;
import com.justsyncit.network.protocol.ProtocolMessage;
import com.justsyncit.network.protocol.ProtocolHeader;
import com.justsyncit.network.protocol.MessageFactory;
import com.justsyncit.network.transfer.TransferOperation;
import com.justsyncit.network.transfer.BufferOperation;
import com.justsyncit.network.transfer.FileRegionOperation;
import com.justsyncit.scanner.AsyncByteBufferPool;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents a client connection to TCP server.
 * Handles message framing, buffering, and asynchronous message processing.
 * Follows Single Responsibility Principle by focusing solely on client
 * connection management.
 */
public class ClientConnection implements Connection {

    /** The logger for this class. */
    private static final Logger logger = LoggerFactory.getLogger(ClientConnection.class);

    /** The socket channel. */
    private final SocketChannel socketChannel;
    /** The remote address. */
    private final SocketAddress remoteAddress;
    /** The pending operations. */
    private final ConcurrentLinkedQueue<TransferOperation> pendingOperations;
    /** The writing flag. */
    private final AtomicBoolean writing;
    /** The closed flag. */
    private final AtomicBoolean closed;
    /** The buffer pool. */
    private final AsyncByteBufferPool bufferPool;

    /** The creation time. */
    private final Instant creationTime;
    /** The last activity time. */
    private volatile Instant lastActivityTime;
    /** Bytes sent counter. */
    private final AtomicLong bytesSent;
    /** Bytes received counter. */
    private final AtomicLong bytesReceived;

    /** The read buffer. */
    private ByteBuffer readBuffer;
    /** The expected message size. */
    private int expectedMessageSize;
    /** The reading header flag. */
    private boolean readingHeader;

    /** The write interest handler. */
    private final Consumer<Boolean> writeInterestHandler;

    /**
     * Creates a new client connection.
     *
     * @param socketChannel        socket channel
     * @param remoteAddress        remote address
     * @param bufferPool           buffer pool (optional)
     * @param writeInterestHandler handler for write interest (true=enable,
     *                             false=disable)
     * @return a new ClientConnection instance
     * @throws IllegalArgumentException if socketChannel or remoteAddress or handler
     *                                  is null
     */
    public static ClientConnection create(SocketChannel socketChannel, SocketAddress remoteAddress,
            AsyncByteBufferPool bufferPool, Consumer<Boolean> writeInterestHandler) {
        // Validate parameters before object creation
        if (socketChannel == null) {
            throw new IllegalArgumentException("SocketChannel cannot be null");
        }
        if (remoteAddress == null) {
            throw new IllegalArgumentException("RemoteAddress cannot be null");
        }
        if (writeInterestHandler == null) {
            throw new IllegalArgumentException("WriteInterestHandler cannot be null");
        }
        return new ClientConnection(socketChannel, remoteAddress, bufferPool, writeInterestHandler);
    }

    public static ClientConnection create(SocketChannel socketChannel, SocketAddress remoteAddress,
            Consumer<Boolean> writeInterestHandler) {
        return create(socketChannel, remoteAddress, null, writeInterestHandler);
    }

    /**
     * Private constructor to enforce use of factory method.
     *
     * @param socketChannel        socket channel (must not be null)
     * @param remoteAddress        remote address (must not be null)
     * @param bufferPool           buffer pool (optional)
     * @param writeInterestHandler write interest handler
     */
    private ClientConnection(SocketChannel socketChannel, SocketAddress remoteAddress, AsyncByteBufferPool bufferPool,
            Consumer<Boolean> writeInterestHandler) {
        // Store reference to socket channel - this is an injected dependency
        this.socketChannel = socketChannel;
        this.remoteAddress = remoteAddress;
        this.pendingOperations = new ConcurrentLinkedQueue<>();
        this.writing = new AtomicBoolean(false);
        this.closed = new AtomicBoolean(false);
        this.bufferPool = bufferPool;
        this.writeInterestHandler = writeInterestHandler;

        this.creationTime = Instant.now();
        this.lastActivityTime = Instant.now();
        this.bytesSent = new AtomicLong(0);
        this.bytesReceived = new AtomicLong(0);

        if (bufferPool != null) {
            // Using a default size for header reading initially, will be replaced with
            // pooled buffer logic later if needed
            // For now, simple allocation for read buffer to avoid complexity in this step
            this.readBuffer = ByteBuffer.allocate(
                    com.justsyncit.network.protocol.ProtocolConstants.HEADER_SIZE);
        } else {
            this.readBuffer = ByteBuffer.allocate(
                    com.justsyncit.network.protocol.ProtocolConstants.HEADER_SIZE);
        }

        this.expectedMessageSize = -1;
        this.readingHeader = true;
    }

    /** The current protocol header. */
    private ProtocolHeader currentHeader;

    /**
     * Processes received data from socket channel.
     *
     * @param dataBuffer     buffer containing received data
     * @param messageHandler message handler
     * @throws IOException if an I/O error occurs
     */
    public void processReceivedData(ByteBuffer dataBuffer,
            Consumer<ProtocolMessage> messageHandler) throws IOException {
        if (closed.get()) {
            return;
        }

        updateLastActivityTime();
        int received = dataBuffer.remaining();
        bytesReceived.addAndGet(received);

        // Copy received data to read buffer
        while (dataBuffer.hasRemaining()) {
            if (readingHeader) {
                // Defensive check: ensure buffer is large enough for header
                if (readBuffer.capacity() < com.justsyncit.network.protocol.ProtocolConstants.HEADER_SIZE) {
                    logger.warn("Read buffer too small for header ({} < {}), reallocating. Pending bytes: {}",
                            readBuffer.capacity(), com.justsyncit.network.protocol.ProtocolConstants.HEADER_SIZE,
                            dataBuffer.remaining());
                    readBuffer = ByteBuffer.allocate(com.justsyncit.network.protocol.ProtocolConstants.HEADER_SIZE);
                }

                // Fill header buffer
                int bytesToRead = Math.min(readBuffer.remaining(), dataBuffer.remaining());
                copyBuffer(dataBuffer, readBuffer, bytesToRead);

                if (!readBuffer.hasRemaining()) {
                    // Header is complete, parse it
                    readBuffer.flip();
                    currentHeader = ProtocolHeader.deserialize(readBuffer);

                    if (!currentHeader.isValid()) {
                        throw new IOException("Invalid protocol header received from " + remoteAddress);
                    }

                    expectedMessageSize = com.justsyncit.network.protocol.ProtocolConstants.HEADER_SIZE
                            + currentHeader.getPayloadLength();
                    readBuffer = ByteBuffer.allocate(currentHeader.getPayloadLength());
                    readingHeader = false;

                    // Reset for next header
                    // readBuffer is now allocated for payload
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

                    // Put the stored header
                    completeBuffer.put(currentHeader.serialize());

                    // Put the payload
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
                    currentHeader = null;
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
     * Sends a raw byte buffer to client.
     *
     * @param buffer buffer to send
     * @return a CompletableFuture that completes when buffer is queued
     */
    @Override
    public CompletableFuture<Void> send(ByteBuffer buffer) {
        if (closed.get()) {
            return CompletableFuture.failedFuture(
                    new IOException("Connection is closed: " + remoteAddress));
        }

        updateLastActivityTime();
        bytesSent.addAndGet(buffer.remaining());

        CompletableFuture<Void> future = new CompletableFuture<>();
        pendingOperations.offer(new BufferOperation(buffer));

        tryWrite(future);

        return future;
    }

    /**
     * Sends a message to client.
     *
     * @param message message to send
     * @return a CompletableFuture that completes when message is sent
     */
    @Override
    public CompletableFuture<Void> sendMessage(ProtocolMessage message) {
        if (closed.get()) {
            return CompletableFuture.failedFuture(
                    new IOException("Connection is closed: " + remoteAddress));
        }

        updateLastActivityTime();
        // ByteSent tracking for message is approx headers + payload
        // We'll track it when serializing or just use message total size?
        bytesSent.addAndGet(message.getTotalSize());

        CompletableFuture<Void> future = new CompletableFuture<>();

        // Queue message for sending
        ByteBuffer messageBuffer = message.serialize();
        pendingOperations.offer(new BufferOperation(messageBuffer));

        tryWrite(future);

        return future;
    }

    /**
     * Sends a file region using zero-copy transfer.
     * 
     * @param fileChannel the file channel to read from
     * @param position    the position to start reading from
     * @param count       the number of bytes to transfer
     * @return a CompletableFuture that completes when transfer is queued
     */
    @Override
    public CompletableFuture<Void> sendFileRegion(FileChannel fileChannel, long position, long count) {
        if (closed.get()) {
            return CompletableFuture.failedFuture(
                    new IOException("Connection is closed: " + remoteAddress));
        }

        updateLastActivityTime();
        bytesSent.addAndGet(count);

        CompletableFuture<Void> future = new CompletableFuture<>();
        pendingOperations.offer(new FileRegionOperation(fileChannel, position, count));

        tryWrite(future);

        return future;
    }

    private void tryWrite(CompletableFuture<Void> future) {
        // Try to start writing if not already writing
        if (writing.compareAndSet(false, true)) {
            try {
                writePendingData(socketChannel);
                future.complete(null);
            } catch (IOException e) {
                future.completeExceptionally(e);
            }
        } else {
            // Writing is already in progress, message will be sent by current write
            // operation
            // We can just complete the future here as "queued"
            // Ideally we would want to track the future until actual completion,
            // but for this refactor we maintain similar semantics to original code
            future.complete(null);
        }
    }

    /**
     * Writes pending data to socket channel.
     */
    public void writePendingData(SocketChannel socketChannel) throws IOException {
        if (closed.get() || pendingOperations.isEmpty()) {
            writing.set(false);
            return;
        }

        TransferOperation op = pendingOperations.peek();
        boolean completed = false;

        if (op != null) {
            try {
                // IMPORTANT: update activity time on physical write too?
                updateLastActivityTime();
                completed = op.transfer(socketChannel);
            } catch (IOException e) {
                logger.error("Error during transfer to {}", remoteAddress, e);
                // If error, we might want to close connection or skip op?
                // For now, rethrow to let caller handle
                writing.set(false);
                throw e;
            }

            if (completed) {
                // Operation is fully written, remove it
                TransferOperation removed = pendingOperations.poll();
                if (removed != null) {
                    removed.release();
                }

                // Continue with next op if available
                if (!pendingOperations.isEmpty()) {
                    writePendingData(socketChannel); // Continue with next op
                } else {
                    writing.set(false);
                    writeInterestHandler.accept(false);
                }
            } else {
                // Op not complete (partial write), stop writing for now, will continue on next
                // writeability event
                writeInterestHandler.accept(true);
                writing.set(false);
            }
        } else {
            writing.set(false);
            writeInterestHandler.accept(false);
        }
    }

    /**
     * Gets remote address.
     *
     * @return remote address
     */
    @Override
    public InetSocketAddress getRemoteAddress() {
        return (InetSocketAddress) remoteAddress;
    }

    @Override
    public InetSocketAddress getLocalAddress() {
        try {
            return (InetSocketAddress) socketChannel.getLocalAddress();
        } catch (IOException e) {
            return new InetSocketAddress(0);
        }
    }

    @Override
    public boolean isActive() {
        return !closed.get() && socketChannel.isConnected();
    }

    /**
     * Checks if connection is closed.
     *
     * @return true if closed, false otherwise
     */
    @Override
    public boolean isClosed() {
        return closed.get();
    }

    @Override
    public CompletableFuture<Void> closeAsync() {
        if (closed.compareAndSet(false, true)) {
            CompletableFuture<Void> future = new CompletableFuture<>();
            try {
                socketChannel.close();
                // Release all pending operations
                TransferOperation op;
                while ((op = pendingOperations.poll()) != null) {
                    op.release();
                }
                logger.debug("Client connection closed: {}", remoteAddress);
                future.complete(null);
            } catch (IOException e) {
                logger.error("Error closing client connection: {}", remoteAddress, e);
                future.completeExceptionally(e);
            }
            return future;
        } else {
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Closes connection.
     */
    public void close() {
        closeAsync().join();
    }

    @Override
    public Instant getCreationTime() {
        return creationTime;
    }

    @Override
    public Instant getLastActivityTime() {
        return lastActivityTime;
    }

    @Override
    public void updateLastActivityTime() {
        lastActivityTime = Instant.now();
    }

    @Override
    public long getBytesSent() {
        return bytesSent.get();
    }

    @Override
    public long getBytesReceived() {
        return bytesReceived.get();
    }

    @Override
    public ConnectionType getConnectionType() {
        return ConnectionType.SERVER; // From perspective of this node, this object represents a connection managed by
                                      // server?
        // Wait, ClientConnection is connection TO a client, managed BY server.
        // So type is SERVER (Server-side connection).
    }

}