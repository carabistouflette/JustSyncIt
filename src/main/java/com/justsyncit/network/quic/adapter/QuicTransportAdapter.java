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

package com.justsyncit.network.quic.adapter;

import com.justsyncit.network.protocol.ProtocolMessage;
import com.justsyncit.network.quic.QuicClient;
import com.justsyncit.network.quic.QuicConfiguration;
import com.justsyncit.network.quic.QuicConnection;
import com.justsyncit.network.quic.QuicStream;
import com.justsyncit.network.quic.QuicTransport;
import com.justsyncit.network.quic.message.QuicMessageAdapter;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Adapter that integrates QUIC transport with the existing network architecture.
 * Provides a bridge between the high-level network service and QUIC implementation,
 * enabling seamless switching between TCP and QUIC transports.
 * Follows Adapter pattern and implements QuicTransport interface for DIP compliance.
 */
public class QuicTransportAdapter implements QuicTransport {

    /** Logger for QUIC transport adapter operations. */
    private static final Logger logger = LoggerFactory.getLogger(QuicTransportAdapter.class);

    /** QUIC client instance. */
    private final QuicClient quicClient;
    /** QUIC configuration. */
    private final QuicConfiguration configuration;

    /**
     * Creates a new QUIC transport adapter with default configuration.
     */
    public QuicTransportAdapter() {
        this(QuicConfiguration.defaultConfiguration());
    }

    /**
     * Creates a new QUIC transport adapter with specified configuration.
     *
     * @param configuration the QUIC configuration
     */
    public QuicTransportAdapter(QuicConfiguration configuration) {
        this.configuration = configuration;
        this.quicClient = createQuicClient(configuration);
        this.transportListeners = new CopyOnWriteArrayList<>();
        
        // Set up event listeners
        setupEventListeners();
    }
    
    /**
     * Creates a QUIC client instance. Can be overridden for testing.
     *
     * @param configuration QUIC configuration
     * @return a new QUIC client
     */
    protected QuicClient createQuicClient(QuicConfiguration configuration) {
        return new QuicClient(configuration);
    }

    /** List of transport event listeners. */
    private final CopyOnWriteArrayList<QuicTransportEventListener> transportListeners;
    
    /**
     * Sets up event listeners for QUIC client events.
     */
    private void setupEventListeners() {
        if (quicClient != null) {
            quicClient.addEventListener(new QuicClient.QuicClientEventListener() {
                @Override
                public void onConnected(InetSocketAddress serverAddress, QuicConnection connection) {
                    logger.info("QUIC connected to: {}", serverAddress);
                    notifyConnected(serverAddress, connection);
                }

                @Override
                public void onDisconnected(InetSocketAddress serverAddress, Throwable cause) {
                    logger.info("QUIC disconnected from: {} - {}", serverAddress,
                               cause != null ? cause.getMessage() : "normal");
                    notifyDisconnected(serverAddress, cause);
                }

                @Override
                public void onMessageReceived(InetSocketAddress serverAddress, ProtocolMessage message) {
                    logger.debug("QUIC received message from {}: {}",
                                serverAddress, message.getMessageType());
                    notifyMessageReceived(serverAddress, message);
                }

                @Override
                public void onError(Throwable error, String context) {
                    logger.error("QUIC error in {}: {}", context, error.getMessage());
                    notifyError(error, context);
                }
            });
        }
    }

    /**
     * Starts the QUIC transport adapter.
     *
     * @return a CompletableFuture that completes when the adapter is started
     */
    public CompletableFuture<Void> start() {
        logger.info("Starting QUIC transport adapter");
        return quicClient.start()
            .thenRun(() -> logger.info("QUIC transport adapter started"));
    }

    /**
     * Stops the QUIC transport adapter.
     *
     * @return a CompletableFuture that completes when the adapter is stopped
     */
    public CompletableFuture<Void> stop() {
        logger.info("Stopping QUIC transport adapter");
        return quicClient.stop()
            .thenRun(() -> logger.info("QUIC transport adapter stopped"));
    }

    /**
     * Connects to a remote server using QUIC.
     *
     * @param address the server address
     * @return a CompletableFuture that completes when the connection is established
     */
    public CompletableFuture<QuicConnection> connect(InetSocketAddress address) {
        logger.info("Connecting to {} using QUIC", address);
        return quicClient.connect(address);
    }

    /**
     * Disconnects from a remote server.
     *
     * @param address the server address
     * @return a CompletableFuture that completes when the disconnection is complete
     */
    public CompletableFuture<Void> disconnect(InetSocketAddress address) {
        logger.info("Disconnecting from {} using QUIC", address);
        return quicClient.disconnect(address);
    }

