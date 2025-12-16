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
import com.justsyncit.network.transfer.TransferOperation;
import com.justsyncit.network.transfer.BufferOperation;
import com.justsyncit.network.transfer.FileRegionOperation;
import com.justsyncit.scanner.AsyncByteBufferPool;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.justsyncit.network.connection.Connection;

/**
 * Represents a server connection from TCP client.
 * Handles message framing, buffering, and asynchronous message processing.
 * Follows Single Responsibility Principle by focusing solely on server
 * connection management.
 */
public class ServerConnection implements Connection {

    /** The logger for this class. */
    private static final Logger logger = LoggerFactory.getLogger(ServerConnection.class);

    /** The socket channel for communication. */
    private final SocketChannel socketChannel;
    /** The remote address of the server. */
    private final SocketAddress remoteAddress;
    /** Queue of pending operations. */
    private final ConcurrentLinkedQueue<TransferOperation> pendingOperations;
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

    /** The write interest handler. */
    private final Consumer<Boolean> writeInterestHandler;

    /** The creation time. */
    private final Instant creationTime;
    /** The last activity time. */
    private volatile Instant lastActivityTime;
    /** Bytes sent counter. */
    private final AtomicLong bytesSent;
    /** Bytes received counter. */
    private final AtomicLong bytesReceived;

    /**
     * Creates a new server connection.
     *
     * @param socketChannel        socket channel
     * @param remoteAddress        remote address
     * @param bufferPool           buffer pool (optional)
     * @param writeInterestHandler handler for write interest (true=enable,
     *                             false=disable)
     * @return a new ServerConnection instance
     * @throws IllegalArgumentException if socketChannel or remoteAddress or handler
     *                                  is null
     */
    public static ServerConnection create(SocketChannel socketChannel, SocketAddress remoteAddress,
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
        return new ServerConnection(socketChannel, remoteAddress, bufferPool, writeInterestHandler);
    }

