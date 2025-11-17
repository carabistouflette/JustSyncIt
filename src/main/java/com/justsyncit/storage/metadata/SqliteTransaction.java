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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * SQLite implementation of Transaction.
 * Provides transaction management for SQLite database operations.
 * Follows Single Responsibility Principle by focusing only on transaction control.
 */
public final class SqliteTransaction implements Transaction {

    /** Logger instance. */
    private static final Logger logger = LoggerFactory.getLogger(SqliteTransaction.class);

    /** The database connection for this transaction. */
    private final Connection connection;
    /** The connection manager to return the connection to. */
    private final DatabaseConnectionManager connectionManager;
    /** Flag indicating if the transaction is active. */
    private volatile boolean active;

    /**
     * Creates a new SqliteTransaction.
     *
     * @param connection the database connection for this transaction
     * @param connectionManager the connection manager to return the connection to
     * @throws IllegalArgumentException if any parameter is null
     */
    public SqliteTransaction(Connection connection, DatabaseConnectionManager connectionManager) {
        if (connection == null) {
            throw new IllegalArgumentException("Connection cannot be null");
        }
        if (connectionManager == null) {
            throw new IllegalArgumentException("Connection manager cannot be null");
        }

        this.connection = connection;
        this.connectionManager = connectionManager;
        this.active = true;
    }

    /**
     * Gets the database connection for this transaction.
     *
     * @return the database connection
     * @throws IllegalStateException if the transaction is not active
     */
    public Connection getConnection() {
        validateActive();
        return connection;
    }

    @Override
    public void commit() throws IOException {
        validateActive();
        
        try {
            if (!connection.getAutoCommit()) {
                connection.commit();
                logger.debug("Committed transaction");
            }
        } catch (SQLException e) {
            throw new IOException("Failed to commit transaction", e);
        } finally {
            close();
        }
    }

    @Override
    public void rollback() throws IOException {
        validateActive();
        
        try {
            if (!connection.getAutoCommit()) {
                connection.rollback();
                logger.debug("Rolled back transaction");
            }
        } catch (SQLException e) {
            throw new IOException("Failed to rollback transaction", e);
        } finally {
            close();
        }
    }

    @Override
    public void close() throws IOException {
        if (active) {
            active = false;
            try {
                connectionManager.closeConnection(connection);
                logger.debug("Closed transaction and returned connection to pool");
            } catch (SQLException e) {
                throw new IOException("Failed to return connection to pool", e);
            }
        }
    }

    @Override
    public boolean isActive() {
        return active;
    }

    /**
     * Validates that the transaction is active.
     *
     * @throws IllegalStateException if the transaction is not active
     */
    private void validateActive() {
        if (!active) {
            throw new IllegalStateException("Transaction is not active");
        }
    }
}