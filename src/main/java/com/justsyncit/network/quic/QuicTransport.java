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

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for QUIC transport operations.
 * Follows Dependency Inversion Principle by abstracting QUIC transport functionality.
 */
public interface QuicTransport {
    
    /**
     * Starts the QUIC transport.
     *
     * @return a CompletableFuture that completes when the transport is started
     */
    CompletableFuture<Void> start();
    
    /**
     * Stops the QUIC transport.
     *
     * @return a CompletableFuture that completes when the transport is stopped
     */
    CompletableFuture<Void> stop();
    
    /**
     * Connects to a remote server using QUIC.
     *
     * @param address the server address
     * @return a CompletableFuture that completes when connection is established
     */
    CompletableFuture<QuicConnection> connect(InetSocketAddress address);
    
    /**
     * Disconnects from a remote server.
     *
     * @param address the server address
     * @return a CompletableFuture that completes when disconnection is complete
     */
    CompletableFuture<Void> disconnect(InetSocketAddress address);
    
    /**
     * Sends a message to a remote server.
     *
     * @param message the message to send
     * @param remoteAddress the server address
     * @return a CompletableFuture that completes when the message is sent
     */
    CompletableFuture<Void> sendMessage(ProtocolMessage message, InetSocketAddress remoteAddress);
    
    /**
     * Sends a file to a remote server.
     *
     * @param filePath the path to the file to send
     * @param remoteAddress the server address
     * @param fileData the file data to send
     * @return a CompletableFuture that completes when the file is sent
     */
    CompletableFuture<Void> sendFile(Path filePath, InetSocketAddress remoteAddress, byte[] fileData);
    
    /**
     * Checks if connected to a specific server.
     *
     * @param serverAddress the server address
     * @return true if connected, false otherwise
     */
    boolean isConnected(InetSocketAddress serverAddress);
    
    /**
     * Gets the number of active connections.
     *
     * @return the number of active connections
     */
    int getActiveConnectionCount();
    
    /**
     * Adds a transport event listener.
     *
     * @param listener the event listener
     */
    void addEventListener(QuicTransportEventListener listener);
    
    /**
     * Removes a transport event listener.
     *
     * @param listener the event listener to remove
     */
    void removeEventListener(QuicTransportEventListener listener);
    
    /**
     * Interface for QUIC transport event listeners.
     */
    interface QuicTransportEventListener {
        
        /**
         * Called when connected to server.
         *
         * @param serverAddress the server address
         * @param connection the established connection
         */
        void onConnected(InetSocketAddress serverAddress, QuicConnection connection);
        
        /**
         * Called when disconnected from server.
         *
         * @param serverAddress the server address
         * @param cause the reason for disconnection (null if normal)
         */
        void onDisconnected(InetSocketAddress serverAddress, Throwable cause);
        
        /**
         * Called when a message is received from server.
         *
         * @param serverAddress the server address
         * @param message the received message
         */
        void onMessageReceived(InetSocketAddress serverAddress, ProtocolMessage message);
        
        /**
         * Called when an error occurs.
         *
         * @param error the error that occurred
         * @param context the context in which the error occurred
         */
        void onError(Throwable error, String context);
    }
}