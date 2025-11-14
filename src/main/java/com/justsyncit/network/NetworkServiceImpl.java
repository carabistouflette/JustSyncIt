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

package com.justsyncit.network;

import com.justsyncit.network.protocol.ProtocolMessage;
import com.justsyncit.network.server.TcpServer;
import com.justsyncit.network.client.TcpClient;
import com.justsyncit.network.connection.Connection;
import com.justsyncit.network.connection.ConnectionManager;
import com.justsyncit.network.transfer.FileTransferManager;
import com.justsyncit.network.transfer.FileTransferResult;
import com.justsyncit.storage.ContentStore;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of NetworkService that provides TCP-based file transfer capabilities.
 * Coordinates server, client, connection management, and file transfer components.
 * Follows Single Responsibility Principle by delegating to specialized components.
 */
public class NetworkServiceImpl implements NetworkService {

    /** Logger for network service operations. */
    private static final Logger logger = LoggerFactory.getLogger(NetworkServiceImpl.class);

    /** The TCP server component. */
    private final TcpServer tcpServer;
    /** The TCP client component. */
    private final TcpClient tcpClient;
    /** The connection manager. */
    private final ConnectionManager connectionManager;
    /** The file transfer manager. */
    private final FileTransferManager fileTransferManager;
    /** Network statistics implementation. */
    private final NetworkStatisticsImpl statistics;
    /** List of network event listeners. */
    private final CopyOnWriteArrayList<NetworkEventListener> listeners;
    /** Flag indicating if the service is running. */
    private final AtomicBoolean running;

    /**
     * Creates a new NetworkService implementation.
     *
     * @param tcpServer the TCP server component
     * @param tcpClient the TCP client component
     * @param connectionManager the connection manager
     * @param fileTransferManager the file transfer manager
     */
    public NetworkServiceImpl(TcpServer tcpServer, TcpClient tcpClient,
                          ConnectionManager connectionManager, FileTransferManager fileTransferManager) {
        this.tcpServer = Objects.requireNonNull(tcpServer, "tcpServer cannot be null");
        this.tcpClient = Objects.requireNonNull(tcpClient, "tcpClient cannot be null");
        this.fileTransferManager = Objects.requireNonNull(fileTransferManager, "fileTransferManager cannot be null");
        this.statistics = new NetworkStatisticsImpl();
        this.listeners = new CopyOnWriteArrayList<>();
        this.running = new AtomicBoolean(false);

        // Use the provided connection manager
        this.connectionManager = Objects.requireNonNull(connectionManager, "connectionManager cannot be null");

        // Register event listeners with components
        setupEventListeners();
    }

