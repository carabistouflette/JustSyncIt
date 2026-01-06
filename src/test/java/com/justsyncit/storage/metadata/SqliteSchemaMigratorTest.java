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
        assertEquals(7, migrator.getTargetVersion());
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
            java.util.Set<String> tableNames = new java.util.HashSet<>();
            try (var stmt = connection.createStatement();
                    var rs = stmt.executeQuery(
                            "SELECT name FROM sqlite_master WHERE type='table' "
                                    + "AND name NOT LIKE 'sqlite_%' ORDER BY name")) {
                while (rs.next()) {
                    tableNames.add(rs.getString("name"));
                }
            }

            assertTrue(tableNames.contains("chunks"));
            assertTrue(tableNames.contains("file_chunks"));
            assertTrue(tableNames.contains("files"));
            assertTrue(tableNames.contains("schema_version"));
            assertTrue(tableNames.contains("snapshots"));
            assertTrue(tableNames.contains("chunk_parity"));
            assertTrue(tableNames.contains("parity_groups"));

            // Verify schema version
            try (var stmt = connection.createStatement();
                    var rs = stmt.executeQuery("SELECT version FROM schema_version")) {
                assertTrue(rs.next());
                assertEquals(7, rs.getInt("version"));
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
        assertThrows(IllegalArgumentException.class, () -> SqliteSchemaMigrator.create().getCurrentVersion(null));
    }

    @Test
    @DisplayName("Should reject null connection for schema creation")
    void shouldRejectNullConnectionForSchemaCreation() {
        // When/Then
        assertThrows(IllegalArgumentException.class, () -> SqliteSchemaMigrator.create().createInitialSchema(null));
    }

    @Test
    @DisplayName("Should reject null connection for schema validation")
    void shouldRejectNullConnectionForSchemaValidation() {
        // When/Then
        assertThrows(IllegalArgumentException.class, () -> SqliteSchemaMigrator.create().validateSchema(null));
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
                assertEquals(7, rs.getInt("version"));
            }
        }
    }
}