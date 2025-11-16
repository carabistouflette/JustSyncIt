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
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents a QUIC stream for data transfer.
 * Provides message sending/receiving capabilities and stream lifecycle management.
 * Supports both bidirectional and unidirectional streams as per QUIC specification.
 */
public class QuicStream {

    /** Logger for QUIC stream operations. */
    private static final Logger logger = LoggerFactory.getLogger(QuicStream.class);

    /** Stream ID as per QUIC specification. */
    private final long streamId;
    /** Parent QUIC connection. */
    private final QuicConnection connection;
    /** Whether this is a bidirectional stream. */
    private final boolean bidirectional;
    /** Stream creation time. */
    private final Instant creationTime;
    /** List of stream event listeners. */
    private final CopyOnWriteArrayList<QuicStreamEventListener> listeners;
    /** Flag indicating if stream is active. */
    private final AtomicBoolean active;
    /** Flag indicating if stream is locally initiated. */
    private final boolean locallyInitiated;
    /** Counter for bytes sent. */
    private final AtomicLong bytesSent;
    /** Counter for bytes received. */
    private final AtomicLong bytesReceived;
    /** Last activity time. */
    private volatile Instant lastActivityTime;

    /**
     * Creates a new QUIC stream.
     *
     * @param streamId the stream ID
     * @param connection the parent connection
     * @param bidirectional whether this is a bidirectional stream
     */
    public QuicStream(long streamId, QuicConnection connection, boolean bidirectional) {
        this.streamId = streamId;
        this.connection = connection;
        this.bidirectional = bidirectional;
        this.creationTime = Instant.now();
        this.lastActivityTime = creationTime;
        this.listeners = new CopyOnWriteArrayList<>();
        this.active = new AtomicBoolean(true);
        this.locallyInitiated = (streamId & 0x01) == 0; // Client-initiated streams have bit 0 = 0
        this.bytesSent = new AtomicLong(0);
        this.bytesReceived = new AtomicLong(0);
    }

    /**
     * Gets the stream ID.
     *
     * @return the stream ID
     */
    public long getStreamId() {
        return streamId;
    }

    /**
     * Gets the parent connection.
     *
     * @return the parent connection
     */
    public QuicConnection getConnection() {
        return connection;
    }

    /**
     * Checks if this is a bidirectional stream.
     *
     * @return true if bidirectional, false if unidirectional
     */
    public boolean isBidirectional() {
        return bidirectional;
    }

    /**
     * Checks if this stream is locally initiated.
     *
     * @return true if locally initiated, false if remotely initiated
     */
    public boolean isLocallyInitiated() {
        return locallyInitiated;
    }

    /**
     * Gets the stream creation time.
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
     * Checks if the stream is active.
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
     * Sends a message over this stream.
     *
     * @param message the message to send
     * @return a CompletableFuture that completes when the message is sent
     */
    public CompletableFuture<Void> sendMessage(ProtocolMessage message) {
        if (!active.get()) {
            return CompletableFuture.failedFuture(new IOException("Stream is not active"));
        }

        if (!bidirectional && !locallyInitiated) {
            return CompletableFuture.failedFuture(
                new IOException("Cannot send on remotely initiated unidirectional stream")
            );
        }

        return CompletableFuture.runAsync(() -> {
            try {
                // Serialize the message
                ByteBuffer messageBuffer = message.serialize();
                
                // Send the message (placeholder implementation)
                // In a real implementation, this would use Kwik's stream API
                doSendMessage(messageBuffer);
                
                bytesSent.addAndGet(message.getTotalSize());
                updateLastActivityTime();
                
                logger.debug("Sent message {} on stream {} to {}", 
                           message.getMessageType(), streamId, 
                           connection.getRemoteAddress());
                
                notifyMessageSent(message);
            } catch (Exception e) {
                logger.error("Failed to send message on stream {}", streamId, e);
                throw new RuntimeException("Failed to send message", e);
            }
        });
    }

    /**
     * Actually sends the message data.
     *
     * @param messageBuffer the serialized message
     * @throws IOException if sending fails
     */
    private void doSendMessage(ByteBuffer messageBuffer) throws IOException {
        // Placeholder for actual message sending
        // In a real implementation, this would use Kwik's stream write API
        logger.debug("Sending {} bytes on stream {}", messageBuffer.remaining(), streamId);
        
        // Simulate network send
        // Actual implementation would use the QUIC library's stream send method
    }