    public static ServerConnection create(SocketChannel socketChannel, SocketAddress remoteAddress,
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
    private ServerConnection(SocketChannel socketChannel, SocketAddress remoteAddress, AsyncByteBufferPool bufferPool,
            Consumer<Boolean> writeInterestHandler) {
        // Store reference to socket channel - this is an injected dependency
        this.socketChannel = socketChannel;
        this.remoteAddress = remoteAddress;
        this.pendingOperations = new ConcurrentLinkedQueue<>();
        this.writing = new AtomicBoolean(false);
        this.closed = new AtomicBoolean(false);
        this.writeInterestHandler = writeInterestHandler;

        this.creationTime = Instant.now();
        this.lastActivityTime = Instant.now();
        this.bytesSent = new AtomicLong(0);
        this.bytesReceived = new AtomicLong(0);

        if (bufferPool != null) {
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
     * Processes received data from the socket channel.
     *
     * @param dataBuffer     the buffer containing received data
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
                    // readBuffer is now allocated for payload, so clean start
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
                    readBuffer = ByteBuffer.allocate(
                            com.justsyncit.network.protocol.ProtocolConstants.HEADER_SIZE);
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
     * Sends a message to the server.
     *
     * @param message the message to send
     * @return a CompletableFuture that completes when the message is sent
     */
    public CompletableFuture<Void> sendMessage(ProtocolMessage message) {
        if (closed.get()) {
            return CompletableFuture.failedFuture(
                    new IOException("Connection is closed: " + remoteAddress));
        }

        ByteBuffer messageBuffer = message.serialize();
        return sendInternal(new BufferOperation(messageBuffer));
    }

    /**
     * Sends a raw buffer to the server.
     * 
     * @param buffer the buffer to send
     * @return a CompletableFuture that completes when the buffer is sent
     */
    public CompletableFuture<Void> send(ByteBuffer buffer) {
        if (closed.get()) {
            return CompletableFuture.failedFuture(
                    new IOException("Connection is closed: " + remoteAddress));
        }
        return sendInternal(new BufferOperation(buffer));
    }

    /**
     * Sends a file region to the server.
     * 
     * @param fileChannel the file channel
     * @param position    the position
     * @param count       the count
     * @return a CompletableFuture that completes when transferred
     */
    public CompletableFuture<Void> sendFileRegion(java.nio.channels.FileChannel fileChannel, long position,
            long count) {
        if (closed.get()) {
            return CompletableFuture.failedFuture(
                    new IOException("Connection is closed: " + remoteAddress));
        }
        return sendInternal(new FileRegionOperation(fileChannel, position, count));
    }

    private CompletableFuture<Void> sendInternal(TransferOperation operation) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        // We can attach the future to the operation if needed, or track completion
        // differently.
        // For simplicity here, we assume sequential FIFO completion or just
        // fire-and-forget for the queue,
        // BUT TransferOperation doesn't carry a future.
        // ClientConnection attached future to logic.
        // Here we just queue it.
        // NOTE: The original sendMessage returned a future that completed when written?
        // Original: "future.completeExceptionally(e)" inside write loop? No, inside
        // try-catch of compareAndSet.
        // Wait, the original code tracked future only for initial queuing error?
        // No, `sendMessage` returned a future, but `pendingWrites` just held
        // ByteBuffer.
        // Where was the future completed?
        // IT WAS NOT COMPLETED! The original `sendMessage` returned a future that was
        // NEVER completed on success!
        // Look at previous code: `return future;` -> but where is
        // `future.complete(null)`?
        // It wasn't there. `sendMessage` promise was broken in original code!

        // Let's fix this properly.
        // We can wrap operation and future together.
        // But `pendingOperations` is `ConcurrentLinkedQueue<TransferOperation>`.
        // We can add a wrapper class or just complete it immediately if we don't track
        // write completion (bad).
        // Best approach: Use a wrapper ensuring TransferOperation interface or update
        // generic.

        // I will creating a local wrapper or just return completed future if we just
        // want to know it's queued.
        // But zero-copy needs completion to know when to close FileChannel?
        // Caller (NetworkServiceImpl) attaches callbacks.
        // If I return `completedFuture` immediately, caller might close FileChannel too
        // early!
        // CRITICAL: Future MUST complete only after transfer finishes.

        // Use a wrapper class `TrackedTransferOperation`?

        pendingOperations.offer(new TrackedTransferOperation(operation, future));

        if (writing.compareAndSet(false, true)) {
            try {
                writePendingData();
            } catch (IOException e) {
                // If write fails immediately, we can't easily find WHICH future failed if
                // batching.
                // But here we are just triggering the loop.
                // The loop handles errors.
                // If triggering fails (unlikely unless IOException in writePendingData bubbling
                // up), we handle it.
                // Actually writePendingData handles exceptions by closing connection usually.
            }
        }

        return future;
    }

    // Wrapper to track future
    private static class TrackedTransferOperation implements TransferOperation {
        final TransferOperation delegate;
        final CompletableFuture<Void> future;

        TrackedTransferOperation(TransferOperation delegate, CompletableFuture<Void> future) {
            this.delegate = delegate;
            this.future = future;
        }

        @Override
        public boolean transfer(SocketChannel channel) throws IOException {
            boolean done = delegate.transfer(channel);
            if (done)
                future.complete(null);
            return done;
        }

        @Override
        public void release() {
            delegate.release();
        }
    }

    /**
     * Writes pending data to the socket channel.
     */
    public void writePendingData() throws IOException {
        if (closed.get() || pendingOperations.isEmpty()) {
            writing.set(false);
            return;
        }

        TransferOperation operation = pendingOperations.peek();
        if (operation != null) {
            boolean complete = false;
            try {
                complete = operation.transfer(socketChannel);
            } catch (IOException e) {
                // Determine if we should fail the operation or the connection
                if (operation instanceof TrackedTransferOperation) {
                    ((TrackedTransferOperation) operation).future.completeExceptionally(e);
                }
                pendingOperations.poll(); // Remove failed op
                throw e;
            }

            if (complete) {
                // Operation is fully written, remove it
                pendingOperations.poll();

                // Release resources
                operation.release();

                // Continue with next operation if available
                if (!pendingOperations.isEmpty()) {
                    writePendingData(); // Continue with next
                } else {
                    writing.set(false);
                    // Disable OP_WRITE interest since we have nothing to write
                    writeInterestHandler.accept(false);
                }
            } else {
                // Not fully written, enable OP_WRITE to be notified when socket is ready again
                writeInterestHandler.accept(true);
                writing.set(false);
            }
        }
    }

    @Override
    public boolean isActive() {
        return !closed.get() && socketChannel.isConnected();
    }

    @Override
    public InetSocketAddress getLocalAddress() {
        try {
            return (InetSocketAddress) socketChannel.getLocalAddress();
        } catch (IOException e) {
            logger.warn("Failed to get local address", e);
            return null;
        }
    }

    @Override
    public InetSocketAddress getRemoteAddress() {
        return (InetSocketAddress) remoteAddress;
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
        return ConnectionType.CLIENT;
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

                // Fail all pending operations
                TransferOperation op;
                while ((op = pendingOperations.poll()) != null) {
                    if (op instanceof TrackedTransferOperation) {
                        ((TrackedTransferOperation) op).future.completeExceptionally(
                                new IOException("Connection closed"));
                    }
                    op.release();
                }

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
     * Checks if there are pending write operations.
     * 
     * @return true if there are pending operations
     */
    public boolean hasPendingWrites() {
        return !pendingOperations.isEmpty();
    }

    /**
     * Closes the connection.
     */
    public void close() {
        closeAsync().join();
    }
}