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

import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

/**
 * Interface representing a network connection.
 * Follows the Interface Segregation Principle by providing focused methods
 * for connection operations.
 */
public interface Connection {

    /**
     * Gets the remote address.
     *
     * @return the remote address
     */
    InetSocketAddress getRemoteAddress();

    /**
     * Gets the local address.
     *
     * @return the local address
     */
    InetSocketAddress getLocalAddress();

    /**
     * Checks if the connection is active.
     *
     * @return true if active, false otherwise
     */
    boolean isActive();

    /**
     * Checks if the connection is closed.
     *
     * @return true if closed, false otherwise
     */
    boolean isClosed();

    /**
     * Sends a message over the connection.
     *
     * @param message the message to send
     * @return a CompletableFuture that completes when the message is sent
     */
    CompletableFuture<Void> sendMessage(ProtocolMessage message);

    /**
     * Closes the connection.
     *
     * @return a CompletableFuture that completes when the connection is closed
     */
    CompletableFuture<Void> closeAsync();

    /**
     * Gets the connection creation time.
     *
     * @return the creation time
     */
    Instant getCreationTime();

    /**
     * Gets the last activity time.
     *
     * @return the last activity time
     */
    Instant getLastActivityTime();

    /**
     * Updates the last activity time to the current time.
     */
    void updateLastActivityTime();

    /**
     * Gets the number of bytes sent.
     *
     * @return the number of bytes sent
     */
    long getBytesSent();

    /**
     * Gets the number of bytes received.
     *
     * @return the number of bytes received
     */
    long getBytesReceived();

    /**
     * Gets the connection type (client or server).
     *
     * @return the connection type
     */
    ConnectionType getConnectionType();

    /**
     * Enumeration of connection types.
     */
    enum ConnectionType {
        /**
         * Client-side connection (initiated by this node).
         */
        CLIENT,

        /**
         * Server-side connection (accepted by this node).
         */
        SERVER
    }
}