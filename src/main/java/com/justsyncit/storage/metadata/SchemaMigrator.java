package com.justsyncit.storage.metadata;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Interface for database schema migration.
 * Follows Interface Segregation Principle by focusing only on schema
 * management.
 */
public interface SchemaMigrator {

    /**
     * Gets the current schema version from the database.
     *
     * @param connection database connection
     * @return current schema version, or 0 if no version is found
     * @throws SQLException             if an error occurs during version query
     * @throws IllegalArgumentException if connection is null
     */
    int getCurrentVersion(Connection connection) throws SQLException;

    /**
     * Gets the target schema version that this migrator supports.
     *
     * @return target schema version
     */
    int getTargetVersion();

    /**
     * Migrates the database schema from the current version to the target version.
     *
     * @param connection database connection
     * @throws SQLException             if the migration fails
     * @throws IllegalArgumentException if connection is null
     */
    void migrate(Connection connection) throws SQLException;

    /**
     * Creates the initial schema if no version is found.
     *
     * @param connection database connection
     * @throws SQLException             if schema creation fails
     * @throws IllegalArgumentException if connection is null
     */
    void createInitialSchema(Connection connection) throws SQLException;

    /**
     * Validates that the current schema is compatible with the target version.
     *
     * @param connection database connection
     * @return true if compatible, false otherwise
     * @throws SQLException             if validation fails
     * @throws IllegalArgumentException if connection is null
     */
    boolean validateSchema(Connection connection) throws SQLException;
}