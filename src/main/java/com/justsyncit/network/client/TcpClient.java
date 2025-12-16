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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.channels.Selector;
import java.nio.channels.SelectionKey;
import java.nio.channels.CancelledKeyException;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TCP client implementation using Java NIO with SocketChannel.
 * Provides non-blocking I/O for efficient communication with remote servers.
 * Follows Single Responsibility Principle by focusing solely on client
 * operations.
 */
public class TcpClient {

    /** The logger for this class. */
    private static final Logger logger = LoggerFactory.getLogger(TcpClient.class);

    /** List of client event listeners. */
    private final CopyOnWriteArrayList<ClientEventListener> listeners;
    /** Active server connections. */
    private final ConcurrentHashMap<SocketAddress, ServerConnection> connections;
    /** Pending connection futures. */
    private final ConcurrentHashMap<SocketAddress, CompletableFuture<Void>> pendingConnections;
    /** Executor service for async operations. */
    private final ExecutorService executorService;
    /** Scheduled executor service for periodic tasks. */
    private final ScheduledExecutorService scheduledExecutor;
    /** Flag indicating if the client is running. */
    private final AtomicBoolean running;

    /** Selector for non-blocking I/O operations. */
    private Selector selector;
    /** The network configuration. */
    private final com.justsyncit.network.NetworkConfiguration configuration;

    /** Main client thread. */
    private Thread clientThread;

