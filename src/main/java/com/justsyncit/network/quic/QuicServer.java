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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QUIC server implementation using Kwik library.
 * Provides high-performance QUIC-based server with support for
 * multi-stream multiplexing and 0-RTT connections.
 */
public class QuicServer {

    /** Logger for QUIC server operations. */
    private static final Logger logger = LoggerFactory.getLogger(QuicServer.class);

    /** List of server event listeners. */
    private final CopyOnWriteArrayList<QuicServerEventListener> listeners;
    /** Connected clients. */
    private final ConcurrentHashMap<InetSocketAddress, QuicConnection> clients;
    /** Executor service for async operations. */
    private final ExecutorService executorService;
    /** QUIC configuration. */
    private final QuicConfiguration configuration;
    /** Flag indicating if server is running. */
    private final AtomicBoolean running;
    /** Server port. */
    private volatile int port;

    /**
     * Creates a new QUIC server with default configuration.
     */
    public QuicServer() {
        this(QuicConfiguration.defaultConfiguration());
    }

    /**
     * Creates a new QUIC server with specified configuration.
     *
     * @param configuration QUIC configuration
     */
    public QuicServer(QuicConfiguration configuration) {
        this.configuration = configuration;
        this.listeners = new CopyOnWriteArrayList<>();
        this.clients = new ConcurrentHashMap<>();
        this.executorService = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "QuicServer-Worker-" + System.currentTimeMillis());
            t.setDaemon(true);
            return t;
        });
        this.running = new AtomicBoolean(false);
        this.port = -1;
    }

    /**
     * Starts the QUIC server on the specified port.
     *
     * @param port port to listen on
     * @return a CompletableFuture that completes when the server is started
     */
    public CompletableFuture<Void> start(int port) {
        if (running.compareAndSet(false, true)) {
            return CompletableFuture.runAsync(() -> {
                try {
                    doStart(port);
                    this.port = port;
                    logger.info("QUIC server started on port {}", port);
                } catch (Exception e) {
                    running.set(false);
                    logger.error("Failed to start QUIC server on port {}", port, e);
                    throw new IllegalStateException("Failed to start QUIC server on port " + port, e);
                }
            }, executorService);
        } else {
            return CompletableFuture.failedFuture(new IllegalStateException("QUIC server is already running"));
        }
    }

    /**
     * Actually starts the server.
     *
     * @param port port to listen on
     * @throws IOException if starting fails
     */
    private void doStart(int port) throws IOException {
        // Initialize Kwik server with configuration
        // This is a placeholder - actual implementation will use Kwik API
        logger.debug("Initializing QUIC server on port {} with configuration: {}", port, configuration);

        // Set up event handlers
        setupEventHandlers();

        // Start listening for connections
        // Actual implementation would use Kwik's server API
        logger.info("QUIC server listening on port {}", port);
    }

    /**
     * Sets up event handlers for QUIC server events.
     */
    private void setupEventHandlers() {
        // Placeholder for event handler setup
        // Actual implementation will use Kwik's event handling mechanism
    }

    /**
     * Sends a message to a specific client.
     *
     * @param message message to send
     * @param clientAddress client address
     * @return a CompletableFuture that completes when the message is sent
     */
    public CompletableFuture<Void> sendMessage(ProtocolMessage message, InetSocketAddress clientAddress) {
        QuicConnection connection = clients.get(clientAddress);
        if (connection != null) {
            return connection.sendMessage(message);
        } else {
            return CompletableFuture.failedFuture(
                new IOException("Client not connected: " + clientAddress)
            );
        }
    }

    /**
     * Broadcasts a message to all connected clients.
     *
     * @param message message to broadcast
     * @return a CompletableFuture that completes when the message is sent to all clients
     */
    public CompletableFuture<Void> broadcastMessage(ProtocolMessage message) {
        if (clients.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<?>[] sendFutures = clients.entrySet().stream()
                .map(entry -> entry.getValue().sendMessage(message))
                .toArray(CompletableFuture[]::new);

        return CompletableFuture.allOf(sendFutures)
            .thenRun(() -> logger.debug("Broadcasted message {} to {} clients",
                    message.getMessageType(), clients.size()))
            .exceptionally(throwable -> {
                logger.error("Failed to broadcast message", throwable);
                return null;
            });
    }

    /**
     * Gets the number of connected clients.
     *
     * @return the number of connected clients
     */
    public int getClientCount() {
        return clients.size();
    }

    /**
     * Gets the actual port the server is listening on.
     *
     * @return the port number, or -1 if the server is not running
     */
    public int getPort() {
        return running.get() ? port : -1;
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
     * Adds a server event listener.
     *
     * @param listener event listener
     */
    public void addEventListener(QuicServerEventListener listener) {
        listeners.add(listener);
    }

    /**
     * Removes a server event listener.
     *
     * @param listener event listener to remove
     */
    public void removeEventListener(QuicServerEventListener listener) {
        listeners.remove(listener);
    }

    /**
     * Stops the QUIC server and releases all resources.
     *
     * @return a CompletableFuture that completes when the server is stopped
     */
    public CompletableFuture<Void> stop() {
        if (running.compareAndSet(true, false)) {
            return CompletableFuture.runAsync(() -> {
                try {
                    // Close all client connections
                    CompletableFuture<?>[] closeFutures = clients.values().stream()
                            .map(QuicConnection::close)
                            .toArray(CompletableFuture[]::new);

                    CompletableFuture.allOf(closeFutures).join();
                    clients.clear();

                    // Shutdown server
                    doStop();

                    // Shutdown executor
                    executorService.shutdown();

                    port = -1;
                    logger.info("QUIC server stopped");
                } catch (Exception e) {
                    logger.error("Error stopping QUIC server", e);
                    throw new IllegalStateException("Error stopping QUIC server", e);
                }
            }, executorService);
        } else {
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Actually stops the server.
     *
     * @throws IOException if stopping fails
     */
    private void doStop() throws IOException {
        // Placeholder for actual server stop
        // In a real implementation, this would use Kwik's server stop API
        logger.debug("Performing server stop");

        // Simulate server stop
        // Actual implementation would use the QUIC library's server stop method
    }

    // Event notification methods

    /**
     * Interface for QUIC server event listeners.
     */
    public interface QuicServerEventListener {

        /**
         * Called when a client connects.
         *
         * @param clientAddress client address
         * @param connection established connection
         */
        void onClientConnected(InetSocketAddress clientAddress, QuicConnection connection);

        /**
         * Called when a client disconnects.
         *
         * @param clientAddress client address
         * @param cause reason for disconnection (null if normal)
         */
        void onClientDisconnected(InetSocketAddress clientAddress, Throwable cause);

        /**
         * Called when a message is received from a client.
         *
         * @param clientAddress client address
         * @param message received message
         * @param streamId stream ID
         */
        void onMessageReceived(InetSocketAddress clientAddress, ProtocolMessage message, long streamId);

        /**
         * Called when a new stream is created for a client.
         *
         * @param clientAddress client address
         * @param stream created stream
         */
        void onStreamCreated(InetSocketAddress clientAddress, QuicStream stream);

        /**
         * Called when a stream is closed for a client.
         *
         * @param clientAddress client address
         * @param streamId stream ID
         * @param cause reason for closure (null if normal)
         */
        void onStreamClosed(InetSocketAddress clientAddress, long streamId, Throwable cause);

        /**
         * Called when an error occurs.
         *
         * @param error error that occurred
         * @param context context in which the error occurred
         */
        void onError(Throwable error, String context);
    }
}