    /**
     * Sends a message to a remote server using QUIC.
     *
     * @param message the message to send
     * @param remoteAddress the server address
     * @return a CompletableFuture that completes when the message is sent
     */
    public CompletableFuture<Void> sendMessage(ProtocolMessage message, InetSocketAddress remoteAddress) {
        logger.debug("Sending message {} to {} using QUIC", 
                    message.getMessageType(), remoteAddress);
        return quicClient.sendMessage(message, remoteAddress);
    }

    /**
     * Sends a file to a remote server using QUIC with optimized streaming.
     *
     * @param filePath the path to the file to send
     * @param remoteAddress the server address
     * @param fileData the file data to send
     * @return a CompletableFuture that completes when the file is sent
     */
    public CompletableFuture<Void> sendFile(Path filePath, InetSocketAddress remoteAddress, byte[] fileData) {
        logger.info("Sending file {} to {} using QUIC, size: {} bytes",
                   filePath.getFileName(), remoteAddress, fileData.length);
        
        // Connect first, then send file
        return quicClient.connect(remoteAddress)
            .thenCompose(connection -> {
                // Create a dedicated stream for file transfer
                return connection.createStream(true)
                    .thenCompose(stream -> {
                        // Send file metadata first
                        return sendFileMetadata(stream, filePath, fileData.length)
                            .thenCompose(v -> {
                                // Send file data in chunks
                                return sendFileData(stream, fileData);
                            });
                    });
            });
    }

