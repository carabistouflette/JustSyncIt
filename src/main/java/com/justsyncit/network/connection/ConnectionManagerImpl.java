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

package com.justsyncit.network.connection;

import com.justsyncit.network.protocol.ProtocolMessage;
import com.justsyncit.network.client.TcpClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of ConnectionManager with reconnection logic and exponential backoff.
 * Follows Single Responsibility Principle by focusing solely on connection management.
 */
public class ConnectionManagerImpl implements ConnectionManager {

    /** The logger for this class. */
    private static final Logger logger = LoggerFactory.getLogger(ConnectionManagerImpl.class);

    /** Maximum number of reconnection attempts. */
    private static final int MAX_RECONNECT_ATTEMPTS = 10;
    /** Initial reconnection delay in milliseconds. */
    private static final long INITIAL_RECONNECT_DELAY_MS = 1000; // 1 second
    /** Multiplier for exponential backoff. */
    private static final double RECONNECT_BACKOFF_MULTIPLIER = 1.5;
    /** Maximum reconnection delay in milliseconds. */
    private static final long MAX_RECONNECT_DELAY_MS = 60000; // 1 minute

    /** Active connections managed by this manager. */
    private final Map<InetSocketAddress, Connection> connections;
    /** Listeners for connection events. */
    private final List<ConnectionListener> listeners;
    /** Message handlers for each address. */
    private final Map<InetSocketAddress, List<Consumer<ProtocolMessage>>> messageHandlers;
    /** Executor for reconnection attempts. */
    private final ScheduledExecutorService reconnectExecutor;
    /** Flag indicating if the manager is running. */
    private final AtomicBoolean running;
    /** TCP client for actual connections. */
    private final TcpClient tcpClient;

    /**
     * Creates a new connection manager.
     */
    public ConnectionManagerImpl() {
        this(new TcpClient());
    }

    /**
     * Creates a new connection manager with the specified TCP client.
     *
     * @param tcpClient the TCP client to use
     * @return a new ConnectionManagerImpl instance
     * @throws IllegalArgumentException if tcpClient is null
     */
    public static ConnectionManagerImpl create(TcpClient tcpClient) {
        // Validate parameters before object creation
        if (tcpClient == null) {
            throw new IllegalArgumentException("TcpClient cannot be null");
        }
        return new ConnectionManagerImpl(tcpClient);
    }

    /**
     * Creates a new connection manager with a default TCP client.
     *
     * @return a new ConnectionManagerImpl instance
     */
    public static ConnectionManagerImpl create() {
        return new ConnectionManagerImpl(new TcpClient());
    }

