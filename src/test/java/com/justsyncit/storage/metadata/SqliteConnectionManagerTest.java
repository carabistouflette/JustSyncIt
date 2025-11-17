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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for SqliteConnectionManager.
 * Tests connection pooling, thread safety, and resource management.
 */
@DisplayName("SqliteConnectionManager Tests")
class SqliteConnectionManagerTest {

    /** Connection manager instance for testing. */
    private SqliteConnectionManager connectionManager;
    /** Temporary directory for test database. */
    private Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("connection-test");
        connectionManager = new SqliteConnectionManager(
                tempDir.resolve("test.db").toString(), 5);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (connectionManager != null && !connectionManager.isClosed()) {
            connectionManager.close();
        }
        // Clean up temp directory
        if (tempDir != null && Files.exists(tempDir)) {
            Files.walk(tempDir)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            // Ignore cleanup errors
                        }
                    });
        }
    }

    @Test
    @DisplayName("Should create connection manager successfully")
    void shouldCreateConnectionManager() {
        // Then
        assertNotNull(connectionManager);
        assertFalse(connectionManager.isClosed());
    }

    @Test
    @DisplayName("Should reject null database path")
    void shouldRejectNullDatabasePath() {
        // When/Then
        assertThrows(IllegalArgumentException.class, () ->
                new SqliteConnectionManager(null, 5));
    }

    @Test
    @DisplayName("Should reject empty database path")
    void shouldRejectEmptyDatabasePath() {
        // When/Then
        assertThrows(IllegalArgumentException.class, () ->
                new SqliteConnectionManager("", 5));
    }

    @Test
    @DisplayName("Should reject non-positive max connections")
    void shouldRejectNonPositiveMaxConnections() {
        // When/Then
        assertThrows(IllegalArgumentException.class, () ->
                new SqliteConnectionManager("test.db", 0));
    }

    @Test
    @DisplayName("Should get connection successfully")
    void shouldGetConnection() throws SQLException {
        // When
        Connection connection = connectionManager.getConnection();

        // Then
        assertNotNull(connection);
        assertFalse(connection.isClosed());
    }

    @Test
    @DisplayName("Should begin transaction successfully")
    void shouldBeginTransaction() throws SQLException {
        // When
        Connection connection = connectionManager.beginTransaction();

        // Then
        assertNotNull(connection);
        assertFalse(connection.getAutoCommit());
    }

    @Test
    @DisplayName("Should commit transaction successfully")
    void shouldCommitTransaction() throws SQLException {
        // Given
        Connection connection = connectionManager.beginTransaction();

        // When
        connectionManager.commitTransaction(connection);

        // Then
        assertTrue(connection.getAutoCommit());
    }

    @Test
    @DisplayName("Should rollback transaction successfully")
    void shouldRollbackTransaction() throws SQLException {
        // Given
        Connection connection = connectionManager.beginTransaction();

        // When
        connectionManager.rollbackTransaction(connection);

        // Then
        assertTrue(connection.getAutoCommit());
    }

    @Test
    @DisplayName("Should close connection successfully")
    void shouldCloseConnection() throws SQLException {
        // Given
        Connection connection = connectionManager.getConnection();

        // When
        connectionManager.closeConnection(connection);

        // Then
        assertTrue(connection.getAutoCommit());
    }

    @Test
    @DisplayName("Should handle concurrent access")
    void shouldHandleConcurrentAccess() throws InterruptedException {
        // Given
        int threadCount = 10;
        int operationsPerThread = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        List<Exception> exceptions = new ArrayList<>();

        // When
        for (int i = 0; i < threadCount; i++) {
            executor.execute(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        Connection connection = connectionManager.getConnection();
                        connectionManager.closeConnection(connection);
                    }
                } catch (Exception e) {
                    synchronized (exceptions) {
                        exceptions.add(e);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        // Ensure all tasks are submitted
        executor.shutdown();

        // Then
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertTrue(exceptions.isEmpty(), "No exceptions should occur during concurrent access: " + exceptions);
        executor.shutdown();
    }

    @Test
    @DisplayName("Should close manager gracefully")
    void shouldCloseManagerGracefully() throws IOException {
        // When
        assertDoesNotThrow(() -> connectionManager.close());

        // Then
        assertTrue(connectionManager.isClosed());
    }

    @Test
    @DisplayName("Should handle operations after close")
    void shouldHandleOperationsAfterClose() throws IOException {
        // Given
        connectionManager.close();

        // When/Then
        SQLException exception = assertThrows(SQLException.class, () ->
                connectionManager.getConnection());
        assertTrue(exception.getCause() instanceof IOException);
    }
}