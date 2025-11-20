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

package com.justsyncit.network.quic;

import com.justsyncit.network.protocol.ProtocolMessage;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents a QUIC connection between client and server.
 * Provides stream management, message sending, and connection lifecycle management.
 * Supports both bidirectional and unidirectional streams for concurrent data transfer.
 */
public class QuicConnection {

    /** Logger for QUIC connection operations. */
    private static final Logger logger = LoggerFactory.getLogger(QuicConnection.class);

    /** Remote address of the connection. */
    private final InetSocketAddress remoteAddress;
    /** QUIC configuration for this connection. */
    private final QuicConfiguration configuration;
    /** Whether this is a client-side connection. */
    private final boolean isClient;
    /** Connection creation time. */
    private final Instant creationTime;
    /** List of connection event listeners. */
    private final CopyOnWriteArrayList<QuicConnectionEventListener> listeners;
    /** Active streams on this connection. */
    private final ConcurrentHashMap<Long, QuicStream> streams;
    /** Counter for generating stream IDs. */
    private final AtomicLong streamIdCounter;
    /** Flag indicating if the connection is active. */
    private final AtomicBoolean active;
    /** Counter for bytes sent. */
    private final AtomicLong bytesSent;
    /** Counter for bytes received. */
    private final AtomicLong bytesReceived;
    /** Last activity time. */
    private volatile Instant lastActivityTime;

    /**
     * Creates a new QUIC connection.
     *
     * @param remoteAddress the remote address
     * @param configuration the QUIC configuration
     * @param isClient whether this is a client-side connection
     */
    public QuicConnection(InetSocketAddress remoteAddress, QuicConfiguration configuration, boolean isClient) {
        this.remoteAddress = remoteAddress;
        this.configuration = configuration;
        this.isClient = isClient;
        this.creationTime = Instant.now();
        this.lastActivityTime = creationTime;
        this.listeners = new CopyOnWriteArrayList<>();
        this.streams = new ConcurrentHashMap<>();
        this.streamIdCounter = new AtomicLong(isClient ? 0 : 1); // Client streams start at 0, server at 1
        this.active = new AtomicBoolean(true);
        this.bytesSent = new AtomicLong(0);
        this.bytesReceived = new AtomicLong(0);
    }

    /**
     * Gets the remote address.
     *
     * @return the remote address
     */
    public InetSocketAddress getRemoteAddress() {
        return remoteAddress;
    }

    /**
     * Gets the QUIC configuration.
     *
     * @return the configuration
     */
    public QuicConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * Checks if this is a client-side connection.
     *
     * @return true if this is a client-side connection
     */
    public boolean isClient() {
        return isClient;
    }

    /**
     * Gets the connection creation time.
     *
     * @return the creation time
     */
    public Instant getCreationTime() {
        return creationTime;
    }

    /**
     * Gets the last activity time.
     *
     * @return the last activity time
     */
    public Instant getLastActivityTime() {
        return lastActivityTime;
    }

    /**
     * Updates the last activity time to the current time.
     */
    public void updateLastActivityTime() {
        this.lastActivityTime = Instant.now();
    }

    /**
     * Checks if the connection is active.
     *
     * @return true if active, false otherwise
     */
    public boolean isActive() {
        return active.get();
    }

    /**
     * Gets the number of bytes sent.
     *
     * @return the number of bytes sent
     */
    public long getBytesSent() {
        return bytesSent.get();
    }

    /**
     * Gets the number of bytes received.
     *
     * @return the number of bytes received
     */
    public long getBytesReceived() {
        return bytesReceived.get();
    }

    /**
     * Gets the number of active streams.
     *
     * @return the number of active streams
     */
    public int getActiveStreamCount() {
        return (int) streams.values().stream()
            .filter(QuicStream::isActive)
            .count();
    }

