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

import java.net.InetSocketAddress;

/**
 * Interface for listening to connection events.
 * Follows the Observer pattern and Interface Segregation Principle
 * by providing focused methods for connection event handling.
 */
public interface ConnectionListener {

    /**
     * Called when a new connection is established.
     *
     * @param connection the new connection
     */
    void onConnectionEstablished(Connection connection);

    /**
     * Called when a connection is closed.
     *
     * @param connection the closed connection
     * @param reason the reason for closure (null if normal closure)
     */
    void onConnectionClosed(Connection connection, String reason);

    /**
     * Called when a connection attempt fails.
     *
     * @param address the remote address
     * @param error the error that occurred
     */
    void onConnectionFailed(InetSocketAddress address, Throwable error);

    /**
     * Called when a connection is lost unexpectedly.
     *
     * @param connection the lost connection
     * @param error the error that caused the loss
     */
    void onConnectionLost(Connection connection, Throwable error);

    /**
     * Called when a reconnection attempt starts.
     *
     * @param address the remote address
     * @param attempt the attempt number
     */
    void onReconnectionStarted(InetSocketAddress address, int attempt);

    /**
     * Called when a reconnection attempt succeeds.
     *
     * @param address the remote address
     * @param connection the re-established connection
     * @param attempt the attempt number
     */
    void onReconnectionSucceeded(InetSocketAddress address, Connection connection, int attempt);

    /**
     * Called when reconnection attempts are exhausted.
     *
     * @param address the remote address
     * @param totalAttempts the total number of attempts made
     */
    void onReconnectionFailed(InetSocketAddress address, int totalAttempts);

    /**
     * Called when a connection error occurs.
     *
     * @param connection the connection
     * @param error the error
     */
    void onConnectionError(Connection connection, Throwable error);
}