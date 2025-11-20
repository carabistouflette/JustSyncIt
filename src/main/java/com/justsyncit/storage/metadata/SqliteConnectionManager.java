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
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * SQLite implementation of DatabaseConnectionManager.
 * Provides connection pooling and thread-safe access to SQLite database.
 * Follows Single Responsibility Principle by focusing only on connection management.
 */
public final class SqliteConnectionManager implements DatabaseConnectionManager {

    /** Logger instance. */
    private static final Logger logger = LoggerFactory.getLogger(SqliteConnectionManager.class);

    /** JDBC URL for the SQLite database. */
    private final String jdbcUrl;
    /** Maximum number of connections in the pool. */
    private final int maxConnections;
    /** Pool of available connections. */
    private final ConcurrentLinkedQueue<Connection> connectionPool;
    /** Lock for thread-safe access to the connection manager state. */
    private final ReadWriteLock lock;
    /** Flag indicating if the manager has been closed. */
    private volatile boolean closed;
    /** Whether this is an in-memory database. */
    private final boolean isInMemory;
    /** Static shared connection for all in-memory databases (for testing). */
    private static volatile Connection staticSharedMemoryConnection;

    /**
     * Creates a new SqliteConnectionManager.
     *
     * @param databasePath path to the SQLite database file
     * @param maxConnections maximum number of connections in the pool
     * @throws IllegalArgumentException if parameters are invalid
     */
    public SqliteConnectionManager(String databasePath, int maxConnections) {
        if (databasePath == null || databasePath.trim().isEmpty()) {
            throw new IllegalArgumentException("Database path cannot be null or empty");
        }
        if (maxConnections <= 0) {
            throw new IllegalArgumentException("Max connections must be positive");
        }

        this.maxConnections = maxConnections;
        this.connectionPool = new ConcurrentLinkedQueue<>();
        this.lock = new ReentrantReadWriteLock();
        this.closed = false;
        this.isInMemory = ":memory:".equals(databasePath) || databasePath.startsWith("file::memory:");
        // For in-memory databases, use shared cache to allow multiple connections
        if (isInMemory) {
            this.jdbcUrl = "jdbc:sqlite:file::memory:?cache=shared";
        } else {
            this.jdbcUrl = "jdbc:sqlite:" + databasePath;
        }

        // Configure SQLite for better performance
        Connection testConn = null;
        try {
            // Test connection and configure SQLite
            testConn = DriverManager.getConnection(jdbcUrl);
            try (var stmt = testConn.createStatement()) {
                stmt.execute("PRAGMA foreign_keys=ON");
                // Use DELETE journal mode instead of WAL to avoid connection isolation issues in tests
                stmt.execute("PRAGMA journal_mode=DELETE");
                stmt.execute("PRAGMA synchronous=NORMAL");
                stmt.execute("PRAGMA cache_size=10000");
                stmt.execute("PRAGMA temp_store=MEMORY");
            }

            logger.info("Initialized SQLite connection manager for database: {}", databasePath);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialize SQLite database", e);
        } finally {
            if (testConn != null) {
                try {
                    testConn.close();
                } catch (SQLException e) {
                    logger.warn("Failed to close test connection: {}", e.getMessage());
                }
            }
        }
    }

    @Override
    public Connection getConnection() throws SQLException {
        try {
            validateNotClosed();
        } catch (IOException e) {
            throw new SQLException("Connection manager is closed", e);
        }

        // For in-memory databases, use a single shared connection
        if (isInMemory) {
            return getOrCreateSharedMemoryConnection(jdbcUrl);
        }

        Connection connection = connectionPool.poll();
        if (connection != null && !connection.isClosed()) {
            return connection;
        }

        // Create new connection if pool is empty
        Connection newConn = DriverManager.getConnection(jdbcUrl);
        // Enable foreign keys and performance optimizations for new connections
        try (var stmt = newConn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys=ON");
            // Use DELETE journal mode instead of WAL to avoid connection isolation issues in tests
            stmt.execute("PRAGMA journal_mode=DELETE");
            stmt.execute("PRAGMA synchronous=NORMAL");
            stmt.execute("PRAGMA cache_size=10000");
            stmt.execute("PRAGMA temp_store=MEMORY");
            stmt.execute("PRAGMA mmap_size=268435456"); // 256MB memory-mapped I/O
        }
        return newConn;
    }

    @Override
    public Connection beginTransaction() throws SQLException {
        try {
            validateNotClosed();
        } catch (IOException e) {
            throw new SQLException("Connection manager is closed", e);
        }

        Connection connection = getConnection();
        connection.setAutoCommit(false);
        return connection;
    }

    @Override
    public void commitTransaction(Connection connection) throws SQLException {
        if (connection == null) {
            throw new IllegalArgumentException("Connection cannot be null");
        }

        try {
            if (!connection.getAutoCommit()) {
                connection.commit();
                connection.setAutoCommit(true);
            }
        } finally {
            returnConnection(connection);
        }
    }