    /**
     * Sets up event listeners for all network components.
     */
    private void setupEventListeners() {
        // Server event listeners
        tcpServer.addServerEventListener(new TcpServer.ServerEventListener() {
            @Override
            public void onClientConnected(InetSocketAddress clientAddress) {
                // Connection will be managed by the server component
                notifyConnectionEstablished(clientAddress);
                statistics.incrementActiveConnections();
                logger.info("Client connected: {}", clientAddress);
            }

            @Override
            public void onClientDisconnected(InetSocketAddress clientAddress, Throwable cause) {
                // Connection will be managed by the server component
                notifyConnectionClosed(clientAddress, cause);
                statistics.decrementActiveConnections();
                logger.info("Client disconnected: {}", clientAddress, cause);
            }

            @Override
            public void onMessageReceived(InetSocketAddress clientAddress, ProtocolMessage message) {
                notifyMessageReceived(message, clientAddress);
                statistics.incrementBytesReceived(message.getTotalSize());
            }

            @Override
            public void onError(Throwable error, String context) {
                notifyError(error, context);
                logger.error("Server error in {}: {}", context, error);
            }
        });

        // Client event listeners
        tcpClient.addClientEventListener(new TcpClient.ClientEventListener() {
            @Override
            public void onConnected(InetSocketAddress serverAddress) {
                // Connection will be managed by the client component
                notifyConnectionEstablished(serverAddress);
                statistics.incrementActiveConnections();
                logger.info("Connected to server: {}", serverAddress);
            }

            @Override
            public void onDisconnected(InetSocketAddress serverAddress, Throwable cause) {
                // Connection will be managed by the client component
                notifyConnectionClosed(serverAddress, cause);
                statistics.decrementActiveConnections();
                logger.info("Disconnected from server: {}", serverAddress, cause);
            }

            @Override
            public void onMessageReceived(InetSocketAddress serverAddress, ProtocolMessage message) {
                notifyMessageReceived(message, serverAddress);
                statistics.incrementBytesReceived(message.getTotalSize());
            }

            @Override
            public void onError(Throwable error, String context) {
                notifyError(error, context);
                logger.error("Client error in {}: {}", context, error);
            }
        });

        // File transfer event listeners
        fileTransferManager.addTransferEventListener(new FileTransferManager.TransferEventListener() {
            @Override
            public void onTransferStarted(Path filePath, InetSocketAddress remoteAddress, long fileSize) {
                notifyFileTransferStarted(filePath, remoteAddress, fileSize);
                logger.info("File transfer started: {} to {} ({} bytes)", filePath, remoteAddress, fileSize);
            }

            @Override
            public void onTransferProgress(Path filePath, InetSocketAddress remoteAddress,
                                      long bytesTransferred, long totalBytes) {
                notifyFileTransferProgress(filePath, remoteAddress, bytesTransferred, totalBytes);
            }

            @Override
            public void onTransferCompleted(Path filePath, InetSocketAddress remoteAddress,
                                       boolean success, String error) {
                if (success) {
                    statistics.incrementCompletedTransfers();
                } else {
                    statistics.incrementFailedTransfers();
                }
                notifyFileTransferCompleted(filePath, remoteAddress, success, error);
                logger.info("File transfer completed: {} to {} - success: {}", filePath, remoteAddress, success);
            }

            @Override
            public void onError(Throwable error, String context) {
                notifyError(error, context);
                logger.error("File transfer error in {}: {}", context, error);
            }
        });
    }

    @Override
    public CompletableFuture<Void> startServer(int port) throws IOException {
        if (running.compareAndSet(false, true)) {
            statistics.start();
            return fileTransferManager.start()
                .thenCompose(v -> {
                    try {
                        return tcpServer.start(port);
                    } catch (IOException e) {
                        return CompletableFuture.failedFuture(e);
                    }
                })
                .thenRun(() -> logger.info("Network service started on port {}", port))
                .exceptionally(throwable -> {
                    running.set(false);
                    logger.error("Failed to start network service on port {}", port, throwable);
                    return null;
                });
        } else {
            return CompletableFuture.failedFuture(new IllegalStateException("Network service is already running"));
        }
    }

    @Override
    public CompletableFuture<Void> stopServer() {
        if (running.compareAndSet(true, false)) {
            return tcpServer.stop()
                .thenRun(() -> {
                    statistics.stop();
                    logger.info("Network service stopped");
                })
                .exceptionally(throwable -> {
                    logger.error("Failed to stop network service", throwable);
                    return null;
                });
        } else {
            return CompletableFuture.completedFuture(null);
        }
    }

    @Override
    public CompletableFuture<Void> connectToNode(InetSocketAddress address) throws IOException {
        CompletableFuture<Connection> connectionFuture = connectionManager.connectToNode(address);
        CompletableFuture<Void> result = new CompletableFuture<>();

        connectionFuture
                .thenAccept(connection -> {
                    logger.info("Connected to node: {}", address);
                    result.complete(null);
                })
                .exceptionally(throwable -> {
                    logger.error("Failed to connect to node: {}", address, throwable);
                    result.completeExceptionally(throwable);
                    return null;
                });

        return result;
    }

    @Override
    public CompletableFuture<Void> disconnectFromNode(InetSocketAddress address) {
        return tcpClient.disconnect(address)
            .thenRun(() -> logger.info("Disconnected from node: {}", address))
            .exceptionally(throwable -> {
                logger.error("Failed to disconnect from node: {}", address, throwable);
                return null;
            });
    }

