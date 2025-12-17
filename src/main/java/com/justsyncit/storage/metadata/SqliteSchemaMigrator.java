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

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * SQLite implementation of SchemaMigrator.
 * Handles database schema creation and migration.
 * Follows Single Responsibility Principle by focusing only on schema
 * management.
 */
public final class SqliteSchemaMigrator implements SchemaMigrator {

    /** Logger instance. */
    private static final Logger logger = LoggerFactory.getLogger(SqliteSchemaMigrator.class);

    /** Private constructor to prevent instantiation. */
    private SqliteSchemaMigrator() {
        // Utility class
    }

    /**
     * Creates a new instance of SqliteSchemaMigrator.
     *
     * @return a new schema migrator instance
     */
    public static SqliteSchemaMigrator create() {
        return new SqliteSchemaMigrator();
    }

    @Override
    public int getCurrentVersion(Connection connection) throws SQLException {
        if (connection == null) {
            throw new IllegalArgumentException("Connection cannot be null");
        }

        // First check if schema_version table exists
        try (Statement stmt = connection.createStatement()) {
            String tableCheckQuery = "SELECT name FROM sqlite_master WHERE type='table' "
                    + "AND name='schema_version'";
            ResultSet rs = stmt.executeQuery(tableCheckQuery);
            if (!rs.next()) {
                logger.debug("Schema version table not found, assuming version 0");
                return 0;
            }
        }

        // Table exists, query the version
        try (Statement stmt = connection.createStatement()) {
            ResultSet rs = stmt.executeQuery(DatabaseSchema.getVersionQuery());
            if (rs.next()) {
                int version = rs.getInt("version");
                logger.debug("Current database schema version: {}", version);
                return version;
            } else {
                logger.debug("No schema version found, assuming version 0");
                return 0;
            }
        }
    }

    @Override
    public int getTargetVersion() {
        return DatabaseSchema.SCHEMA_VERSION;
    }

    @Override
    public void migrate(Connection connection) throws SQLException {
        if (connection == null) {
            throw new IllegalArgumentException("Connection cannot be null");
        }

        int currentVersion = getCurrentVersion(connection);
        int targetVersion = getTargetVersion();

        if (currentVersion == targetVersion) {
            logger.info("Database schema is already up to date (version {})", targetVersion);
            return;
        }

        if (currentVersion > targetVersion) {
            throw new SQLException(
                    String.format("Database schema version %d is newer than target version %d",
                            currentVersion, targetVersion));
        }

        logger.info("Migrating database schema from version {} to {}", currentVersion, targetVersion);

        // Handle schema migrations
        if (currentVersion == 0) {
            createInitialSchema(connection);
            // createInitialSchema already inserts the target version, so no need to update
        } else {
            if (currentVersion < 2) {
                // Migration from version 1 to 2
                migrateToVersion2(connection);
                currentVersion = 2;
            }
            if (currentVersion < 3) {
                // Migration from version 2 to 3
                migrateToVersion3(connection);
                currentVersion = 3;
            }
            if (currentVersion < 4) {
                // Migration from version 3 to 4
                migrateToVersion4(connection);
                currentVersion = 4;
            }
        }

        logger.info("Database schema migration completed successfully");
    }

    @Override
    public void createInitialSchema(Connection connection) throws SQLException {
        if (connection == null) {
            throw new IllegalArgumentException("Connection cannot be null");
        }

        logger.info("Creating initial database schema");

        try (Statement stmt = connection.createStatement()) {
            // Execute all create statements
            String[] createStatements = DatabaseSchema.getCreateStatements();
            for (String createStatement : createStatements) {
                logger.debug("Executing: {}", createStatement);
                stmt.execute(createStatement);
            }

            // Insert schema version using Statement
            String insertVersionSql = DatabaseSchema.getInsertVersionStatement();
            try (Statement versionStmt = connection.createStatement()) {
                versionStmt.executeUpdate(insertVersionSql);
            }

            logger.info("Initial database schema created successfully");
        }
    }