    /**
     * Private constructor to enforce use of factory methods.
     *
     * @param tcpClient the TCP client to use (must not be null)
     */
    private ConnectionManagerImpl(TcpClient tcpClient) {
        this.connections = new ConcurrentHashMap<>();
        this.listeners = new CopyOnWriteArrayList<>();
        this.messageHandlers = new ConcurrentHashMap<>();
        this.reconnectExecutor = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "ConnectionManager-Reconnect");
            t.setDaemon(true);
            return t;
        });
        this.running = new AtomicBoolean(false);
        // Store reference to TCP client - this is an injected dependency
        this.tcpClient = tcpClient;
    }

    @Override
    public CompletableFuture<Void> start() {
        if (running.compareAndSet(false, true)) {
            logger.info("Connection manager started");
            return CompletableFuture.completedFuture(null);
        } else {
            logger.warn("Connection manager already started");
            return CompletableFuture.completedFuture(null);
        }
    }

    @Override
    public CompletableFuture<Void> stop() {
        if (running.compareAndSet(true, false)) {
            reconnectExecutor.shutdown();
            try {
                if (!reconnectExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    reconnectExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                reconnectExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }

            // Close all connections
            List<CompletableFuture<Void>> closeFutures = new ArrayList<>();
            for (Connection connection : connections.values()) {
                closeFutures.add(connection.closeAsync());
            }

            return CompletableFuture.allOf(closeFutures.toArray(new CompletableFuture[0]))
                .thenRun(() -> {
                    connections.clear();
                    messageHandlers.clear();
                    logger.info("Connection manager stopped");
                });
        } else {
            logger.warn("Connection manager already stopped");
            return CompletableFuture.completedFuture(null);
        }
    }

    @Override
    public CompletableFuture<Connection> connectToNode(InetSocketAddress address) {
        if (!running.get()) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Connection manager is not running")
            );
        }

        Connection existingConnection = connections.get(address);
        if (existingConnection != null && existingConnection.isActive()) {
            return CompletableFuture.completedFuture(existingConnection);
        }

        // Create a new connection implementation
        // This would typically delegate to TcpClient or TcpServer
        // For now, we'll create a placeholder
        CompletableFuture<Connection> connectionFuture = new CompletableFuture<>();

        // Start reconnection logic
        scheduleReconnection(address, connectionFuture, 0);

        return connectionFuture;
    }

    /**
     * Schedules a reconnection attempt with exponential backoff.
     */
    private void scheduleReconnection(InetSocketAddress address,
                                     CompletableFuture<Connection> connectionFuture,
                                     int attempt) {
        if (!running.get() || connectionFuture.isDone()) {
            return;
        }

        if (attempt >= MAX_RECONNECT_ATTEMPTS) {
            String errorMsg = String.format(
                    "Failed to connect to %s after %d attempts", address, MAX_RECONNECT_ATTEMPTS
            );
            logger.error(errorMsg);
            connectionFuture.completeExceptionally(new IOException(errorMsg));
            notifyReconnectionFailed(address, MAX_RECONNECT_ATTEMPTS);
            return;
        }

        long delayMs = calculateReconnectDelay(attempt);

        logger.info(
                "Scheduling reconnection attempt {} to {} in {}ms", attempt + 1, address, delayMs
        );

        notifyReconnectionStarted(address, attempt + 1);

        reconnectExecutor.schedule(() -> {
            try {
                // Attempt to establish connection
                // This would delegate to TcpClient.connectToNode()
                Connection connection = establishConnection(address);

                if (connection.isActive()) {
                    connectionFuture.complete(connection);
                    notifyReconnectionSucceeded(address, connection, attempt + 1);

                    // Set up connection monitoring
                    setupConnectionMonitoring(address, connection);
                } else {
                    // Connection failed, schedule next attempt
                    scheduleReconnection(address, connectionFuture, attempt + 1);
                }
            } catch (Exception e) {
                logger.warn(
                        "Connection attempt {} to {} failed: {}", attempt + 1, address, e.getMessage()
                );
                notifyConnectionFailed(address, e);

                // Schedule next attempt
                scheduleReconnection(address, connectionFuture, attempt + 1);
            }
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Calculates the reconnection delay using exponential backoff.
     */
    private long calculateReconnectDelay(int attempt) {
        long delay = (long) (INITIAL_RECONNECT_DELAY_MS * Math.pow(RECONNECT_BACKOFF_MULTIPLIER, attempt));
        return Math.min(delay, MAX_RECONNECT_DELAY_MS);
    }

    /**
     * Establishes a connection to the specified address.
     * Uses the TCP client to establish the actual connection.
     */
    private Connection establishConnection(InetSocketAddress address) throws IOException {
        // Use TCP client to establish connection
        CompletableFuture<Void> connectFuture = tcpClient.connect(address);

        try {
            // Wait for connection to be established
            connectFuture.get(5, TimeUnit.SECONDS);

            // Verify the connection is actually established
            if (!tcpClient.isConnected(address)) {
                throw new IOException(
                    "TCP client reports connection not established to " + address
                );
            }

            // Create a connection wrapper that delegates to the TCP client
            Connection connection = TcpClientConnectionWrapper.create(address, tcpClient);

            // Add to connections map
            connections.put(address, connection);

            // Notify listeners that connection was established
            notifyConnectionEstablished(connection);

            logger.debug("Connection established to {}", address);
            return connection;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Connection attempt to {} was interrupted", address, e);
            throw new IOException("Connection attempt to " + address + " was interrupted", e);
        } catch (java.util.concurrent.ExecutionException e) {
            logger.error("Failed to establish connection to {}", address, e);
            throw new IOException("Failed to establish connection to " + address, e.getCause());
        } catch (java.util.concurrent.TimeoutException e) {
            logger.error("Connection attempt to {} timed out", address, e);
            throw new IOException("Connection attempt to " + address + " timed out", e);
        } catch (RuntimeException e) {
            logger.error("Failed to establish connection to {}", address, e);
            throw new IOException("Failed to establish connection to " + address, e);
        } catch (IOException e) {
            logger.error("Failed to establish connection to {}", address, e);
            throw e;
        }
    }

    /**
     * Sets up monitoring for a connection.
     */
    private void setupConnectionMonitoring(InetSocketAddress address, Connection connection) {
        // Monitor connection state periodically
        reconnectExecutor.scheduleAtFixedRate(() -> {
            if (!connection.isActive() && connections.containsKey(address)) {
                connections.remove(address);
                notifyConnectionClosed(connection, "Connection lost");

                // Schedule reconnection if still running
                if (running.get()) {
                    connectToNode(address);
                }
            }
        }, 5, 5, TimeUnit.SECONDS);
    }

    @Override
    public CompletableFuture<Void> disconnectFromNode(InetSocketAddress address) {
        Connection connection = connections.remove(address);
        if (connection != null) {
            return connection.closeAsync()
                .thenRun(() -> notifyConnectionClosed(connection, "Disconnected by request"));
        } else {
            return CompletableFuture.completedFuture(null);
        }
    }

    @Override
    public Connection getConnection(InetSocketAddress address) {
        return connections.get(address);
    }

    @Override
    public boolean isConnected(InetSocketAddress address) {
        Connection connection = connections.get(address);
        return connection != null && connection.isActive();
    }

    @Override
    public CompletableFuture<Void> sendMessage(InetSocketAddress address, ProtocolMessage message) {
        Connection connection = connections.get(address);
        if (connection != null && connection.isActive()) {
            return connection.sendMessage(message);
        } else {
            return CompletableFuture.failedFuture(
                new IOException("Not connected to " + address)
            );
        }
    }

    @Override
    public void addConnectionListener(ConnectionListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeConnectionListener(ConnectionListener listener) {
        listeners.remove(listener);
    }

    @Override
    public void addMessageHandler(InetSocketAddress address, Consumer<ProtocolMessage> messageHandler) {
        messageHandlers.computeIfAbsent(address, k -> new CopyOnWriteArrayList<>()).add(messageHandler);
    }

    @Override
    public void removeMessageHandler(InetSocketAddress address, Consumer<ProtocolMessage> messageHandler) {
        List<Consumer<ProtocolMessage>> handlers = messageHandlers.get(address);
        if (handlers != null) {
            handlers.remove(messageHandler);
            if (handlers.isEmpty()) {
                messageHandlers.remove(address);
            }
        }
    }

    @Override
    public Connection[] getActiveConnections() {
        return connections.values().stream()
            .filter(Connection::isActive)
            .toArray(Connection[]::new);
    }

    @Override
    public int getActiveConnectionCount() {
        return (int) connections.values().stream()
            .filter(Connection::isActive)
            .count();
    }

    // Notification methods

    private void notifyConnectionEstablished(Connection connection) {
        for (ConnectionListener listener : listeners) {
            try {
                listener.onConnectionEstablished(connection);
            } catch (Exception e) {
                logger.error("Error in connection listener", e);
            }
        }
    }

    private void notifyConnectionClosed(Connection connection, String reason) {
        for (ConnectionListener listener : listeners) {
            try {
                listener.onConnectionClosed(connection, reason);
            } catch (Exception e) {
                logger.error("Error in connection listener", e);
            }
        }
    }

    private void notifyConnectionFailed(InetSocketAddress address, Throwable error) {
        for (ConnectionListener listener : listeners) {
            try {
                listener.onConnectionFailed(address, error);
            } catch (Exception e) {
                logger.error("Error in connection listener", e);
            }
        }
    }


    private void notifyReconnectionStarted(InetSocketAddress address, int attempt) {
        for (ConnectionListener listener : listeners) {
            try {
                listener.onReconnectionStarted(address, attempt);
            } catch (Exception e) {
                logger.error("Error in connection listener", e);
            }
        }
    }

    private void notifyReconnectionSucceeded(InetSocketAddress address, Connection connection, int attempt) {
        for (ConnectionListener listener : listeners) {
            try {
                listener.onReconnectionSucceeded(address, connection, attempt);
            } catch (Exception e) {
                logger.error("Error in connection listener", e);
            }
        }
    }

    private void notifyReconnectionFailed(InetSocketAddress address, int totalAttempts) {
        for (ConnectionListener listener : listeners) {
            try {
                listener.onReconnectionFailed(address, totalAttempts);
            } catch (Exception e) {
                logger.error("Error in connection listener", e);
            }
        }
    }

}