    @Override
    public CompletableFuture<FileTransferResult> sendFile(Path filePath, InetSocketAddress remoteAddress,
                                               ContentStore contentStore) throws IOException {
        return fileTransferManager.sendFile(filePath, remoteAddress, contentStore)
                .thenApply(result -> {
                    // Update statistics with bytes sent for file transfer
                    if (result.isSuccess()) {
                        try {
                            long fileSize = Files.size(filePath);
                            statistics.incrementBytesSent(fileSize);
                            statistics.incrementMessagesSent(); // Count file transfer as a message
                        } catch (IOException e) {
                            logger.warn("Could not update bytes sent statistics for file transfer", e);
                        }
                    }
                    logger.info("File sent: {} to {}", filePath, remoteAddress);
                    return result;
                })
                .exceptionally(throwable -> {
                    logger.error("Failed to send file: {} to {}", filePath, remoteAddress, throwable);
                    long now = System.currentTimeMillis();
                    return FileTransferResult.failure(
                            "unknown", filePath, remoteAddress, throwable.getMessage(), 0, now, now
                    );
                });
    }

    @Override
    public CompletableFuture<Void> sendMessage(ProtocolMessage message, InetSocketAddress remoteAddress)
                                                throws IOException {
        CompletableFuture<Void> future;

        Connection connection = connectionManager.getConnection(remoteAddress);
        if (connection != null && connection.getConnectionType() == Connection.ConnectionType.SERVER) {
            future = tcpServer.sendMessage(message, remoteAddress);
        } else {
            future = tcpClient.sendMessage(message, remoteAddress);
        }

        return future.thenRun(() -> {
            statistics.incrementBytesSent(message.getTotalSize());
            logger.debug("Message sent to {}: {}", remoteAddress, message.getMessageType());
        }).exceptionally(throwable -> {
            logger.error("Failed to send message to {}: {}", remoteAddress, message.getMessageType(), throwable);
            return null;
        });
    }

    @Override
    public void addNetworkEventListener(NetworkEventListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener cannot be null"));
    }

