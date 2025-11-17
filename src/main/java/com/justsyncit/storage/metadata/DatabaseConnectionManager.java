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

package com.justsyncit.storage.metadata;

import com.justsyncit.storage.ClosableResource;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Interface for managing database connections.
 * Follows Interface Segregation Principle by focusing only on connection management.
 * Extends ClosableResource for proper resource cleanup.
 */
public interface DatabaseConnectionManager extends ClosableResource {

    /**
     * Gets a database connection.
     *
     * @return a database connection
     * @throws SQLException if a connection cannot be obtained
     */
    Connection getConnection() throws SQLException;

    /**
     * Begins a new transaction.
     *
     * @return a connection with auto-commit disabled for transaction
     * @throws SQLException if the transaction cannot be started
     */
    Connection beginTransaction() throws SQLException;

    /**
     * Commits a transaction on the given connection.
     *
     * @param connection the connection with an active transaction
     * @throws SQLException if the commit fails
     * @throws IllegalArgumentException if connection is null
     */
    void commitTransaction(Connection connection) throws SQLException;

    /**
     * Rolls back a transaction on the given connection.
     *
     * @param connection the connection with an active transaction
     * @throws SQLException if the rollback fails
     * @throws IllegalArgumentException if connection is null
     */
    void rollbackTransaction(Connection connection) throws SQLException;

    /**
     * Closes a connection and returns it to the pool.
     *
     * @param connection the connection to close
     * @throws SQLException if the connection cannot be closed
     * @throws IllegalArgumentException if connection is null
     */
    void closeConnection(Connection connection) throws SQLException;

    /**
     * Checks if the connection manager is closed.
     *
     * @return true if closed, false otherwise
     */
    boolean isClosed();
}