    /**
     * Handles a received message on this stream.
     *
     * @param message the received message
     */
    public void handleReceivedMessage(ProtocolMessage message) {
        if (!active.get()) {
            logger.warn("Received message on inactive stream {}", streamId);
            return;
        }

        if (!bidirectional && locallyInitiated) {
            logger.warn("Received message on locally initiated unidirectional stream {}", streamId);
            return;
        }

        bytesReceived.addAndGet(message.getTotalSize());
        updateLastActivityTime();
        
        logger.debug("Received message {} on stream {} from {}", 
                   message.getMessageType(), streamId, 
                   connection.getRemoteAddress());
        
        notifyMessageReceived(message);
    }

    /**
     * Handles received raw data on this stream.
     *
     * @param data the received data
     */
    public void handleReceivedData(ByteBuffer data) {
        if (!active.get()) {
            logger.warn("Received data on inactive stream {}", streamId);
            return;
        }

        if (!bidirectional && locallyInitiated) {
            logger.warn("Received data on locally initiated unidirectional stream {}", streamId);
            return;
        }

        bytesReceived.addAndGet(data.remaining());
        updateLastActivityTime();
        
        logger.debug("Received {} bytes on stream {} from {}", 
                   data.remaining(), streamId, 
                   connection.getRemoteAddress());
        
        notifyDataReceived(data);
    }

    /**
     * Closes the stream.
     *
     * @return a CompletableFuture that completes when the stream is closed
     */
    public CompletableFuture<Void> close() {
        if (active.compareAndSet(true, false)) {
            logger.info("Closing stream {}", streamId);
            
            return CompletableFuture.runAsync(() -> {
                try {
                    // Perform stream cleanup
                    doClose();
                    
                    logger.info("Stream {} closed", streamId);
                    notifyStreamClosed(null);
                } catch (Exception e) {
                    logger.error("Error closing stream {}", streamId, e);
                    notifyStreamClosed(e);
                    throw new RuntimeException("Error closing stream", e);
                }
            });
        } else {
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Actually closes the stream.
     *
     * @throws IOException if closing fails
     */
    private void doClose() throws IOException {
        // Placeholder for actual stream closing
        // In a real implementation, this would use Kwik's stream close API
        logger.debug("Performing stream close for {}", streamId);
        
        // Simulate stream close
        // Actual implementation would use the QUIC library's stream close method
    }

    /**
     * Adds a stream event listener.
     *
     * @param listener the event listener
     */
    public void addEventListener(QuicStreamEventListener listener) {
        listeners.add(listener);
    }

    /**
     * Removes a stream event listener.
     *
     * @param listener the event listener to remove
     */
    public void removeEventListener(QuicStreamEventListener listener) {
        listeners.remove(listener);
    }

    // Event notification methods
    private void notifyMessageSent(ProtocolMessage message) {
        for (QuicStreamEventListener listener : listeners) {
            try {
                listener.onMessageSent(message);
            } catch (Exception e) {
                logger.error("Error notifying listener of message sent", e);
            }
        }
    }

    private void notifyMessageReceived(ProtocolMessage message) {
        for (QuicStreamEventListener listener : listeners) {
            try {
                listener.onMessageReceived(message);
            } catch (Exception e) {
                logger.error("Error notifying listener of message received", e);
            }
        }
    }

    private void notifyDataReceived(ByteBuffer data) {
        for (QuicStreamEventListener listener : listeners) {
            try {
                listener.onDataReceived(data);
            } catch (Exception e) {
                logger.error("Error notifying listener of data received", e);
            }
        }
    }

    private void notifyStreamClosed(Throwable cause) {
        for (QuicStreamEventListener listener : listeners) {
            try {
                listener.onStreamClosed(cause);
            } catch (Exception e) {
                logger.error("Error notifying listener of stream closure", e);
            }
        }
    }

    /**
     * Interface for QUIC stream event listeners.
     */
    public interface QuicStreamEventListener {

        /**
         * Called when a message is sent.
         *
         * @param message the sent message
         */
        void onMessageSent(ProtocolMessage message);

        /**
         * Called when a message is received.
         *
         * @param message the received message
         */
        void onMessageReceived(ProtocolMessage message);

        /**
         * Called when raw data is received.
         *
         * @param data the received data
         */
        void onDataReceived(ByteBuffer data);

        /**
         * Called when the stream is closed.
         *
         * @param cause the reason for closure (null if normal)
         */
        void onStreamClosed(Throwable cause);
    }
}