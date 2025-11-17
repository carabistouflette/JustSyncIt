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
 * Unit tests for SqliteSchemaMigrator.
 * Tests schema creation, migration, and validation.
 */
@DisplayName("SqliteSchemaMigrator Tests")
class SqliteSchemaMigratorTest {

    /** Temporary directory for test database. */
    private Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("schema-test");
    }

    @AfterEach
    void tearDown() throws IOException {
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
    @DisplayName("Should create migrator successfully")
    void shouldCreateMigrator() {
        // When
        SchemaMigrator migrator = SqliteSchemaMigrator.create();

        // Then
        assertNotNull(migrator);
        assertEquals(1, migrator.getTargetVersion());
    }

    @Test
    @DisplayName("Should get initial version as 0")
    void shouldGetInitialVersionAsZero() throws SQLException, IOException {
        // Given
        String dbPath = tempDir.resolve("test.db").toString();
        try (Connection connection = java.sql.DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
            // When
            int version = SqliteSchemaMigrator.create().getCurrentVersion(connection);

            // Then
            assertEquals(0, version);
        }
    }

    @Test
    @DisplayName("Should create initial schema")
    void shouldCreateInitialSchema() throws SQLException, IOException {
        // Given
        String dbPath = tempDir.resolve("test.db").toString();
        try (Connection connection = java.sql.DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
            // When
            SqliteSchemaMigrator.create().createInitialSchema(connection);

            // Then
            // Verify tables exist (excluding SQLite system tables)
            try (var stmt = connection.createStatement();
                    var rs = stmt.executeQuery(
                            "SELECT name FROM sqlite_master WHERE type='table' "
                                    + "AND name NOT LIKE 'sqlite_%' ORDER BY name")) {
                assertTrue(rs.next());
                assertEquals("chunks", rs.getString("name"));
                assertTrue(rs.next());
                assertEquals("file_chunks", rs.getString("name"));
                assertTrue(rs.next());
                assertEquals("files", rs.getString("name"));
                assertTrue(rs.next());
                assertEquals("schema_version", rs.getString("name"));
                assertTrue(rs.next());
                assertEquals("snapshots", rs.getString("name"));
                assertFalse(rs.next());
            }

            // Verify schema version
            try (var stmt = connection.createStatement();
                    var rs = stmt.executeQuery("SELECT version FROM schema_version")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt("version"));
            }
        }
    }

    @Test
    @DisplayName("Should validate schema successfully")
    void shouldValidateSchemaSuccessfully() throws SQLException, IOException {
        // Given
        String dbPath = tempDir.resolve("test.db").toString();
        try (Connection connection = java.sql.DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
            SchemaMigrator migrator = SqliteSchemaMigrator.create();
            migrator.createInitialSchema(connection);

            // When
            boolean isValid = migrator.validateSchema(connection);

            // Then
            assertTrue(isValid);
        }
    }

    @Test
    @DisplayName("Should reject null connection for version check")
    void shouldRejectNullConnectionForVersionCheck() {
        // When/Then
        assertThrows(IllegalArgumentException.class, () ->
                SqliteSchemaMigrator.create().getCurrentVersion(null));
    }

    @Test
    @DisplayName("Should reject null connection for schema creation")
    void shouldRejectNullConnectionForSchemaCreation() {
        // When/Then
        assertThrows(IllegalArgumentException.class, () ->
                SqliteSchemaMigrator.create().createInitialSchema(null));
    }

    @Test
    @DisplayName("Should reject null connection for schema validation")
    void shouldRejectNullConnectionForSchemaValidation() {
        // When/Then
        assertThrows(IllegalArgumentException.class, () ->
                SqliteSchemaMigrator.create().validateSchema(null));
    }

    @Test
    @DisplayName("Should migrate from version 0 to target")
    void shouldMigrateFromVersion0ToTarget() throws SQLException, IOException {
        // Given
        String dbPath = tempDir.resolve("test.db").toString();
        try (Connection connection = java.sql.DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
            // When
            SqliteSchemaMigrator.create().migrate(connection);

            // Then
            // Verify schema version after migration
            try (var stmt = connection.createStatement();
                    var rs = stmt.executeQuery("SELECT version FROM schema_version")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt("version"));
            }
        }
    }
}