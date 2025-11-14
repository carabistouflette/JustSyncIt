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
import com.justsyncit.network.transfer.FileTransferResult;
import com.justsyncit.storage.ContentStore;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * Service interface for network operations in JustSyncIt.
 * Provides high-level abstractions for TCP-based file transfers and connection management.
 * Follows Interface Segregation Principle by providing focused network operations.
 */
public interface NetworkService {

    /**
     * Starts the network server for accepting incoming connections.
     *
     * @param port the port to listen on
     * @return a CompletableFuture that completes when the server is started
     * @throws IOException if an I/O error occurs
     */
    CompletableFuture<Void> startServer(int port) throws IOException;

    /**
     * Stops the network server and closes all connections.
     *
     * @return a CompletableFuture that completes when the server is stopped
     */
    CompletableFuture<Void> stopServer();

    /**
     * Connects to a remote JustSyncIt node.
     *
     * @param address the remote address
     * @return a CompletableFuture that completes when the connection is established
     * @throws IOException if an I/O error occurs
     */
    CompletableFuture<Void> connectToNode(InetSocketAddress address) throws IOException;

    /**
     * Disconnects from a remote node.
     *
     * @param address the remote address
     * @return a CompletableFuture that completes when the disconnection is complete
     */
    CompletableFuture<Void> disconnectFromNode(InetSocketAddress address);

    /**
     * Sends a file to a remote node.
     *
     * @param filePath the path to the file to send
     * @param remoteAddress the remote node address
     * @param contentStore the content store for chunk access
     * @return a CompletableFuture that completes when the file transfer is complete
     * @throws IOException if an I/O error occurs
     */
    CompletableFuture<FileTransferResult> sendFile(Path filePath, InetSocketAddress remoteAddress,
            ContentStore contentStore) throws IOException;

    /**
     * Sends a protocol message to a remote node.
     *
     * @param message the message to send
     * @param remoteAddress the remote node address
     * @return a CompletableFuture that completes when the message is sent
     * @throws IOException if an I/O error occurs
     */
    CompletableFuture<Void> sendMessage(ProtocolMessage message, InetSocketAddress remoteAddress) throws IOException;

    /**
     * Registers a listener for network events.
     *
     * @param listener the event listener
     */
    void addNetworkEventListener(NetworkEventListener listener);

    /**
     * Removes a network event listener.
     *
     * @param listener the event listener to remove
     */
    void removeNetworkEventListener(NetworkEventListener listener);

    /**
     * Gets the current connection status.
     *
     * @return true if the service is running, false otherwise
     */
    boolean isRunning();

    /**
     * Gets the server running status.
     *
     * @return true if the server is running, false otherwise
     */
    boolean isServerRunning();

    /**
     * Gets the actual port the server is listening on.
     *
     * @return the port number, or -1 if the server is not running
     */
    int getServerPort();

    /**
     * Gets the number of active connections.
     *
     * @return the number of active connections
     */
    int getActiveConnectionCount();

    /**
     * Gets the number of active transfers.
     *
     * @return the number of active transfers
     */
    int getActiveTransferCount();

    /**
     * Gets the total bytes sent.
     *
     * @return the total bytes sent
     */
    long getBytesSent();

    /**
     * Gets the total bytes received.
     *
     * @return the total bytes received
     */
    long getBytesReceived();

    /**
     * Gets the total messages sent.
     *
     * @return the total messages sent
     */
    long getMessagesSent();

    /**
     * Gets the total messages received.
     *
     * @return the total messages received
     */
    long getMessagesReceived();

    /**
     * Gets network statistics.
     *
     * @return network statistics
     */
    NetworkStatistics getStatistics();

    /**
     * Closes the network service and releases all resources.
     *
     * @throws IOException if an I/O error occurs
     */
    void close() throws IOException;

    /**
     * Interface for network event listeners.
     */
    interface NetworkEventListener {

        /**
         * Called when a new connection is established.
         *
         * @param remoteAddress the remote address
         */
        void onConnectionEstablished(InetSocketAddress remoteAddress);

        /**
         * Called when a connection is closed.
         *
         * @param remoteAddress the remote address
         * @param cause the reason for disconnection (null if normal)
         */
        void onConnectionClosed(InetSocketAddress remoteAddress, Throwable cause);

        /**
         * Called when a message is received.
         *
         * @param message the received message
         * @param remoteAddress the remote address
         */
        void onMessageReceived(ProtocolMessage message, InetSocketAddress remoteAddress);

        /**
         * Called when a file transfer starts.
         *
         * @param filePath the file path
         * @param remoteAddress the remote address
         * @param fileSize the file size
         */
        void onFileTransferStarted(Path filePath, InetSocketAddress remoteAddress, long fileSize);

        /**
         * Called when file transfer progress is updated.
         *
         * @param filePath the file path
         * @param remoteAddress the remote address
         * @param bytesTransferred the number of bytes transferred
         * @param totalBytes the total number of bytes
         */
        void onFileTransferProgress(Path filePath, InetSocketAddress remoteAddress,
                               long bytesTransferred, long totalBytes);

        /**
         * Called when a file transfer completes.
         *
         * @param filePath the file path
         * @param remoteAddress the remote address
         * @param success true if successful, false otherwise
         * @param error the error message if unsuccessful
         */
        void onFileTransferCompleted(Path filePath, InetSocketAddress remoteAddress,
                                  boolean success, String error);

        /**
         * Called when an error occurs.
         *
         * @param error the error that occurred
         * @param context the context in which the error occurred
         */
        void onError(Throwable error, String context);
    }

    /**
     * Interface for network statistics.
     */
    interface NetworkStatistics {

        /**
         * Gets the total number of bytes sent.
         *
         * @return the total bytes sent
         */
        long getTotalBytesSent();

        /**
         * Gets the total number of bytes received.
         *
         * @return the total bytes received
         */
        long getTotalBytesReceived();

        /**
         * Gets the total number of messages sent.
         *
         * @return the total messages sent
         */
        long getTotalMessagesSent();

        /**
         * Gets the total number of messages received.
         *
         * @return the total messages received
         */
        long getTotalMessagesReceived();

        /**
         * Gets the number of active connections.
         *
         * @return the number of active connections
         */
        int getActiveConnections();

        /**
         * Gets the number of active connections.
         *
         * @return the number of active connections
         */
        default int getActiveConnectionCount() {
            return getActiveConnections();
        }

        /**
         * Gets the total bytes sent.
         *
         * @return the total bytes sent
         */
        default long getBytesSent() {
            return getTotalBytesSent();
        }

        /**
         * Gets the total bytes received.
         *
         * @return the total bytes received
         */
        default long getBytesReceived() {
            return getTotalBytesReceived();
        }

        /**
         * Gets the total messages sent.
         *
         * @return the total messages sent
         */
        default long getMessagesSent() {
            return getTotalMessagesSent();
        }

        /**
         * Gets the total messages received.
         *
         * @return the total messages received
         */
        default long getMessagesReceived() {
            return getTotalMessagesReceived();
        }

        /**
         * Gets the number of completed file transfers.
         *
         * @return the number of completed transfers
         */
        long getCompletedTransfers();

        /**
         * Gets the number of failed file transfers.
         *
         * @return the number of failed transfers
         */
        long getFailedTransfers();

        /**
         * Gets the average transfer rate in bytes per second.
         *
         * @return the average transfer rate
         */
        double getAverageTransferRate();

        /**
         * Gets the uptime of the network service in milliseconds.
         *
         * @return the uptime in milliseconds
         */
        long getUptimeMillis();
    }
}