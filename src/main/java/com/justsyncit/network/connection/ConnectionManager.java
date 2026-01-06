package com.justsyncit.network.connection;

import com.justsyncit.network.protocol.ProtocolMessage;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Interface for managing network connections with reconnection logic.
 * Follows the Interface Segregation Principle by providing focused methods
 * for connection management.
 */
public interface ConnectionManager {

    /**
     * Starts the connection manager.
     *
     * @return a CompletableFuture that completes when the manager is started
     */
    CompletableFuture<Void> start();

    /**
     * Stops the connection manager.
     *
     * @return a CompletableFuture that completes when the manager is stopped
     */
    CompletableFuture<Void> stop();

    /**
     * Connects to a remote node with automatic reconnection.
     *
     * @param address the remote address
     * @return a CompletableFuture that completes when the connection is established
     */
    CompletableFuture<Connection> connectToNode(InetSocketAddress address);

    /**
     * Disconnects from a remote node.
     *
     * @param address the remote address
     * @return a CompletableFuture that completes when disconnection is complete
     */
    CompletableFuture<Void> disconnectFromNode(InetSocketAddress address);

    /**
     * Gets the connection to a remote node.
     *
     * @param address the remote address
     * @return the connection, or null if not connected
     */
    Connection getConnection(InetSocketAddress address);

    /**
     * Checks if connected to a remote node.
     *
     * @param address the remote address
     * @return true if connected, false otherwise
     */
    boolean isConnected(InetSocketAddress address);

    /**
     * Sends a message to a remote node.
     *
     * @param address the remote address
     * @param message the message to send
     * @return a CompletableFuture that completes when the message is sent
     */
    CompletableFuture<Void> sendMessage(InetSocketAddress address, ProtocolMessage message);

    /**
     * Registers a listener for connection events.
     *
     * @param listener the connection listener
     */
    void addConnectionListener(ConnectionListener listener);

    /**
     * Unregisters a connection listener.
     *
     * @param listener the connection listener
     */
    void removeConnectionListener(ConnectionListener listener);

    /**
     * Registers a listener for message events.
     *
     * @param address        the remote address
     * @param messageHandler the message handler
     */
    void addMessageHandler(InetSocketAddress address, Consumer<ProtocolMessage> messageHandler);

    /**
     * Unregisters a message handler.
     *
     * @param address        the remote address
     * @param messageHandler the message handler
     */
    void removeMessageHandler(InetSocketAddress address, Consumer<ProtocolMessage> messageHandler);

    /**
     * Gets all active connections.
     *
     * @return array of active connections
     */
    Connection[] getActiveConnections();

    /**
     * Gets the number of active connections.
     *
     * @return the number of active connections
     */
    int getActiveConnectionCount();
}