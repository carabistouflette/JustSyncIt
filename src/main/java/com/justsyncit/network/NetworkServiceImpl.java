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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of NetworkService that provides TCP-based file transfer
 * capabilities.
 * Coordinates server, client, connection management, and file transfer
 * components.
 * Follows Single Responsibility Principle by delegating to specialized
 * components.
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
    /** Default transport type for new connections. */
    private volatile TransportType defaultTransportType;
    /** Map of connection addresses to their transport types. */
    private final ConcurrentHashMap<InetSocketAddress, TransportType> connectionTransports;

    /** Handler for secure transfers. */
    private final com.justsyncit.network.transfer.SecureTransferHandler secureTransferHandler;
    private final byte[] clusterKey;

    /** Handler for QUIC transfers. */
    private final com.justsyncit.network.transfer.QuicTransportHandler quicTransportHandler;

    public NetworkServiceImpl(TcpServer tcpServer, TcpClient tcpClient, FileTransferManager fileTransferManager,
            ConnectionManager connectionManager, com.justsyncit.hash.Blake3Service blake3Service,
            QuicTransport quicTransport, QuicConfiguration quicConfiguration,
            TransportType defaultTransportType,
            com.justsyncit.network.encryption.EncryptionService encryptionService,
            byte[] clusterKey) {
        this.tcpServer = tcpServer;
        this.tcpClient = tcpClient;
        this.fileTransferManager = fileTransferManager;
        this.connectionManager = connectionManager;
        this.quicTransport = quicTransport;
        this.defaultTransportType = defaultTransportType;
        this.clusterKey = clusterKey;
        this.quicServer = new QuicServer(quicConfiguration);
        this.connectionTransports = new ConcurrentHashMap<>();
        this.statistics = new NetworkStatisticsImpl();
        this.listeners = new CopyOnWriteArrayList<>();
        this.running = new AtomicBoolean(false);

        // Initialize helpers
        this.secureTransferHandler = new com.justsyncit.network.transfer.SecureTransferHandler(blake3Service,
                encryptionService);
        this.quicTransportHandler = new com.justsyncit.network.transfer.QuicTransportHandler(quicTransport, statistics);
    }

    // ...

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
                logger.debug("TCP client connected: {}", clientAddress);
            }

            @Override
            public void onClientDisconnected(InetSocketAddress clientAddress, Throwable cause) {
                // Connection will be managed by the server component
                connectionTransports.remove(clientAddress);
                notifyConnectionClosed(clientAddress, cause);
                statistics.decrementActiveConnections();
                logger.debug("TCP client disconnected: {}", clientAddress, cause);
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
                logger.debug("TCP connected to server: {}", serverAddress);
            }

            @Override
            public void onDisconnected(InetSocketAddress serverAddress, Throwable cause) {
                // Connection will be managed by the client component
                connectionTransports.remove(serverAddress);
                notifyConnectionClosed(serverAddress, cause);
                statistics.decrementActiveConnections();
                logger.debug("TCP disconnected from server: {}", serverAddress, cause);
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
                logger.debug("QUIC client connected: {}", clientAddress);
            }

            @Override
            public void onClientDisconnected(InetSocketAddress clientAddress, Throwable cause) {
                connectionTransports.remove(clientAddress);
                notifyConnectionClosed(clientAddress, cause);
                statistics.decrementActiveConnections();
                logger.debug("QUIC client disconnected: {}", clientAddress, cause);
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
                    logger.debug("QUIC connected to server: {}", serverAddress);
                }

                @Override
                public void onDisconnected(InetSocketAddress serverAddress, Throwable cause) {
                    connectionTransports.remove(serverAddress);
                    notifyConnectionClosed(serverAddress, cause);
                    statistics.decrementActiveConnections();
                    logger.debug("QUIC disconnected from server: {}", serverAddress, cause);
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
            public void onTransferProgress(Path filePath, InetSocketAddress remoteAddress, long bytesTransferred,
                    long totalBytes) {
                notifyFileTransferProgress(filePath, remoteAddress, bytesTransferred, totalBytes);
            }

            @Override
            public void onTransferCompleted(Path filePath, InetSocketAddress remoteAddress, boolean success,
                    String error) {
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
            return CompletableFuture.allOf(fileTransferManager.start(), connectionManager.start()).thenCompose(v -> {
                try {
                    if (transportType == TransportType.QUIC) {
                        return quicTransport.start().thenCompose(v2 -> quicServer.start(port));
                    } else {
                        return tcpServer.start(port);
                    }
                } catch (IOException e) {
                    return CompletableFuture.failedFuture(e);
                } catch (ServiceException e) {
                    return CompletableFuture.failedFuture(e);
                }
            }).thenRun(() -> logger.info("Network service started on port {} using {}", port, transportType))
                    .exceptionally(throwable -> {
                        running.set(false);
                        logger.error("Failed to start network service on port {} using {}", port, transportType,
                                throwable);
                        return null;
                    });
        } else {
            return CompletableFuture.failedFuture(new IllegalStateException("Network service is already running"));
        }
    }

    @Override
    public CompletableFuture<Void> stopServer() {
        if (running.compareAndSet(true, false)) {
            return CompletableFuture
                    .allOf(tcpServer.stop(), quicServer.stop(), quicTransport.stop(), connectionManager.stop())
                    .thenRun(() -> {
                        statistics.stop();
                        logger.info("Network service stopped");
                    }).exceptionally(throwable -> {
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
            return quicTransport.connect(address).thenAccept(connection -> {
                logger.debug("Connected to node via QUIC: {}", address);
            }).exceptionally(throwable -> {
                logger.error("Failed to connect to node via QUIC: {}", address, throwable);
                return null;
            });
        } else {
            CompletableFuture<Connection> connectionFuture = connectionManager.connectToNode(address);
            CompletableFuture<Void> result = new CompletableFuture<>();

            connectionFuture.thenAccept(connection -> {
                logger.debug("Connected to node via TCP: {}", address);
                result.complete(null);
            }).exceptionally(throwable -> {
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
                    .thenRun(() -> logger.debug("Disconnected from node via QUIC: {}", address))
                    .exceptionally(throwable -> {
                        logger.error("Failed to disconnect from node via QUIC: {}", address, throwable);
                        return null;
                    });
        } else {
            return tcpClient.disconnect(address)
                    .thenRun(() -> logger.debug("Disconnected from node via TCP: {}", address))
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
            // For QUIC, send the file in chunks to avoid OOM
            // We reuse the sendFilePart logic which sends chunks as ChunkDataMessages
            long fileSize = Files.size(filePath);
            long chunkSize = 1024 * 1024; // 1 MB chunks

            // Limit concurrency to prevent creating too many futures at once for massive
            // files
            // Simple approach: Sequential chaining for reliability, or Batched.
            // Given sendFilePart is async, simple chaining is safest for resource usage.

            return sendFileChunksSequentially(filePath, remoteAddress, 0, fileSize, chunkSize, transportType)
                    .thenApply(v -> {
                        statistics.incrementMessagesSent();
                        logger.debug("File sent via QUIC (chunked): {} to {}", filePath, remoteAddress);
                        long now = System.currentTimeMillis();
                        return FileTransferResult.success("unknown", filePath, remoteAddress,
                                fileSize, fileSize, now, now);
                    }).exceptionally(throwable -> {
                        logger.error("Failed to send file via QUIC: {} to {}", filePath, remoteAddress, throwable);
                        long now = System.currentTimeMillis();
                        return FileTransferResult.failure("unknown", filePath, remoteAddress, throwable.getMessage(), 0,
                                now,
                                now);
                    });
        } else {
            return fileTransferManager.sendFile(filePath, remoteAddress, contentStore).thenApply(result -> {
                // Update statistics with bytes sent for file transfer
                if (result.isSuccess()) {
                    try {
                        long fileSize = Files.size(filePath);
                        statistics.incrementBytesSent(fileSize);
                        statistics.incrementMessagesSent(); // Count
                                                            // file
                                                            // transfer
                                                            // as
                                                            // a
                                                            // message
                    } catch (IOException e) {
                        logger.warn("Could not update bytes sent statistics for file transfer", e);
                    }
                }
                logger.debug("File sent via TCP: {} to {}", filePath, remoteAddress);
                return result;
            }).exceptionally(throwable -> {
                logger.error("Failed to send file via TCP: {} to {}", filePath, remoteAddress, throwable);
                long now = System.currentTimeMillis();
                return FileTransferResult.failure("unknown", filePath, remoteAddress, throwable.getMessage(), 0, now,
                        now);
            });
        }
    }

    @Override
    public CompletableFuture<Void> sendFilePart(Path filePath, long offset, long length,
            InetSocketAddress remoteAddress) throws IOException {
        return sendFilePart(filePath, offset, length, remoteAddress, defaultTransportType);
    }

    private CompletableFuture<Void> sendFileChunksSequentially(Path filePath, InetSocketAddress remoteAddress,
            long offset, long totalSize, long chunkSize, TransportType transportType) {
        if (offset >= totalSize) {
            return CompletableFuture.completedFuture(null);
        }

        long length = Math.min(chunkSize, totalSize - offset);
        try {
            // Use thenComposeAsync to break the recursion stack, preventing
            // StackOverflowError
            // for large files where chunks might be sent synchronously or very fast.
            return sendFilePart(filePath, offset, length, remoteAddress, transportType)
                    .thenComposeAsync(
                            v -> sendFileChunksSequentially(filePath, remoteAddress, offset + length, totalSize,
                                    chunkSize, transportType));
        } catch (IOException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    public CompletableFuture<Void> sendFilePart(Path filePath, long offset, long length,
            InetSocketAddress remoteAddress, TransportType transportType) throws IOException {
        if (transportType == TransportType.QUIC) {
            return quicTransportHandler.sendFilePart(filePath, offset, length, remoteAddress);
        } else {
            // TCP zero-copy
            Connection connection = getConnection(remoteAddress);
            if (connection == null) {
                return CompletableFuture.failedFuture(new IOException("Not connected to " + remoteAddress));
            }
            // Generate a unique message ID for this transfer
            int messageId = java.util.concurrent.ThreadLocalRandom.current().nextInt();

            return secureTransferHandler.sendFilePart(connection, filePath, offset, length, messageId, clusterKey);
        }
    }

    private Connection getConnection(InetSocketAddress remoteAddress) {
        Connection connection = connectionManager.getConnection(remoteAddress);
        if (connection != null && connection.isActive()) {
            return connection;
        }

        connection = tcpServer.getConnection(remoteAddress);
        if (connection != null && connection.isActive()) {
            return connection;
        }

        return null;
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
            return quicTransportHandler.sendMessage(message, remoteAddress);
        } else {
            Connection connection = getConnection(remoteAddress);
            if (connection == null) {
                return CompletableFuture.failedFuture(new IOException("Not connected to " + remoteAddress));
            }

            return connection.sendMessage(message).thenRun(() -> {
                statistics.incrementBytesSent(message.getTotalSize());
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

    private void notifyListeners(java.util.function.Consumer<NetworkEventListener> action, String context) {
        for (NetworkEventListener listener : listeners) {
            try {
                action.accept(listener);
            } catch (Exception e) {
                // Catching generic Exception to ensure one failing listener doesn't break the
                // loop for others
                logger.error("Error notifying listener of {}", context, e);
            }
        }
    }

    private void notifyConnectionEstablished(InetSocketAddress remoteAddress) {
        notifyListeners(listener -> listener.onConnectionEstablished(remoteAddress), "connection established");
    }

    private void notifyConnectionClosed(InetSocketAddress remoteAddress, Throwable cause) {
        notifyListeners(listener -> listener.onConnectionClosed(remoteAddress, cause), "connection closed");
    }

    private void notifyMessageReceived(ProtocolMessage message, InetSocketAddress remoteAddress) {
        notifyListeners(listener -> listener.onMessageReceived(message, remoteAddress), "message received");
    }

    private void notifyFileTransferStarted(Path filePath, InetSocketAddress remoteAddress, long fileSize) {
        notifyListeners(listener -> listener.onFileTransferStarted(filePath, remoteAddress, fileSize),
                "file transfer started");
    }

    private void notifyFileTransferProgress(Path filePath, InetSocketAddress remoteAddress, long bytesTransferred,
            long totalBytes) {
        notifyListeners(
                listener -> listener.onFileTransferProgress(filePath, remoteAddress, bytesTransferred, totalBytes),
                "file transfer progress");
    }

    private void notifyFileTransferCompleted(Path filePath, InetSocketAddress remoteAddress, boolean success,
            String error) {
        notifyListeners(listener -> listener.onFileTransferCompleted(filePath, remoteAddress, success, error),
                "file transfer completed");
    }

    private void notifyError(Throwable error, String context) {
        notifyListeners(listener -> listener.onError(error, context), "error");
    }

}