    /**
     * Migrates database schema from version 1 to 2.
     * Adds foreign key constraint to file_chunks table.
     *
     * @param connection database connection
     * @throws SQLException if migration fails
     */
    private void migrateToVersion2(Connection connection) throws SQLException {
        logger.info("Migrating database schema from version 1 to 2");
        try (Statement stmt = connection.createStatement()) {
            // SQLite doesn't support adding foreign key constraints to existing tables
            // directly
            // We need to recreate the file_chunks table with the foreign key constraint
            logger.debug("Recreating file_chunks table with foreign key constraint");
            // Drop the existing table
            stmt.execute("DROP TABLE IF EXISTS file_chunks");
            // Recreate with foreign key constraint
            stmt.execute("CREATE TABLE file_chunks ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "file_id TEXT NOT NULL,"
                    + "chunk_hash TEXT NOT NULL,"
                    + "chunk_order INTEGER NOT NULL,"
                    + "chunk_size INTEGER NOT NULL,"
                    + "FOREIGN KEY (file_id) REFERENCES files(id) ON DELETE CASCADE,"
                    + "FOREIGN KEY (chunk_hash) REFERENCES chunks(hash) ON DELETE CASCADE,"
                    + "UNIQUE(file_id, chunk_order)"
                    + ")");
            // Recreate indexes
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_file_chunks_file_id ON file_chunks(file_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_file_chunks_chunk_hash ON file_chunks(chunk_hash)");
            // Update schema version to 2
            stmt.execute("UPDATE schema_version SET version = 2");
            logger.info("Successfully migrated database schema to version 2");
        }
    }

    /**
     * Migrates database schema from version 2 to 3.
     * Adds FTS5 virtual table for files and related triggers.
     * Starts populating the FTS index from existing files.
     *
     * @param connection database connection
     * @throws SQLException if migration fails
     */
    private void migrateToVersion3(Connection connection) throws SQLException {
        logger.info("Migrating database schema from version 2 to 3");
        try (Statement stmt = connection.createStatement()) {
            // Create FTS5 virtual table
            logger.debug("Creating files_search FTS5 table");
            stmt.execute("CREATE VIRTUAL TABLE IF NOT EXISTS files_search USING fts5("
                    + "file_id UNINDEXED, "
                    + "path"
                    // + "content_model = 'trigram'" // Removed for compatibility check
                    + ")");

            // Create triggers
            logger.debug("Creating triggers for FTS synchronization");
            stmt.execute("CREATE TRIGGER IF NOT EXISTS files_ai AFTER INSERT ON files BEGIN "
                    + "  INSERT INTO files_search(file_id, path) "
                    + "  VALUES (new.id, new.path); "
                    + "END");

            stmt.execute("CREATE TRIGGER IF NOT EXISTS files_ad AFTER DELETE ON files BEGIN "
                    + "  DELETE FROM files_search WHERE file_id = old.id; "
                    + "END");

            stmt.execute("CREATE TRIGGER IF NOT EXISTS files_au AFTER UPDATE ON files BEGIN "
                    + "  UPDATE files_search SET path = new.path "
                    + "  WHERE file_id = old.id; "
                    + "END");

            // Populate existing data
            logger.info("Populating FTS index with existing files...");
            stmt.execute("INSERT INTO files_search(file_id, path) SELECT id, path FROM files");
            logger.info("FTS index population complete");

            // Update schema version to 3
            stmt.execute("UPDATE schema_version SET version = 3");
            logger.info("Successfully migrated database schema to version 3");
        }
    }

    /**
     * Migrates database schema from version 3 to 4.
     * Adds encryption support columns and file keywords table.
     *
     * @param connection database connection
     * @throws SQLException if migration fails
     */
    private void migrateToVersion4(Connection connection) throws SQLException {
        logger.info("Migrating database schema from version 3 to 4");
        try (Statement stmt = connection.createStatement()) {
            // Add encryption_mode column to files table
            logger.debug("Adding encryption_mode column to files table");
            // Check if column exists first (idempotency check for partial migrations)
            boolean colExists = false;
            try (ResultSet rs = stmt.executeQuery("PRAGMA table_info(files)")) {
                while (rs.next()) {
                    if ("encryption_mode".equals(rs.getString("name"))) {
                        colExists = true;
                        break;
                    }
                }
            }
            if (!colExists) {
                stmt.execute("ALTER TABLE files ADD COLUMN encryption_mode TEXT DEFAULT 'NONE'");
            }

            // Create file_keywords table
            logger.debug("Creating file_keywords table");
            stmt.execute("CREATE TABLE IF NOT EXISTS file_keywords ("
                    + "file_id TEXT NOT NULL,"
                    + "keyword_hash TEXT NOT NULL,"
                    + "FOREIGN KEY (file_id) REFERENCES files(id) ON DELETE CASCADE"
                    + ")");

            // Create indexes for file_keywords
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_file_keywords_hash ON file_keywords(keyword_hash)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_file_keywords_file_id ON file_keywords(file_id)");

            // Update schema version to 4
            stmt.execute("UPDATE schema_version SET version = 4");
            logger.info("Successfully migrated database schema to version 4");
        }
    }

    @Override
    public boolean validateSchema(Connection connection) throws SQLException {
        if (connection == null) {
            throw new IllegalArgumentException("Connection cannot be null");
        }

        int currentVersion = getCurrentVersion(connection);
        int targetVersion = getTargetVersion();

        if (currentVersion != targetVersion) {
            logger.warn("Schema validation failed: current version {}, target version {}",
                    currentVersion, targetVersion);
            return false;
        }

        // Check that all required tables exist
        try (Statement stmt = connection.createStatement()) {
            String tableQuery = "SELECT name FROM sqlite_master WHERE type='table'";
            ResultSet rs = stmt.executeQuery(tableQuery);

            String[] requiredTables = { "snapshots", "files", "file_chunks", "chunks", "schema_version",
                    "files_search" };
            for (String table : requiredTables) {
                boolean found = false;
                while (rs.next()) {
                    if (table.equals(rs.getString("name"))) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    logger.warn("Required table '{}' not found in schema", table);
                    return false;
                }
            }
        }

        logger.debug("Schema validation passed");
        return true;
    }
}