    /**
     * Creates a new TCP client.
     */
    public TcpClient(com.justsyncit.network.NetworkConfiguration configuration) {
        this.listeners = new CopyOnWriteArrayList<>();
        this.connections = new ConcurrentHashMap<>();
        this.pendingConnections = new ConcurrentHashMap<>();
        this.executorService = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "TcpClient-Worker-" + System.currentTimeMillis());
            t.setDaemon(true);
            return t;
        });
        this.scheduledExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "TcpClient-Scheduler");
            t.setDaemon(true);
            return t;
        });
        this.running = new AtomicBoolean(false);
        this.configuration = configuration != null ? configuration : new com.justsyncit.network.NetworkConfiguration();
    }

    public TcpClient() {
        this(new com.justsyncit.network.NetworkConfiguration());
    }

    /**
     * Connects to a remote server.
     *
     * @param address the server address
     * @return a CompletableFuture that completes when connection is established
     * @throws IOException if an I/O error occurs
     */
    public CompletableFuture<Void> connect(InetSocketAddress address) throws IOException {
        // Check if already connected in an atomic way
        ServerConnection existingConnection = connections.get(address);
        if (existingConnection != null) {
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<Void> connectFuture = new CompletableFuture<>();

        // Store the connection future for completion in handleConnect
        // Use putIfAbsent to avoid race conditions
        CompletableFuture<Void> existingFuture = pendingConnections.putIfAbsent(address, connectFuture);
        if (existingFuture != null) {
            // Connection already in progress
            return existingFuture;
        }

        // Start connection process
        CompletableFuture.runAsync(() -> {
            try {
                doConnect(address, connectFuture);
            } catch (IOException e) {
                connectFuture.completeExceptionally(e);
                // Remove from pending connections if failed
                pendingConnections.remove(address, connectFuture);
            }
        }, executorService);

        return connectFuture;
    }

    /**
     * Actually connects to the server.
     */
    private void doConnect(InetSocketAddress address, CompletableFuture<Void> connectFuture) throws IOException {
        logger.debug("Starting connection process to {}", address);

        // Create socket channel
        SocketChannel socketChannel = SocketChannel.open();
        socketChannel.configureBlocking(false);
        socketChannel.socket().setTcpNoDelay(configuration.isTcpNoDelay());
        socketChannel.socket().setKeepAlive(configuration.isKeepAlive());
        socketChannel.socket().setReuseAddress(configuration.isReuseAddress());

        // Apply socket buffer tuning
        socketChannel.socket().setSendBufferSize(configuration.getSendBufferSize());
        socketChannel.socket().setReceiveBufferSize(configuration.getReceiveBufferSize());

        // Store the connection future (already checked with putIfAbsent in connect
        // method)
        pendingConnections.putIfAbsent(address, connectFuture);

        // Create selector if not exists
        if (selector == null) {
            selector = Selector.open();
            running.set(true);
            clientThread = new Thread(this::clientLoop, "TcpClient-Main");
            clientThread.setDaemon(false);
            clientThread.start();
        }

        // Connect to server
        socketChannel.connect(address);
        socketChannel.register(selector, SelectionKey.OP_CONNECT, address);

        // Wake up selector to process the connection
        selector.wakeup();

        logger.info("Connecting to server: {}", address);
        logger.debug("Socket channel registered for connection to {}", address);
    }

    /**
     * Main client loop for handling connections and events.
     */
    private void clientLoop() {
        ByteBuffer readBuffer = ByteBuffer.allocate(
                com.justsyncit.network.protocol.ProtocolConstants.DEFAULT_CHUNK_SIZE * 2);

        while (running.get() || hasActiveConnections()) {
            try {
                // Wait for events
                selector.select();

                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                Iterator<SelectionKey> iterator = selectedKeys.iterator();

                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();
                    iterator.remove();

                    try {
                        if (key.isConnectable()) {
                            handleConnect(key);
                        } else if (key.isReadable()) {
                            handleRead(key, readBuffer);
                        } else if (key.isWritable()) {
                            handleWrite(key);
                        }
                    } catch (CancelledKeyException e) {
                        // Key was cancelled, remove connection
                        if (key.channel() instanceof SocketChannel) {
                            handleServerDisconnection((SocketChannel) key.channel());
                        }
                    } catch (Exception e) {
                        logger.error("Error handling selection key", e);
                        if (key.channel() instanceof SocketChannel) {
                            handleServerDisconnection((SocketChannel) key.channel());
                        }
                    }
                }
            } catch (IOException e) {
                if (running.get()) {
                    logger.error("Error in client selector loop", e);
                }
            }
        }
    }

    /**
     * Handles connection completion.
     */
    private void handleConnect(SelectionKey key) throws IOException {
        SocketChannel socketChannel = (SocketChannel) key.channel();
        InetSocketAddress serverAddress = (InetSocketAddress) key.attachment();

        try {
            if (socketChannel.finishConnect()) {
                // Connection successful
                logger.info("TCP connection established to server: {}", serverAddress);
                ServerConnection connection = ServerConnection.create(socketChannel, serverAddress, (enable) -> {
                    SelectionKey k = socketChannel.keyFor(selector);
                    if (k != null && k.isValid()) {
                        if (enable) {
                            k.interestOps(k.interestOps() | SelectionKey.OP_WRITE);
                        } else {
                            k.interestOps(k.interestOps() & ~SelectionKey.OP_WRITE);
                        }
                        selector.wakeup();
                    }
                });

                // Use putIfAbsent to avoid race conditions
                ServerConnection existingConnection = connections.putIfAbsent(serverAddress, connection);
                if (existingConnection != null) {
                    // Connection already exists, close this one
                    connection.close();
                    // Use the existing connection
                } else {
                    // Use the new connection we created
                }

                // Register for read operations
                key.interestOps(SelectionKey.OP_READ);

                logger.debug("Connected to server: {}", serverAddress);
                notifyConnected(serverAddress);

                // Complete the connection future atomically
                CompletableFuture<Void> connectFuture = pendingConnections.remove(serverAddress);
                if (connectFuture != null) {
                    logger.debug("Completing connection future for {}", serverAddress);
                    connectFuture.complete(null);
                } else {
                    logger.warn("No pending connection future found for {}", serverAddress);
                }
            } else {
                // Connection failed
                throw new IOException("Connection failed");
            }
        } catch (Exception e) {
            logger.error("Error completing connection to {}", serverAddress, e);
            handleServerDisconnection(socketChannel);

            // Complete the connection future with exception
            CompletableFuture<Void> connectFuture = pendingConnections.remove(serverAddress);
            if (connectFuture != null) {
                connectFuture.completeExceptionally(e);
            }
        }
    }

    /**
     * Handles read operations from server.
     */
    private void handleRead(SelectionKey key, ByteBuffer readBuffer) throws IOException {
        SocketChannel socketChannel = (SocketChannel) key.channel();
        SocketAddress remoteAddress = socketChannel.getRemoteAddress();
        ServerConnection connection = connections.get(remoteAddress);

        if (connection == null) {
            logger.warn("Received data from unknown server: {}", remoteAddress);
            return;
        }

        try {
            // Read data from server
            int bytesRead = socketChannel.read(readBuffer);
            if (bytesRead == -1) {
                // Server disconnected
                handleServerDisconnection(socketChannel);
                return;
            }

            // Process received data
            readBuffer.flip();
            try {
                connection.processReceivedData(readBuffer, message -> handleMessage(message, remoteAddress));
            } finally {
                readBuffer.clear();
            }

        } catch (IOException e) {
            logger.error("Error reading from server: {}", socketChannel.getRemoteAddress(), e);
            handleServerDisconnection(socketChannel);
        }
    }

    /**
     * Handles write operations to server.
     */
    private void handleWrite(SelectionKey key) throws IOException {
        SocketChannel socketChannel = (SocketChannel) key.channel();
        SocketAddress remoteAddress = socketChannel.getRemoteAddress();
        ServerConnection connection = connections.get(remoteAddress);

        if (connection != null) {
            try {
                connection.writePendingData();
            } catch (IOException e) {
                logger.error("Error writing to server: {}", remoteAddress, e);
                handleServerDisconnection(socketChannel);
            }
        }
    }

    /**
     * Handles a received protocol message.
     */
    private void handleMessage(ProtocolMessage message, SocketAddress serverAddress) {
        logger.debug("Received message from {}: {}", serverAddress, message.getMessageType());
        notifyMessageReceived((InetSocketAddress) serverAddress, message);
    }

    /**
     * Handles server disconnection.
     */
    private void handleServerDisconnection(SocketChannel socketChannel) {
        try {
            SocketAddress serverAddress = socketChannel.getRemoteAddress();
            if (serverAddress != null) {
                ServerConnection connection = connections.remove(serverAddress);
                if (connection != null) {
                    connection.close();
                    logger.debug("Disconnected from server: {}", serverAddress);
                    notifyDisconnected((InetSocketAddress) serverAddress, null);
                }

                // Complete any pending connection future with exception
                CompletableFuture<Void> connectFuture = pendingConnections.remove(serverAddress);
                if (connectFuture != null && !connectFuture.isDone()) {
                    connectFuture.completeExceptionally(new IOException("Connection failed"));
                }
            }
            socketChannel.close();
        } catch (IOException e) {
            logger.error("Error closing socket channel", e);
        }
    }

    /**
     * Disconnects from a remote server.
     *
     * @param address the server address
     * @return a CompletableFuture that completes when disconnection is complete
     */
    public CompletableFuture<Void> disconnect(InetSocketAddress address) {
        ServerConnection connection = connections.get(address);
        if (connection != null) {
            return connection.closeAsync()
                    .thenRun(() -> {
                        connections.remove(address);
                        logger.info("Disconnected from server: {}", address);
                        notifyDisconnected(address, null);
                    });
        } else {
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Sends a message to a specific server.
     *
     * @param message       the message to send
     * @param serverAddress the server address
     * @return a CompletableFuture that completes when the message is sent
     */
    public CompletableFuture<Void> sendMessage(ProtocolMessage message, InetSocketAddress serverAddress) {
        ServerConnection connection = connections.get(serverAddress);
        if (connection != null) {
            return connection.sendMessage(message);
        } else {
            return CompletableFuture.failedFuture(
                    new IOException("Not connected to server: " + serverAddress));
        }
    }

    /**
     * Sends a raw buffer to a specific server.
     * 
     * @param buffer        the buffer to send
     * @param serverAddress the server address
     * @return a CompletableFuture that completes when the buffer is sent
     */
    public CompletableFuture<Void> send(ByteBuffer buffer, InetSocketAddress serverAddress) {
        ServerConnection connection = connections.get(serverAddress);
        if (connection != null) {
            return connection.send(buffer);
        } else {
            return CompletableFuture.failedFuture(
                    new IOException("Not connected to server: " + serverAddress));
        }
    }

    /**
     * Sends a file region to a specific server using zero-copy transfer.
     * 
     * @param fileChannel   the file channel to read from
     * @param position      the position to start reading from
     * @param count         the number of bytes to transfer
     * @param serverAddress the server address
     * @return a CompletableFuture that completes when the transfer is complete
     */
    public CompletableFuture<Void> sendFileRegion(java.nio.channels.FileChannel fileChannel, long position, long count,
            InetSocketAddress serverAddress) {
        ServerConnection connection = connections.get(serverAddress);
        if (connection != null) {
            return connection.sendFileRegion(fileChannel, position, count);
        } else {
            return CompletableFuture.failedFuture(
                    new IOException("Not connected to server: " + serverAddress));
        }
    }

    /**
     * Checks if there is an active connection to the specified server.
     *
     * @param serverAddress the server address
     * @return true if connected, false otherwise
     */
    public boolean isConnected(InetSocketAddress serverAddress) {
        return connections.containsKey(serverAddress);
    }

    /**
     * Adds a client event listener.
     *
     * @param listener the event listener
     */
    public void addClientEventListener(ClientEventListener listener) {
        listeners.add(listener);
    }

    /**
     * Removes a client event listener.
     *
     * @param listener the event listener to remove
     */
    public void removeClientEventListener(ClientEventListener listener) {
        listeners.remove(listener);
    }

    /**
     * Closes the TCP client and releases all resources.
     */
    public void close() {
        if (running.compareAndSet(true, false)) {
            try {
                // Close all connections
                for (ServerConnection connection : connections.values()) {
                    connection.close();
                }
                connections.clear();

                // Wake up selector
                if (selector != null) {
                    selector.wakeup();
                }

                // Wait for client thread to finish
                if (clientThread != null) {
                    clientThread.join(5000); // Wait up to 5 seconds
                }

                // Close selector
                if (selector != null) {
                    selector.close();
                }

                logger.info("TCP client closed");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("Interrupted while closing TCP client", e);
            } catch (IOException e) {
                logger.error("I/O error while closing TCP client", e);
            } finally {
                executorService.shutdown();
                scheduledExecutor.shutdown();
                try {
                    if (!scheduledExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                        scheduledExecutor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    scheduledExecutor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    // Event notification methods
    private void notifyConnected(InetSocketAddress serverAddress) {
        for (ClientEventListener listener : listeners) {
            try {
                listener.onConnected(serverAddress);
            } catch (Exception e) {
                logger.error("Error notifying listener of connection", e);
            }
        }
    }

    private void notifyDisconnected(InetSocketAddress serverAddress, Throwable cause) {
        for (ClientEventListener listener : listeners) {
            try {
                listener.onDisconnected(serverAddress, cause);
            } catch (Exception e) {
                logger.error("Error notifying listener of disconnection", e);
            }
        }
    }

    private void notifyMessageReceived(InetSocketAddress serverAddress, ProtocolMessage message) {
        for (ClientEventListener listener : listeners) {
            try {
                listener.onMessageReceived(serverAddress, message);
            } catch (Exception e) {
                logger.error("Error notifying listener of message received", e);
            }
        }
    }

    /**
     * Interface for client event listeners.
     */
    public interface ClientEventListener {

        /**
         * Called when connected to server.
         *
         * @param serverAddress the server address
         */
        void onConnected(InetSocketAddress serverAddress);

        /**
         * Called when disconnected from server.
         *
         * @param serverAddress the server address
         * @param cause         the reason for disconnection (null if normal)
         */
        void onDisconnected(InetSocketAddress serverAddress, Throwable cause);

        /**
         * Called when a message is received from server.
         *
         * @param serverAddress the server address
         * @param message       the received message
         */
        void onMessageReceived(InetSocketAddress serverAddress, ProtocolMessage message);

        /**
         * Called when an error occurs.
         *
         * @param error   the error that occurred
         * @param context the context in which the error occurred
         */
        void onError(Throwable error, String context);
    }

    /**
     * Checks if there are active connections in a thread-safe way.
     * This method avoids the SpotBugs warning about non-atomic operations on
     * ConcurrentHashMap.
     *
     * @return true if there are active connections, false otherwise
     */
    private boolean hasActiveConnections() {
        return !connections.isEmpty();
    }
}