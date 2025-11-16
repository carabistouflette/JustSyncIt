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

import com.justsyncit.ServiceException;
import com.justsyncit.network.protocol.ProtocolMessage;
import com.justsyncit.network.server.TcpServer;
import com.justsyncit.network.client.TcpClient;
import com.justsyncit.network.connection.Connection;
import com.justsyncit.network.connection.ConnectionManager;
import com.justsyncit.network.transfer.FileTransferManager;
import com.justsyncit.network.transfer.FileTransferResult;
import com.justsyncit.network.quic.adapter.QuicTransportAdapter;
import com.justsyncit.network.quic.QuicServer;
import com.justsyncit.network.quic.QuicClient;
import com.justsyncit.network.quic.QuicConnection;
import com.justsyncit.network.quic.QuicStream;
import com.justsyncit.network.quic.QuicConfiguration;
import com.justsyncit.network.quic.QuicTransport;
import com.justsyncit.storage.ContentStore;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
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

    /** QUIC transport adapter. */
    private final QuicTransport quicTransport;
    /** QUIC server component. */
    private final QuicServer quicServer;
    /** QUIC configuration. */
    private final QuicConfiguration quicConfiguration;
    /** Default transport type for new connections. */
    private volatile TransportType defaultTransportType;
    /** Map of connection addresses to their transport types. */
    private final ConcurrentHashMap<InetSocketAddress, TransportType> connectionTransports;

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
        this(tcpServer, tcpClient, connectionManager, fileTransferManager,
                QuicConfiguration.defaultConfiguration(), TransportType.TCP);
    }

    /**
     * Creates a new NetworkService implementation with QUIC support.
     *
     * @param tcpServer the TCP server component
     * @param tcpClient the TCP client component
     * @param connectionManager the connection manager
     * @param fileTransferManager the file transfer manager
     * @param quicConfiguration the QUIC configuration
     * @param defaultTransportType the default transport type for new connections
     */
    public NetworkServiceImpl(TcpServer tcpServer, TcpClient tcpClient,
                          ConnectionManager connectionManager, FileTransferManager fileTransferManager,
                          QuicConfiguration quicConfiguration, TransportType defaultTransportType) {
        this(tcpServer, tcpClient, connectionManager, fileTransferManager,
                new QuicTransportAdapter(quicConfiguration), quicConfiguration, defaultTransportType);
    }

    /**
     * Creates a new NetworkService implementation with QUIC transport injection.
     * Follows Dependency Inversion Principle by accepting QuicTransport interface.
     *
     * @param tcpServer the TCP server component
     * @param tcpClient the TCP client component
     * @param connectionManager the connection manager
     * @param fileTransferManager the file transfer manager
     * @param quicTransport the QUIC transport implementation
     * @param quicConfiguration the QUIC configuration
     * @param defaultTransportType the default transport type for new connections
     */
    public NetworkServiceImpl(TcpServer tcpServer, TcpClient tcpClient,
                          ConnectionManager connectionManager, FileTransferManager fileTransferManager,
                          QuicTransport quicTransport, QuicConfiguration quicConfiguration,
                          TransportType defaultTransportType) {
        this.tcpServer = Objects.requireNonNull(tcpServer, "tcpServer cannot be null");
        this.tcpClient = Objects.requireNonNull(tcpClient, "tcpClient cannot be null");
        this.fileTransferManager = Objects.requireNonNull(fileTransferManager, "fileTransferManager cannot be null");
        this.connectionManager = Objects.requireNonNull(connectionManager, "connectionManager cannot be null");
        this.quicTransport = Objects.requireNonNull(quicTransport, "quicTransport cannot be null");
        this.quicConfiguration = Objects.requireNonNull(quicConfiguration, "quicConfiguration cannot be null");
        this.defaultTransportType = Objects.requireNonNull(defaultTransportType, "defaultTransportType cannot be null");
        this.statistics = new NetworkStatisticsImpl();
        this.listeners = new CopyOnWriteArrayList<>();
        this.running = new AtomicBoolean(false);
        this.connectionTransports = new ConcurrentHashMap<>();

        // Initialize QUIC server
        this.quicServer = new QuicServer(quicConfiguration);

        // Register event listeners with components
        setupEventListeners();
    }

    /**
     * Sets up event listeners for all network components.
     */
    private void setupEventListeners() {
        setupTcpServerEventListeners();
        setupTcpClientEventListeners();
        setupQuicServerEventListeners();
        setupQuicClientEventListeners();
        setupFileTransferEventListeners();
    }

    /**
     * Sets up TCP server event listeners.
     */
    private void setupTcpServerEventListeners() {
        tcpServer.addServerEventListener(new TcpServer.ServerEventListener() {
            @Override
            public void onClientConnected(InetSocketAddress clientAddress) {
                // Connection will be managed by the server component
                connectionTransports.put(clientAddress, TransportType.TCP);
                notifyConnectionEstablished(clientAddress);
                statistics.incrementActiveConnections();
                logger.info("TCP client connected: {}", clientAddress);
            }

            @Override
            public void onClientDisconnected(InetSocketAddress clientAddress, Throwable cause) {
                // Connection will be managed by the server component
                connectionTransports.remove(clientAddress);
                notifyConnectionClosed(clientAddress, cause);
                statistics.decrementActiveConnections();
                logger.info("TCP client disconnected: {}", clientAddress, cause);
            }

            @Override
            public void onMessageReceived(InetSocketAddress clientAddress, ProtocolMessage message) {
                notifyMessageReceived(message, clientAddress);
                statistics.incrementBytesReceived(message.getTotalSize());
            }

            @Override
            public void onError(Throwable error, String context) {
                notifyError(error, context);
                logger.error("TCP server error in {}: {}", context, error);
            }
        });
    }

    /**
     * Sets up TCP client event listeners.
     */
    private void setupTcpClientEventListeners() {
        tcpClient.addClientEventListener(new TcpClient.ClientEventListener() {
            @Override
            public void onConnected(InetSocketAddress serverAddress) {
                // Connection will be managed by the client component
                connectionTransports.put(serverAddress, TransportType.TCP);
                notifyConnectionEstablished(serverAddress);
                statistics.incrementActiveConnections();
                logger.info("TCP connected to server: {}", serverAddress);
            }

            @Override
            public void onDisconnected(InetSocketAddress serverAddress, Throwable cause) {
                // Connection will be managed by the client component
                connectionTransports.remove(serverAddress);
                notifyConnectionClosed(serverAddress, cause);
                statistics.decrementActiveConnections();
                logger.info("TCP disconnected from server: {}", serverAddress, cause);
            }

            @Override
            public void onMessageReceived(InetSocketAddress serverAddress, ProtocolMessage message) {
                notifyMessageReceived(message, serverAddress);
                statistics.incrementBytesReceived(message.getTotalSize());
            }

            @Override
            public void onError(Throwable error, String context) {
                notifyError(error, context);
                logger.error("TCP client error in {}: {}", context, error);
            }
        });
    }

    /**
     * Sets up QUIC server event listeners.
     */
    private void setupQuicServerEventListeners() {
        quicServer.addEventListener(new QuicServer.QuicServerEventListener() {
            @Override
            public void onClientConnected(InetSocketAddress clientAddress, QuicConnection connection) {
                connectionTransports.put(clientAddress, TransportType.QUIC);
                notifyConnectionEstablished(clientAddress);
                statistics.incrementActiveConnections();
                logger.info("QUIC client connected: {}", clientAddress);
            }

            @Override
            public void onClientDisconnected(InetSocketAddress clientAddress, Throwable cause) {
                connectionTransports.remove(clientAddress);
                notifyConnectionClosed(clientAddress, cause);
                statistics.decrementActiveConnections();
                logger.info("QUIC client disconnected: {}", clientAddress, cause);
            }

            @Override
            public void onMessageReceived(InetSocketAddress clientAddress, ProtocolMessage message, long streamId) {
                notifyMessageReceived(message, clientAddress);
                statistics.incrementBytesReceived(message.getTotalSize());
            }

            @Override
            public void onStreamCreated(InetSocketAddress clientAddress, QuicStream stream) {
                logger.debug("QUIC stream created for client {}: {}", clientAddress, stream.getStreamId());
            }

            @Override
            public void onStreamClosed(InetSocketAddress clientAddress, long streamId, Throwable cause) {
                logger.debug("QUIC stream {} closed for client {}: {}", streamId, clientAddress,
                           cause != null ? cause.getMessage() : "normal");
            }

            @Override
            public void onError(Throwable error, String context) {
                notifyError(error, context);
                logger.error("QUIC server error in {}: {}", context, error);
            }
        });
    }

    /**
     * Sets up QUIC client event listeners.
     */
    private void setupQuicClientEventListeners() {
        // Cast to QuicTransportAdapter to access client for event setup
        if (quicTransport instanceof QuicTransportAdapter) {
            QuicTransportAdapter adapter = (QuicTransportAdapter) quicTransport;
            adapter.getQuicClient().addEventListener(new QuicClient.QuicClientEventListener() {
                @Override
                public void onConnected(InetSocketAddress serverAddress, QuicConnection connection) {
                    connectionTransports.put(serverAddress, TransportType.QUIC);
                    notifyConnectionEstablished(serverAddress);
                    statistics.incrementActiveConnections();
                    logger.info("QUIC connected to server: {}", serverAddress);
                }

                @Override
                public void onDisconnected(InetSocketAddress serverAddress, Throwable cause) {
                    connectionTransports.remove(serverAddress);
                    notifyConnectionClosed(serverAddress, cause);
                    statistics.decrementActiveConnections();
                    logger.info("QUIC disconnected from server: {}", serverAddress, cause);
                }

                @Override
                public void onMessageReceived(InetSocketAddress serverAddress, ProtocolMessage message) {
                    notifyMessageReceived(message, serverAddress);
                    statistics.incrementBytesReceived(message.getTotalSize());
                }

                @Override
                public void onError(Throwable error, String context) {
                    notifyError(error, context);
                    logger.error("QUIC client error in {}: {}", context, error);
                }
            });
        }
    }

    /**
     * Sets up file transfer event listeners.
     */
    private void setupFileTransferEventListeners() {
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
    public CompletableFuture<Void> startServer(int port) throws IOException, ServiceException {
        return startServer(port, TransportType.TCP);
    }

    @Override
    public CompletableFuture<Void> startServer(int port, TransportType transportType)
            throws IOException, ServiceException {
        if (running.compareAndSet(false, true)) {
            statistics.start();
            return fileTransferManager.start()
                .thenCompose(v -> {
                    try {
                        if (transportType == TransportType.QUIC) {
                            return quicTransport.start()
                                .thenCompose(v2 -> quicServer.start(port));
                        } else {
                            return tcpServer.start(port);
                        }
                    } catch (IOException e) {
                        return CompletableFuture.failedFuture(e);
                    } catch (ServiceException e) {
                        return CompletableFuture.failedFuture(e);
                    }
                })
                .thenRun(() -> logger.info("Network service started on port {} using {}", port, transportType))
                .exceptionally(throwable -> {
                    running.set(false);
                    logger.error("Failed to start network service on port {} using {}", port, transportType, throwable);
                    return null;
                });
        } else {
            return CompletableFuture.failedFuture(new IllegalStateException("Network service is already running"));
        }
    }

    @Override
    public CompletableFuture<Void> stopServer() {
        if (running.compareAndSet(true, false)) {
            return CompletableFuture.allOf(
                tcpServer.stop(),
                quicServer.stop(),
                quicTransport.stop()
            )
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
        return connectToNode(address, defaultTransportType);
    }

    @Override
    public CompletableFuture<Void> connectToNode(InetSocketAddress address, TransportType transportType)
            throws IOException {
        if (transportType == TransportType.QUIC) {
            return quicTransport.connect(address)
                .thenAccept(connection -> {
                    logger.info("Connected to node via QUIC: {}", address);
                })
                .exceptionally(throwable -> {
                    logger.error("Failed to connect to node via QUIC: {}", address, throwable);
                    return null;
                });
        } else {
            CompletableFuture<Connection> connectionFuture = connectionManager.connectToNode(address);
            CompletableFuture<Void> result = new CompletableFuture<>();

            connectionFuture
                    .thenAccept(connection -> {
                        logger.info("Connected to node via TCP: {}", address);
                        result.complete(null);
                    })
                    .exceptionally(throwable -> {
                        logger.error("Failed to connect to node via TCP: {}", address, throwable);
                        result.completeExceptionally(throwable);
                        return null;
                    });

            return result;
        }
    }

    @Override
    public CompletableFuture<Void> disconnectFromNode(InetSocketAddress address) {
        TransportType transportType = connectionTransports.get(address);
        if (transportType == TransportType.QUIC) {
            return quicTransport.disconnect(address)
                .thenRun(() -> logger.info("Disconnected from node via QUIC: {}", address))
                .exceptionally(throwable -> {
                    logger.error("Failed to disconnect from node via QUIC: {}", address, throwable);
                    return null;
                });
        } else {
            return tcpClient.disconnect(address)
                .thenRun(() -> logger.info("Disconnected from node via TCP: {}", address))
                .exceptionally(throwable -> {
                    logger.error("Failed to disconnect from node via TCP: {}", address, throwable);
                    return null;
                });
        }
    }

    @Override
    public CompletableFuture<FileTransferResult> sendFile(Path filePath, InetSocketAddress remoteAddress,
            ContentStore contentStore) throws IOException {
        return sendFile(filePath, remoteAddress, contentStore, defaultTransportType);
    }

    @Override
    public CompletableFuture<FileTransferResult> sendFile(Path filePath, InetSocketAddress remoteAddress,
            ContentStore contentStore, TransportType transportType) throws IOException {
        if (transportType == TransportType.QUIC) {
            // For QUIC, we need to read the file data and send it via the QUIC transport
            byte[] fileData = Files.readAllBytes(filePath);
            return quicTransport.sendFile(filePath, remoteAddress, fileData)
                .thenCompose(v -> {
                    // Update statistics with bytes sent for file transfer
                    try {
                        long fileSize = Files.size(filePath);
                        statistics.incrementBytesSent(fileSize);
                        statistics.incrementMessagesSent(); // Count file transfer as a message
                    } catch (IOException e) {
                        logger.warn("Could not update bytes sent statistics for file transfer", e);
                    }
                    logger.info("File sent via QUIC: {} to {}", filePath, remoteAddress);
                    long now = System.currentTimeMillis();
                    return CompletableFuture.completedFuture(FileTransferResult.success(
                            "unknown", filePath, remoteAddress, fileData.length, fileData.length, now, now
                    ));
                })
                .exceptionally(throwable -> {
                    logger.error("Failed to send file via QUIC: {} to {}", filePath, remoteAddress, throwable);
                    long now = System.currentTimeMillis();
                    return FileTransferResult.failure(
                            "unknown", filePath, remoteAddress, throwable.getMessage(), 0, now, now
                    );
                });
        } else {
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
                        logger.info("File sent via TCP: {} to {}", filePath, remoteAddress);
                        return result;
                    })
                    .exceptionally(throwable -> {
                        logger.error("Failed to send file via TCP: {} to {}", filePath, remoteAddress, throwable);
                        long now = System.currentTimeMillis();
                        return FileTransferResult.failure(
                                "unknown", filePath, remoteAddress, throwable.getMessage(), 0, now, now
                        );
                    });
        }
    }

    @Override
    public CompletableFuture<Void> sendMessage(ProtocolMessage message, InetSocketAddress remoteAddress)
            throws IOException {
        return sendMessage(message, remoteAddress, defaultTransportType);
    }

    @Override
    public CompletableFuture<Void> sendMessage(ProtocolMessage message, InetSocketAddress remoteAddress,
            TransportType transportType) throws IOException {
        if (transportType == TransportType.QUIC) {
            return quicTransport.sendMessage(message, remoteAddress)
                .thenRun(() -> {
                    statistics.incrementBytesSent(message.getTotalSize());
                    logger.debug("Message sent via QUIC to {}: {}", remoteAddress, message.getMessageType());
                })
                .exceptionally(throwable -> {
                    logger.error("Failed to send message via QUIC to {}: {}",
                               remoteAddress, message.getMessageType(), throwable);
                    return null;
                });
        } else {
            CompletableFuture<Void> future;

            Connection connection = connectionManager.getConnection(remoteAddress);
            if (connection != null && connection.getConnectionType() == Connection.ConnectionType.SERVER) {
                future = tcpServer.sendMessage(message, remoteAddress);
            } else {
                future = tcpClient.sendMessage(message, remoteAddress);
            }

            return future.thenRun(() -> {
                statistics.incrementBytesSent(message.getTotalSize());
                logger.debug("Message sent via TCP to {}: {}", remoteAddress, message.getMessageType());
            }).exceptionally(throwable -> {
                logger.error("Failed to send message via TCP to {}: {}",
                           remoteAddress, message.getMessageType(), throwable);
                return null;
            });
        }
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
        if (tcpServer.isRunning()) {
            return tcpServer.getPort();
        } else if (quicServer.isRunning()) {
            return quicServer.getPort();
        } else {
            return -1;
        }
    }

    @Override
    public int getActiveConnectionCount() {
        // Combine TCP and QUIC connection counts
        int tcpConnections = connectionManager.getActiveConnectionCount();
        int quicConnections = quicTransport.getActiveConnectionCount();
        return tcpConnections + quicConnections;
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
        quicTransport.stop();
        listeners.clear();
        connectionTransports.clear();
        logger.info("Network service closed");
    }

    @Override
    public TransportType getConnectionTransportType(InetSocketAddress remoteAddress) {
        return connectionTransports.get(remoteAddress);
    }

    @Override
    public TransportType getDefaultTransportType() {
        return defaultTransportType;
    }

    @Override
    public void setDefaultTransportType(TransportType transportType) {
        this.defaultTransportType = Objects.requireNonNull(transportType, "transportType cannot be null");
        logger.info("Default transport type set to: {}", transportType);
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