    /**
     * Creates a new stream on this connection.
     *
     * @param bidirectional whether the stream should be bidirectional
     * @return a CompletableFuture that completes with the new stream
     */
    public CompletableFuture<QuicStream> createStream(boolean bidirectional) {
        if (!active.get()) {
            return CompletableFuture.failedFuture(new IOException("Connection is not active"));
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                long streamId = generateStreamId(bidirectional);
                QuicStream stream = new QuicStream(streamId, this, bidirectional);

                streams.put(streamId, stream);
                updateLastActivityTime();

                logger.debug("Created {} stream {} on connection to {}",
                        bidirectional ? "bidirectional" : "unidirectional",
                        streamId, remoteAddress);

                notifyStreamCreated(stream);
                return stream;
            } catch (Exception e) {
                logger.error("Failed to create stream on connection to {}", remoteAddress, e);
                throw new IllegalStateException("Failed to create stream", e);
            }
        });
    }

    /**
     * Generates a new stream ID.
     *
     * @param bidirectional whether the stream is bidirectional
     * @return a new stream ID
     */
    private long generateStreamId(boolean bidirectional) {
        // QUIC stream ID format: [0/1][0/1][stream-id]
        // First bit: 0 for client-initiated, 1 for server-initiated
        // Second bit: 0 for bidirectional, 1 for unidirectional
        // Remaining bits: stream identifier

        long baseId = streamIdCounter.getAndAdd(2); // Increment by 2 to maintain initiator bit
        long streamId = baseId;

        if (!bidirectional) {
            streamId |= 0x02; // Set the unidirectional bit
        }

        if (!isClient) {
            streamId |= 0x01; // Set the server-initiated bit
        }

        return streamId;
    }

    /**
     * Gets a stream by ID.
     *
     * @param streamId the stream ID
     * @return the stream, or null if not found
     */
    public QuicStream getStream(long streamId) {
        return streams.get(streamId);
    }

    /**
     * Sends a message over the connection.
     * Creates a new stream for the message if needed.
     *
     * @param message the message to send
     * @return a CompletableFuture that completes when the message is sent
     */
    public CompletableFuture<Void> sendMessage(ProtocolMessage message) {
        if (!active.get()) {
            return CompletableFuture.failedFuture(new IOException("Connection is not active"));
        }

        return createStream(true)
            .thenCompose(stream -> stream.sendMessage(message));
    }

    /**
     * Sends a message over an existing stream.
     *
     * @param message the message to send
     * @param streamId the stream ID to use
     * @return a CompletableFuture that completes when the message is sent
     */
    public CompletableFuture<Void> sendMessage(ProtocolMessage message, long streamId) {
        QuicStream stream = streams.get(streamId);
        if (stream != null) {
            return stream.sendMessage(message);
        } else {
            return CompletableFuture.failedFuture(
                new IOException("Stream " + streamId + " not found"));
        }
    }

    /**
     * Handles a received message on a specific stream.
     *
     * @param message the received message
     * @param streamId the stream ID
     */
    public void handleReceivedMessage(ProtocolMessage message, long streamId) {
        updateLastActivityTime();
        bytesReceived.addAndGet(message.getTotalSize());

        QuicStream stream = streams.get(streamId);
        if (stream != null) {
            stream.handleReceivedMessage(message);
        } else {
            logger.warn("Received message on unknown stream {} from {}", streamId, remoteAddress);
            // Create a new stream for incoming messages
            QuicStream newStream = new QuicStream(streamId, this, true);
            streams.put(streamId, newStream);
            newStream.handleReceivedMessage(message);
            notifyStreamCreated(newStream);
        }

        notifyMessageReceived(message, streamId);
    }

    /**
     * Closes a specific stream.
     *
     * @param streamId the stream ID
     * @return a CompletableFuture that completes when the stream is closed
     */
    public CompletableFuture<Void> closeStream(long streamId) {
        QuicStream stream = streams.remove(streamId);
        if (stream != null) {
            return stream.close().thenRun(() -> {
                logger.debug("Closed stream {} on connection to {}", streamId, remoteAddress);
                notifyStreamClosed(streamId, null);
            });
        } else {
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Closes the connection and all associated streams.
     *
     * @return a CompletableFuture that completes when the connection is closed
     */
    public CompletableFuture<Void> close() {
        if (active.compareAndSet(true, false)) {
            logger.info("Closing QUIC connection to {}", remoteAddress);

            // Close all streams
            CompletableFuture<?>[] closeFutures = streams.values().stream()
                    .map(QuicStream::close)
                    .toArray(CompletableFuture[]::new);

            return CompletableFuture.allOf(closeFutures)
                .thenRun(() -> {
                    streams.clear();
                    notifyConnectionClosed(null);
                    logger.info("QUIC connection to {} closed", remoteAddress);
                })
                .exceptionally(throwable -> {
                    logger.error("Error closing connection to {}", remoteAddress, throwable);
                    notifyConnectionClosed(throwable);
                    return null;
                });
        } else {
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Adds a connection event listener.
     *
     * @param listener the event listener
     */
    public void addEventListener(QuicConnectionEventListener listener) {
        listeners.add(listener);
    }

    /**
     * Removes a connection event listener.
     *
     * @param listener the event listener to remove
     */
    public void removeEventListener(QuicConnectionEventListener listener) {
        listeners.remove(listener);
    }

    // Event notification methods
    private void notifyStreamCreated(QuicStream stream) {
        for (QuicConnectionEventListener listener : listeners) {
            try {
                listener.onStreamCreated(stream);
            } catch (Exception e) {
                logger.error("Error notifying listener of stream creation", e);
            }
        }
    }

    private void notifyStreamClosed(long streamId, Throwable cause) {
        for (QuicConnectionEventListener listener : listeners) {
            try {
                listener.onStreamClosed(streamId, cause);
            } catch (Exception e) {
                logger.error("Error notifying listener of stream closure", e);
            }
        }
    }

    private void notifyMessageReceived(ProtocolMessage message, long streamId) {
        for (QuicConnectionEventListener listener : listeners) {
            try {
                listener.onMessageReceived(message, streamId);
            } catch (Exception e) {
                logger.error("Error notifying listener of message received", e);
            }
        }
    }

    private void notifyConnectionClosed(Throwable cause) {
        for (QuicConnectionEventListener listener : listeners) {
            try {
                listener.onConnectionClosed(cause);
            } catch (Exception e) {
                logger.error("Error notifying listener of connection closure", e);
            }
        }
    }

    /**
     * Interface for QUIC connection event listeners.
     */
    public interface QuicConnectionEventListener {

        /**
         * Called when a new stream is created.
         *
         * @param stream the created stream
         */
        void onStreamCreated(QuicStream stream);

        /**
         * Called when a stream is closed.
         *
         * @param streamId the stream ID
         * @param cause the reason for closure (null if normal)
         */
        void onStreamClosed(long streamId, Throwable cause);

        /**
         * Called when a message is received.
         *
         * @param message the received message
         * @param streamId the stream ID
         */
        void onMessageReceived(ProtocolMessage message, long streamId);

        /**
         * Called when the connection is closed.
         *
         * @param cause the reason for closure (null if normal)
         */
        void onConnectionClosed(Throwable cause);
    }
}