    @Override
    public void rollbackTransaction(Connection connection) throws SQLException {
        if (connection == null) {
            throw new IllegalArgumentException("Connection cannot be null");
        }

        try {
            if (!connection.getAutoCommit()) {
                connection.rollback();
                connection.setAutoCommit(true);
            }
        } finally {
            returnConnection(connection);
        }
    }

    @Override
    public void closeConnection(Connection connection) throws SQLException {
        if (connection == null) {
            throw new IllegalArgumentException("Connection cannot be null");
        }

        if (!connection.isClosed()) {
            // Reset auto-commit before returning to pool
            connection.setAutoCommit(true);
            // For in-memory databases, don't return the shared connection to pool
            // and don't close it, as it's shared across all operations
            if (isInMemory && connection == staticSharedMemoryConnection) {
                return;
            }

            returnConnection(connection);
        }
    }

    @Override
    public void close() throws IOException {
        lock.writeLock().lock();
        try {
            if (!closed) {
                // Close static shared memory connection if exists
                closeStaticSharedMemoryConnection();

                // Close all connections in the pool
                Connection connection;
                while ((connection = connectionPool.poll()) != null) {
                    try {
                        connection.close();
                    } catch (SQLException e) {
                        logger.warn("Failed to close connection during shutdown: {}", e.getMessage());
                    }
                }
                closed = true;
                logger.info("Closed SQLite connection manager");
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    /**
     * Returns a connection to the pool if there's space available.
     *
     * @param connection the connection to return
     * @throws SQLException if the connection cannot be returned
     */
    private void returnConnection(Connection connection) throws SQLException {
        if (connection == null || connection.isClosed()) {
            return;
        }

        // For in-memory databases, don't return connections to pool
        // They all share the same connection
        if (isInMemory) {
            return;
        }

        lock.readLock().lock();
        try {
            if (!closed && connectionPool.size() < maxConnections) {
                connectionPool.offer(connection);
            } else {
                connection.close();
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Validates that the connection manager is not closed.
     *
     * @throws IOException if the manager has been closed
     */
    private void validateNotClosed() throws IOException {
        if (closed) {
            throw new IOException("Connection manager has been closed");
        }
    }

    /**
     * Gets or creates the shared memory connection for in-memory databases.
     * This method is synchronized to ensure thread-safe access to the static connection.
     *
     * @param jdbcUrl the JDBC URL for the connection
     * @return a connection to the shared in-memory database
     * @throws SQLException if the connection cannot be created or configured
     */
    private static synchronized Connection getOrCreateSharedMemoryConnection(String jdbcUrl) throws SQLException {
        if (staticSharedMemoryConnection == null || staticSharedMemoryConnection.isClosed()) {
            staticSharedMemoryConnection = DriverManager.getConnection(jdbcUrl);
            // Configure the shared connection with performance optimizations
            try (var stmt = staticSharedMemoryConnection.createStatement()) {
                stmt.execute("PRAGMA foreign_keys=ON");
                // Use DELETE journal mode instead of WAL to avoid connection isolation issues in tests
                stmt.execute("PRAGMA journal_mode=DELETE");
                stmt.execute("PRAGMA synchronous=NORMAL");
                stmt.execute("PRAGMA cache_size=10000");
                stmt.execute("PRAGMA temp_store=MEMORY");
                stmt.execute("PRAGMA mmap_size=268435456"); // 256MB memory-mapped I/O
            }
            // Initialize schema for the shared connection
            try {
                SchemaMigrator migrator = SqliteSchemaMigrator.create();
                migrator.migrate(staticSharedMemoryConnection);
                logger.info("Initialized schema for shared in-memory database");
            } catch (Exception e) {
                logger.error("Failed to initialize schema for shared in-memory database", e);
                try {
                    staticSharedMemoryConnection.close();
                } catch (SQLException closeEx) {
                    logger.warn("Failed to close shared connection after schema init failure", closeEx);
                }
                staticSharedMemoryConnection = null;
                throw new IllegalStateException("Failed to initialize shared in-memory database", e);
            }
        }
        // Return the same connection instance for all in-memory databases
        return staticSharedMemoryConnection;
    }

    /**
     * Closes the static shared memory connection if it exists.
     */
    private static synchronized void closeStaticSharedMemoryConnection() {
        if (staticSharedMemoryConnection != null) {
            try {
                if (!staticSharedMemoryConnection.isClosed()) {
                    staticSharedMemoryConnection.close();
                }
            } catch (SQLException e) {
                logger.warn("Failed to close static shared memory connection during shutdown: {}", e.getMessage());
            }
            staticSharedMemoryConnection = null;
        }
    }
}