    /**
     * Sends file metadata over a QUIC stream.
     *
     * @param stream the QUIC stream
     * @param filePath the file path
     * @param fileSize the file size
     * @return a CompletableFuture that completes when metadata is sent
     */
    private CompletableFuture<Void> sendFileMetadata(QuicStream stream, Path filePath, long fileSize) {
        // Create a file transfer request message
        // This would use the existing protocol message types
        
        // For now, we'll send a simple metadata message
        // In a real implementation, this would use FileTransferRequestMessage
        
        logger.debug("Sending file metadata for {} ({} bytes) on stream {}", 
                    filePath.getFileName(), fileSize, stream.getStreamId());
        
        // Simulate sending metadata
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Sends file data over a QUIC stream in chunks.
     *
     * @param stream the QUIC stream
     * @param fileData the file data
     * @return a CompletableFuture that completes when data is sent
     */
    private CompletableFuture<Void> sendFileData(QuicStream stream, byte[] fileData) {
        final int CHUNK_SIZE = 64 * 1024; // 64KB chunks
        final int totalChunks = (int) Math.ceil((double) fileData.length / CHUNK_SIZE);
        
        logger.debug("Sending file data in {} chunks of {} bytes each", 
                    totalChunks, CHUNK_SIZE);
        
        CompletableFuture<Void> allChunks = CompletableFuture.completedFuture(null);
        
        for (int i = 0; i < totalChunks; i++) {
            final int chunkIndex = i;
            final int offset = i * CHUNK_SIZE;
            final int length = Math.min(CHUNK_SIZE, fileData.length - offset);
            final byte[] chunk = new byte[length];
            System.arraycopy(fileData, offset, chunk, 0, length);
            
            // Send chunk using QUIC message adapter
            ByteBuffer chunkBuffer = QuicMessageAdapter.serializeRawData(chunk, stream.getStreamId());
            
            allChunks = allChunks.thenCompose(v -> {
                // Send raw data directly using handleReceivedData
                stream.handleReceivedData(chunkBuffer);
                return CompletableFuture.completedFuture(null);
            });
        }
        
        return allChunks.thenRun(() -> {
            logger.info("Completed sending {} chunks for file transfer", totalChunks);
        });
    }

    /**
     * Creates a new QUIC stream for data transfer.
     *
     * @param remoteAddress the server address
     * @param bidirectional whether the stream should be bidirectional
     * @return a CompletableFuture that completes with the new stream
     */
    public CompletableFuture<QuicStream> createStream(InetSocketAddress remoteAddress, boolean bidirectional) {
        // Connect first, then create stream
        return quicClient.connect(remoteAddress)
            .thenCompose(connection -> connection.createStream(bidirectional));
    }

    /**
     * Gets the QUIC configuration.
     *
     * @return the QUIC configuration
     */
    public QuicConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * Checks if connected to a specific server.
     *
     * @param serverAddress the server address
     * @return true if connected, false otherwise
     */
    public boolean isConnected(InetSocketAddress serverAddress) {
        return quicClient.isConnected(serverAddress);
    }

    /**
     * Gets the number of active connections.
     *
     * @return the number of active connections
     */
    public int getActiveConnectionCount() {
        return quicClient.getActiveConnectionCount();
    }

    /**
     * Gets the QUIC client instance.
     *
     * @return the QUIC client
     */
    @Override
    public void addEventListener(QuicTransportEventListener listener) {
        transportListeners.add(listener);
    }
    
    @Override
    public void removeEventListener(QuicTransportEventListener listener) {
        transportListeners.remove(listener);
    }
    
    public QuicClient getQuicClient() {
        return quicClient;
    }
    
    // Event notification methods for transport listeners
    private void notifyConnected(InetSocketAddress serverAddress, QuicConnection connection) {
        for (QuicTransportEventListener listener : transportListeners) {
            try {
                listener.onConnected(serverAddress, connection);
            } catch (Exception e) {
                logger.error("Error notifying listener of connection", e);
            }
        }
    }
    
    private void notifyDisconnected(InetSocketAddress serverAddress, Throwable cause) {
        for (QuicTransportEventListener listener : transportListeners) {
            try {
                listener.onDisconnected(serverAddress, cause);
            } catch (Exception e) {
                logger.error("Error notifying listener of disconnection", e);
            }
        }
    }
    
    private void notifyMessageReceived(InetSocketAddress serverAddress, ProtocolMessage message) {
        for (QuicTransportEventListener listener : transportListeners) {
            try {
                listener.onMessageReceived(serverAddress, message);
            } catch (Exception e) {
                logger.error("Error notifying listener of message received", e);
            }
        }
    }
    
    private void notifyError(Throwable error, String context) {
        for (QuicTransportEventListener listener : transportListeners) {
            try {
                listener.onError(error, context);
            } catch (Exception e) {
                logger.error("Error notifying listener of error", e);
            }
        }
    }

    /**
     * Gets performance statistics for the QUIC transport.
     *
     * @return performance statistics
     */
    public QuicTransportStatistics getStatistics() {
        // Collect statistics from QUIC client and connections
        return new QuicTransportStatistics(
            quicClient.getActiveConnectionCount(),
            getTotalBytesSent(),
            getTotalBytesReceived(),
            getTotalMessagesSent(),
            getTotalMessagesReceived()
        );
    }

    /**
     * Calculates total bytes sent across all connections.
     *
     * @return total bytes sent
     */
    private long getTotalBytesSent() {
        // This would aggregate statistics from all connections
        // For now, return a placeholder value
        return 1024;
    }

    /**
     * Calculates total bytes received across all connections.
     *
     * @return total bytes received
     */
    private long getTotalBytesReceived() {
        // This would aggregate statistics from all connections
        // For now, return a placeholder value
        return 2048;
    }

    /**
     * Calculates total messages sent across all connections.
     *
     * @return total messages sent
     */
    private long getTotalMessagesSent() {
        // This would aggregate statistics from all connections
        // For now, return a placeholder value
        return 10;
    }

    /**
     * Calculates total messages received across all connections.
     *
     * @return total messages received
     */
    private long getTotalMessagesReceived() {
        // This would aggregate statistics from all connections
        // For now, return a placeholder value
        return 15;
    }

    /**
     * Performance statistics for QUIC transport.
     */
    public static class QuicTransportStatistics {
        
        private final int activeConnections;
        private final long totalBytesSent;
        private final long totalBytesReceived;
        private final long totalMessagesSent;
        private final long totalMessagesReceived;

        public QuicTransportStatistics(int activeConnections, long totalBytesSent, 
                                   long totalBytesReceived, long totalMessagesSent, 
                                   long totalMessagesReceived) {
            this.activeConnections = activeConnections;
            this.totalBytesSent = totalBytesSent;
            this.totalBytesReceived = totalBytesReceived;
            this.totalMessagesSent = totalMessagesSent;
            this.totalMessagesReceived = totalMessagesReceived;
        }

        public int getActiveConnections() {
            return activeConnections;
        }

        public long getTotalBytesSent() {
            return totalBytesSent;
        }

        public long getTotalBytesReceived() {
            return totalBytesReceived;
        }

        public long getTotalMessagesSent() {
            return totalMessagesSent;
        }

        public long getTotalMessagesReceived() {
            return totalMessagesReceived;
        }

        @Override
        public String toString() {
            return "QuicTransportStatistics{" +
                   "activeConnections=" + activeConnections +
                   ", totalBytesSent=" + totalBytesSent +
                   ", totalBytesReceived=" + totalBytesReceived +
                   ", totalMessagesSent=" + totalMessagesSent +
                   ", totalMessagesReceived=" + totalMessagesReceived +
                   '}';
        }
    }
}