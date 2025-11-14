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

import com.justsyncit.network.protocol.ProtocolMessage;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TCP server implementation using Java NIO with ServerSocketChannel.
 * Provides non-blocking I/O for efficient handling of multiple client connections.
 * Follows Single Responsibility Principle by focusing solely on server operations.
 */
public class TcpServer {

    /** The logger for this class. */
    private static final Logger logger = LoggerFactory.getLogger(TcpServer.class);

    /** The server event listeners. */
    private final CopyOnWriteArrayList<ServerEventListener> listeners;
    /** The connected clients. */
    private final ConcurrentHashMap<SocketAddress, ClientConnection> clients;
    /** The executor service. */
    private final ExecutorService executorService;
    /** The running flag. */
    private final AtomicBoolean running;
    /** The accepting flag. */
    private final AtomicBoolean accepting;

    /** The server socket channel. */
    private ServerSocketChannel serverChannel;
    /** The selector. */
    private Selector selector;
    /** The server thread. */
    private Thread serverThread;

    /**
     * Creates a new TCP server.
     */
    public TcpServer() {
        this.listeners = new CopyOnWriteArrayList<>();
        this.clients = new ConcurrentHashMap<>();
        this.executorService = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "TcpServer-Worker-" + System.currentTimeMillis());
            t.setDaemon(true);
            return t;
        });
        this.running = new AtomicBoolean(false);
        this.accepting = new AtomicBoolean(false);
    }

    /**
     * Starts the TCP server on the specified port.
     *
     * @param port the port to listen on
     * @return a CompletableFuture that completes when the server is started
     * @throws IOException if an I/O error occurs
     */
    public CompletableFuture<Void> start(int port) throws IOException {
        if (running.compareAndSet(false, true)) {
            return CompletableFuture.runAsync(() -> {
                try {
                    doStart(port);
                } catch (IOException e) {
                    running.set(false);
                    throw new RuntimeException("Failed to start server on port " + port, e);
                }
            }, executorService);
        } else {
            return CompletableFuture.failedFuture(new IllegalStateException("Server is already running"));
        }
    }

    /**
     * Actually starts the server.
     */
    private void doStart(int port) throws IOException {
        // Create server socket channel
        serverChannel = ServerSocketChannel.open();
        serverChannel.configureBlocking(false);
        serverChannel.socket().setReuseAddress(true);

        // Apply socket buffer tuning
        serverChannel.socket().setReceiveBufferSize(
                com.justsyncit.network.protocol.ProtocolConstants.DEFAULT_RECEIVE_BUFFER_SIZE);

        // Bind to port
        serverChannel.bind(new InetSocketAddress(port));

        // Create selector
        selector = Selector.open();
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);

        // Start server thread
        serverThread = new Thread(this::serverLoop, "TcpServer-Main");
        serverThread.setDaemon(false);
        serverThread.start();

        logger.info("TCP server started on port {}", port);
    }

    /**
     * Main server loop for handling connections and events.
     */
    private void serverLoop() {
        ByteBuffer readBuffer = ByteBuffer.allocate(
                com.justsyncit.network.protocol.ProtocolConstants.DEFAULT_CHUNK_SIZE * 2);

        while (running.get()) {
            try {
                // Wait for events
                selector.select();

                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                Iterator<SelectionKey> iterator = selectedKeys.iterator();

                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();
                    iterator.remove();

                    try {
                        if (key.isAcceptable()) {
                            handleAccept(key);
                        } else if (key.isReadable()) {
                            handleRead(key, readBuffer);
                        } else if (key.isWritable()) {
                            handleWrite(key);
                        }
                    } catch (CancelledKeyException e) {
                        // Key was cancelled, remove client
                        if (key.channel() instanceof SocketChannel) {
                            handleClientDisconnection((SocketChannel) key.channel());
                        }
                    } catch (Exception e) {
                        logger.error("Error handling selection key", e);
                        if (key.channel() instanceof SocketChannel) {
                            handleClientDisconnection((SocketChannel) key.channel());
                        }
                    }
                }
            } catch (IOException e) {
                if (running.get()) {
                    logger.error("Error in server selector loop", e);
                }
            }
        }
    }

    /**
     * Handles new client connections.
     */
    private void handleAccept(SelectionKey key) throws IOException {
        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
        SocketChannel clientChannel = serverChannel.accept();

        if (clientChannel != null) {
            // Configure client channel
            clientChannel.configureBlocking(false);
            clientChannel.socket().setTcpNoDelay(true);
            clientChannel.socket().setKeepAlive(true);

            // Apply socket buffer tuning
            clientChannel.socket().setSendBufferSize(
                    com.justsyncit.network.protocol.ProtocolConstants.DEFAULT_SEND_BUFFER_SIZE);
            clientChannel.socket().setReceiveBufferSize(
                    com.justsyncit.network.protocol.ProtocolConstants.DEFAULT_RECEIVE_BUFFER_SIZE);

            // Register for read operations
            clientChannel.register(selector, SelectionKey.OP_READ);

            // Create client connection
            SocketAddress clientAddress = clientChannel.getRemoteAddress();
            ClientConnection connection = ClientConnection.create(clientChannel, clientAddress);
            clients.put(clientAddress, connection);

            logger.debug("Client connected: {}", clientAddress);
            notifyClientConnected((InetSocketAddress) clientAddress);
        }
    }

    /**
     * Handles read operations from clients.
     */
    private void handleRead(SelectionKey key, ByteBuffer readBuffer) throws IOException {
        SocketChannel clientChannel = (SocketChannel) key.channel();
        ClientConnection connection = clients.get(clientChannel.getRemoteAddress());

        if (connection == null) {
            logger.warn("Received data from unknown client: {}", clientChannel.getRemoteAddress());
            return;
        }

        try {
            // Read data from client
            int bytesRead = clientChannel.read(readBuffer);
            if (bytesRead == -1) {
                // Client disconnected
                handleClientDisconnection(clientChannel);
                return;
            }

            // Process received data
            SocketAddress clientAddress = clientChannel.getRemoteAddress();
            connection.processReceivedData(readBuffer, message -> handleMessage(message, clientAddress));

        } catch (IOException e) {
            logger.error("Error reading from client: {}", clientChannel.getRemoteAddress(), e);
            handleClientDisconnection(clientChannel);
        }
    }

    /**
     * Handles write operations to clients.
     */
    private void handleWrite(SelectionKey key) throws IOException {
        SocketChannel clientChannel = (SocketChannel) key.channel();
        ClientConnection connection = clients.get(clientChannel.getRemoteAddress());

        if (connection != null) {
            connection.writePendingData(clientChannel);
        }
    }

    /**
     * Handles a received protocol message.
     */
    private void handleMessage(ProtocolMessage message, SocketAddress clientAddress) {
        logger.debug("Received message from {}: {}", clientAddress, message.getMessageType());
        notifyMessageReceived((InetSocketAddress) clientAddress, message);
    }

    /**
     * Handles client disconnection.
     */
    private void handleClientDisconnection(SocketChannel clientChannel) {
        try {
            SocketAddress clientAddress = clientChannel.getRemoteAddress();
            if (clientAddress != null) {
                ClientConnection connection = clients.remove(clientAddress);
                if (connection != null) {
                    connection.close();
                    logger.debug("Client disconnected: {}", clientAddress);
                    notifyClientDisconnected((InetSocketAddress) clientAddress, null);
                }
            }
            clientChannel.close();
        } catch (IOException e) {
            logger.error("Error closing client channel", e);
        }
    }

    /**
     * Stops the TCP server.
     *
     * @return a CompletableFuture that completes when the server is stopped
     */
    public CompletableFuture<Void> stop() {
        if (running.compareAndSet(true, false)) {
            return CompletableFuture.runAsync(() -> {
                try {
                    doStop();
                } catch (Exception e) {
                    logger.error("Error stopping server", e);
                }
            }, executorService);
        } else {
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Actually stops the server.
     */
    private void doStop() {
        try {
            // Wake up selector
            if (selector != null) {
                selector.wakeup();
            }

            // Wait for server thread to finish
            if (serverThread != null) {
                serverThread.join(5000); // Wait up to 5 seconds
            }

            // Close all client connections
            for (ClientConnection connection : clients.values()) {
                connection.close();
            }
            clients.clear();

            // Close server channel
            if (serverChannel != null) {
                serverChannel.close();
            }

            // Close selector
            if (selector != null) {
                selector.close();
            }

            logger.info("TCP server stopped");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while stopping server", e);
        } catch (IOException e) {
            logger.error("I/O error while stopping server", e);
        }
    }

    /**
     * Sends a message to a specific client.
     *
     * @param message the message to send
     * @param clientAddress the client address
     * @return a CompletableFuture that completes when the message is sent
     */
    public CompletableFuture<Void> sendMessage(ProtocolMessage message, InetSocketAddress clientAddress) {
        ClientConnection connection = clients.get(clientAddress);
        if (connection != null) {
            return connection.sendMessage(message);
        } else {
            return CompletableFuture.failedFuture(
                new IOException("Client not connected: " + clientAddress));
        }
    }

    /**
     * Adds a server event listener.
     *
     * @param listener the event listener
     */
    public void addServerEventListener(ServerEventListener listener) {
        listeners.add(listener);
    }

    /**
     * Removes a server event listener.
     *
     * @param listener the event listener to remove
     */
    public void removeServerEventListener(ServerEventListener listener) {
        listeners.remove(listener);
    }

    /**
     * Closes the server and releases all resources.
     */
    public void close() {
        stop().thenRun(() -> {
            executorService.shutdown();
            listeners.clear();
        });
    }

    /**
     * Checks if the server is running.
     *
     * @return true if the server is running, false otherwise
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Gets the actual port the server is listening on.
     *
     * @return the port number, or -1 if the server is not running
     */
    public int getPort() {
        if (serverChannel != null && running.get()) {
            try {
                return serverChannel.socket().getLocalPort();
            } catch (Exception e) {
                logger.error("Error getting server port", e);
                return -1;
            }
        }
        return -1;
    }

    // Event notification methods
    private void notifyClientConnected(InetSocketAddress clientAddress) {
        for (ServerEventListener listener : listeners) {
            try {
                listener.onClientConnected(clientAddress);
            } catch (Exception e) {
                logger.error("Error notifying listener of client connection", e);
            }
        }
    }

    private void notifyClientDisconnected(InetSocketAddress clientAddress, Throwable cause) {
        for (ServerEventListener listener : listeners) {
            try {
                listener.onClientDisconnected(clientAddress, cause);
            } catch (Exception e) {
                logger.error("Error notifying listener of client disconnection", e);
            }
        }
    }

    private void notifyMessageReceived(InetSocketAddress clientAddress, ProtocolMessage message) {
        for (ServerEventListener listener : listeners) {
            try {
                listener.onMessageReceived(clientAddress, message);
            } catch (Exception e) {
                logger.error("Error notifying listener of message received", e);
            }
        }
    }


    /**
     * Interface for server event listeners.
     */
    public interface ServerEventListener {

        /**
         * Called when a client connects.
         *
         * @param clientAddress the client address
         */
        void onClientConnected(InetSocketAddress clientAddress);

        /**
         * Called when a client disconnects.
         *
         * @param clientAddress the client address
         * @param cause the reason for disconnection (null if normal)
         */
        void onClientDisconnected(InetSocketAddress clientAddress, Throwable cause);

        /**
         * Called when a message is received from a client.
         *
         * @param clientAddress the client address
         * @param message the received message
         */
        void onMessageReceived(InetSocketAddress clientAddress, ProtocolMessage message);

        /**
         * Called when an error occurs.
         *
         * @param error the error that occurred
         * @param context the context in which the error occurred
         */
        void onError(Throwable error, String context);
    }
}