    @Override
    public void removeNetworkEventListener(NetworkEventListener listener) {
        listeners.remove(listener);
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public boolean isServerRunning() {
        return tcpServer.isRunning();
    }

    @Override
    public int getServerPort() {
        return tcpServer.isRunning() ? tcpServer.getPort() : -1;
    }

    @Override
    public int getActiveConnectionCount() {
        // Use connection manager's active connection count for accuracy
        return connectionManager.getActiveConnectionCount();
    }

    @Override
    public int getActiveTransferCount() {
        return fileTransferManager.getActiveTransferCount();
    }

    @Override
    public long getBytesSent() {
        return statistics.getTotalBytesSent();
    }

    @Override
    public long getBytesReceived() {
        return statistics.getTotalBytesReceived();
    }

    @Override
    public long getMessagesSent() {
        return statistics.getMessagesSent();
    }

    @Override
    public long getMessagesReceived() {
        return statistics.getMessagesReceived();
    }

    @Override
    public NetworkStatistics getStatistics() {
        return statistics;
    }

    @Override
    public void close() throws IOException {
        stopServer();
        tcpClient.close();
        connectionManager.stop();
        fileTransferManager.stop();
        listeners.clear();
        logger.info("Network service closed");
    }

    // Event notification methods
    private void notifyConnectionEstablished(InetSocketAddress remoteAddress) {
        for (NetworkEventListener listener : listeners) {
            try {
                listener.onConnectionEstablished(remoteAddress);
            } catch (Exception e) {
                logger.error("Error notifying listener of connection established", e);
            }
        }
    }

    private void notifyConnectionClosed(InetSocketAddress remoteAddress, Throwable cause) {
        for (NetworkEventListener listener : listeners) {
            try {
                listener.onConnectionClosed(remoteAddress, cause);
            } catch (Exception e) {
                logger.error("Error notifying listener of connection closed", e);
            }
        }
    }

    private void notifyMessageReceived(ProtocolMessage message, InetSocketAddress remoteAddress) {
        for (NetworkEventListener listener : listeners) {
            try {
                listener.onMessageReceived(message, remoteAddress);
            } catch (Exception e) {
                logger.error("Error notifying listener of message received", e);
            }
        }
    }

    private void notifyFileTransferStarted(Path filePath, InetSocketAddress remoteAddress, long fileSize) {
        for (NetworkEventListener listener : listeners) {
            try {
                listener.onFileTransferStarted(filePath, remoteAddress, fileSize);
            } catch (Exception e) {
                logger.error("Error notifying listener of file transfer started", e);
            }
        }
    }

    private void notifyFileTransferProgress(Path filePath, InetSocketAddress remoteAddress,
                                       long bytesTransferred, long totalBytes) {
        for (NetworkEventListener listener : listeners) {
            try {
                listener.onFileTransferProgress(filePath, remoteAddress, bytesTransferred, totalBytes);
            } catch (Exception e) {
                logger.error("Error notifying listener of file transfer progress", e);
            }
        }
    }

    private void notifyFileTransferCompleted(Path filePath, InetSocketAddress remoteAddress,
                                        boolean success, String error) {
        for (NetworkEventListener listener : listeners) {
            try {
                listener.onFileTransferCompleted(filePath, remoteAddress, success, error);
            } catch (Exception e) {
                logger.error("Error notifying listener of file transfer completed", e);
            }
        }
    }

    private void notifyError(Throwable error, String context) {
        for (NetworkEventListener listener : listeners) {
            try {
                listener.onError(error, context);
            } catch (Exception e) {
                logger.error("Error notifying listener of error", e);
            }
        }
    }

    /**
     * Implementation of NetworkStatistics.
     */
    private static class NetworkStatisticsImpl implements NetworkStatistics {

        /** Counter for total bytes sent. */
        private final AtomicLong totalBytesSent = new AtomicLong(0);
        /** Counter for total bytes received. */
        private final AtomicLong totalBytesReceived = new AtomicLong(0);
        /** Counter for active connections. */
        private final AtomicLong activeConnections = new AtomicLong(0);
        /** Counter for completed transfers. */
        private final AtomicLong completedTransfers = new AtomicLong(0);
        /** Counter for failed transfers. */
        private final AtomicLong failedTransfers = new AtomicLong(0);
        /** Counter for messages sent. */
        private final AtomicLong messagesSent = new AtomicLong(0);
        /** Counter for messages received. */
        private final AtomicLong messagesReceived = new AtomicLong(0);
        /** Start time in milliseconds. */
        private volatile long startTime;
        /** End time in milliseconds. */
        private volatile long endTime;

        public void start() {
            startTime = System.currentTimeMillis();
            endTime = 0;
        }

        public void stop() {
            endTime = System.currentTimeMillis();
        }

        public void incrementBytesSent(long bytes) {
            totalBytesSent.addAndGet(bytes);
        }

        public void incrementBytesReceived(long bytes) {
            totalBytesReceived.addAndGet(bytes);
        }

        public void incrementActiveConnections() {
            activeConnections.incrementAndGet();
        }

        public void decrementActiveConnections() {
            activeConnections.decrementAndGet();
        }

        public void incrementCompletedTransfers() {
            completedTransfers.incrementAndGet();
        }

        public void incrementFailedTransfers() {
            failedTransfers.incrementAndGet();
        }

        public void incrementMessagesSent() {
            messagesSent.incrementAndGet();
        }

        public void incrementMessagesReceived() {
            messagesReceived.incrementAndGet();
        }

        @Override
        public long getTotalBytesSent() {
            return totalBytesSent.get();
        }

        @Override
        public long getTotalBytesReceived() {
            return totalBytesReceived.get();
        }

        @Override
        public int getActiveConnections() {
            return (int) activeConnections.get();
        }

        @Override
        public long getCompletedTransfers() {
            return completedTransfers.get();
        }

        @Override
        public long getFailedTransfers() {
            return failedTransfers.get();
        }

        @Override
        public double getAverageTransferRate() {
            long uptime = getUptimeMillis();
            if (uptime <= 0) {
                return 0.0;
            }
            long totalBytes = totalBytesSent.get() + totalBytesReceived.get();
            return (double) totalBytes / (uptime / 1000.0); // bytes per second
        }

        @Override
        public long getUptimeMillis() {
            if (startTime == 0) {
                return 0;
            }
            long current = endTime > 0 ? endTime : System.currentTimeMillis();
            return current - startTime;
        }

        public long getMessagesSent() {
            return messagesSent.get();
        }

        public long getMessagesReceived() {
            return messagesReceived.get();
        }

        @Override
        public long getTotalMessagesSent() {
            return messagesSent.get();
        }

        @Override
        public long getTotalMessagesReceived() {
            return messagesReceived.get();
        }
    }
}