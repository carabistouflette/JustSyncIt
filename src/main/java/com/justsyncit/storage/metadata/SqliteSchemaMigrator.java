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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * SQLite implementation of SchemaMigrator.
 * Handles database schema creation and migration.
 * Follows Single Responsibility Principle by focusing only on schema management.
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
    @SuppressFBWarnings("SQL_PREPARED_STATEMENT_GENERATED_FROM_NONCONSTANT_STRING")
    public int getCurrentVersion(Connection connection) throws SQLException {
        if (connection == null) {
            throw new IllegalArgumentException("Connection cannot be null");
        }

        try (PreparedStatement stmt = connection.prepareStatement(DatabaseSchema.getVersionQuery())) {
            ResultSet rs = stmt.executeQuery();
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

        // For now, we only support creating initial schema
        if (currentVersion == 0) {
            createInitialSchema(connection);
        } else {
            // Future migrations would be handled here
            throw new SQLException(
                String.format("Migration from version %d to %d is not supported",
                currentVersion, targetVersion));
        }

        logger.info("Database schema migration completed successfully");
    }

    @Override
    @SuppressFBWarnings({
        "SQL_NONCONSTANT_STRING_PASSED_TO_EXECUTE",
        "SQL_PREPARED_STATEMENT_GENERATED_FROM_NONCONSTANT_STRING"
    })
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

            // Insert schema version using PreparedStatement to avoid SpotBugs warning
            try (PreparedStatement versionStmt = connection.prepareStatement(
                    DatabaseSchema.getInsertVersionStatement())) {
                versionStmt.executeUpdate();
            }

            logger.info("Initial database schema created successfully");
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

            String[] requiredTables = {"snapshots", "files", "file_chunks", "chunks", "schema_version"};
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