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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for SqliteTransaction.
 * Tests transaction management, commit, and rollback operations.
 */
@DisplayName("SqliteTransaction Tests")
class SqliteTransactionTest {

    /** Connection manager instance for testing. */
    private DatabaseConnectionManager connectionManager;
    /** Temporary directory for test database. */
    private Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("transaction-test");
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
    @DisplayName("Should create transaction successfully")
    void shouldCreateTransaction() throws SQLException, IOException {
        // Given
        Connection connection = connectionManager.beginTransaction();

        // When
        try (Transaction transaction = new SqliteTransaction(connection, connectionManager)) {
            // Then
            assertNotNull(transaction);
            assertTrue(transaction.isActive());
            assertEquals(connection, ((SqliteTransaction) transaction).getConnection());
        }
    }

    @Test
    @DisplayName("Should reject null connection")
    void shouldRejectNullConnection() {
        // When/Then
        assertThrows(IllegalArgumentException.class, () -> new SqliteTransaction(null, connectionManager));
    }

    @Test
    @DisplayName("Should reject null connection manager")
    void shouldRejectNullConnectionManager() throws SQLException, IOException {
        // Given
        Connection connection = connectionManager.beginTransaction();

        // When/Then
        assertThrows(IllegalArgumentException.class, () -> new SqliteTransaction(connection, null));
    }

    @Test
    @DisplayName("Should commit transaction successfully")
    void shouldCommitTransaction() throws SQLException, IOException {
        // Given
        Connection connection = connectionManager.beginTransaction();
        Transaction transaction = new SqliteTransaction(connection, connectionManager);

        // When
        transaction.commit();

        // Then
        assertFalse(transaction.isActive());
        assertTrue(connection.getAutoCommit());
    }

    @Test
    @DisplayName("Should rollback transaction successfully")
    void shouldRollbackTransaction() throws SQLException, IOException {
        // Given
        Connection connection = connectionManager.beginTransaction();
        Transaction transaction = new SqliteTransaction(connection, connectionManager);

        // When
        transaction.rollback();

        // Then
        assertFalse(transaction.isActive());
        assertTrue(connection.getAutoCommit());
    }

    @Test
    @DisplayName("Should close transaction successfully")
    void shouldCloseTransaction() throws SQLException, IOException {
        // Given
        Connection connection = connectionManager.beginTransaction();
        Transaction transaction = new SqliteTransaction(connection, connectionManager);

        // When
        transaction.close();

        // Then
        assertFalse(transaction.isActive());
        assertTrue(connection.getAutoCommit());
    }

    @Test
    @DisplayName("Should get connection when active")
    void shouldGetConnectionWhenActive() throws SQLException, IOException {
        // Given
        Connection connection = connectionManager.beginTransaction();
        try (Transaction transaction = new SqliteTransaction(connection, connectionManager)) {
            // When
            Connection retrievedConnection = ((SqliteTransaction) transaction).getConnection();

            // Then
            assertEquals(connection, retrievedConnection);
            assertTrue(transaction.isActive());
        }
    }

    @Test
    @DisplayName("Should reject get connection when inactive")
    void shouldRejectGetConnectionWhenInactive() throws SQLException, IOException {
        // Given
        Connection connection = connectionManager.beginTransaction();
        Transaction transaction = new SqliteTransaction(connection, connectionManager);
        transaction.close();

        // When/Then
        assertThrows(IllegalStateException.class, () -> ((SqliteTransaction) transaction).getConnection());
    }

    @Test
    @DisplayName("Should handle multiple close calls gracefully")
    void shouldHandleMultipleCloseCallsGracefully() throws SQLException, IOException {
        // Given
        Connection connection = connectionManager.beginTransaction();
        Transaction transaction = new SqliteTransaction(connection, connectionManager);

        // When
        transaction.close();
        transaction.close(); // Second close should not throw exception

        // Then
        assertFalse(transaction.isActive());
    }

    @Test
    @DisplayName("Should reject operations after close")
    void shouldRejectOperationsAfterClose() throws SQLException, IOException {
        // Given
        Connection connection = connectionManager.beginTransaction();
        Transaction transaction = new SqliteTransaction(connection, connectionManager);
        transaction.close();

        // When/Then
        assertThrows(IllegalStateException.class, () -> transaction.commit());
        assertThrows(IllegalStateException.class, () -> transaction.rollback());
    }
}