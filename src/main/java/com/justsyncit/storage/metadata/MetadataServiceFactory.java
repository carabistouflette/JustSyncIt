package com.justsyncit.storage.metadata;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;

/**
 * Factory for creating metadata service instances.
 * Follows Dependency Inversion Principle by depending on abstractions.
 * Provides a clean interface for creating different types of metadata services.
 */
public final class MetadataServiceFactory {

    /** Logger instance. */
    private static final Logger logger = LoggerFactory.getLogger(MetadataServiceFactory.class);

    /** Default maximum number of database connections in the pool. */
    private static final int DEFAULT_MAX_CONNECTIONS = 10;

    /** Shared connection manager for in-memory databases (for testing). */
    private static volatile DatabaseConnectionManager sharedInMemoryConnectionManager;

    /** Private constructor to prevent instantiation. */
    private MetadataServiceFactory() {
        // Utility class
    }

    /**
     * Creates a SQLite-based metadata service with file-based database.
     *
     * @param databasePath path to the SQLite database file
     * @return a new MetadataService instance
     * @throws IOException              if the service cannot be created
     * @throws IllegalArgumentException if databasePath is null or empty
     */
    public static MetadataService createFileBasedService(String databasePath) throws IOException {
        return createFileBasedService(databasePath, DEFAULT_MAX_CONNECTIONS);
    }

    /**
     * Creates a SQLite-based metadata service with file-based database and custom
     * connection pool size.
     *
     * @param databasePath   path to the SQLite database file
     * @param maxConnections maximum number of connections in the pool
     * @return a new MetadataService instance
     * @throws IOException              if the service cannot be created
     * @throws IllegalArgumentException if parameters are invalid
     */
    public static MetadataService createFileBasedService(String databasePath, int maxConnections) throws IOException {
        if (databasePath == null || databasePath.trim().isEmpty()) {
            throw new IllegalArgumentException("Database path cannot be null or empty");
        }
        if (maxConnections <= 0) {
            throw new IllegalArgumentException("Max connections must be positive");
        }

        // Ensure parent directory exists
        Path dbPath = Paths.get(databasePath);
        Path parentDir = dbPath.getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
            try {
                Files.createDirectories(parentDir);
                logger.info("Created database directory: {}", parentDir);
            } catch (IOException e) {
                throw new IOException("Failed to create database directory: " + parentDir, e);
            }
        }

        logger.info("Creating file-based metadata service with database: {}", databasePath);

        DatabaseConnectionManager connectionManager = new SqliteConnectionManager(databasePath, maxConnections);

        ChunkRepository chunkRepository = new ChunkRepository(connectionManager);
        FileRepository fileRepository = new FileRepository(connectionManager, null, null, null, chunkRepository);

        return new SqliteMetadataService(connectionManager, fileRepository, chunkRepository);
    }

    /**
     * Creates a SQLite-based metadata service with in-memory database.
     * This is useful for testing or temporary operations.
     *
     * @return a new MetadataService instance with in-memory database
     * @throws IOException if the service cannot be created
     */
    public static MetadataService createInMemoryService() throws IOException {
        return createInMemoryService(DEFAULT_MAX_CONNECTIONS);
    }

    /**
     * Creates a SQLite-based metadata service with in-memory database and custom
     * connection pool size.
     * This is useful for testing or temporary operations.
     *
     * @param maxConnections maximum number of connections in the pool
     * @return a new MetadataService instance with in-memory database
     * @throws IOException              if the service cannot be created
     * @throws IllegalArgumentException if maxConnections is not positive
     */
    public static MetadataService createInMemoryService(int maxConnections) throws IOException {
        if (maxConnections <= 0) {
            throw new IllegalArgumentException("Max connections must be positive");
        }

        logger.info("Creating in-memory metadata service");

        // Use shared connection manager for in-memory databases to ensure all
        // operations
        // use the same database instance
        if (sharedInMemoryConnectionManager == null) {
            synchronized (MetadataServiceFactory.class) {
                if (sharedInMemoryConnectionManager == null) {
                    DatabaseConnectionManager localManager = new SqliteConnectionManager("file::memory:?cache=shared",
                            maxConnections);

                    // Get the shared connection to ensure it's initialized
                    try {
                        try (Connection sharedConnection = localManager.getConnection()) {
                            logger.info("Pre-initialized shared in-memory database connection: {}", sharedConnection);
                        }
                    } catch (Exception e) {
                        logger.error("Failed to pre-initialize shared in-memory database", e);
                        throw new IOException("Failed to initialize shared in-memory database", e);
                    }

                    sharedInMemoryConnectionManager = localManager;
                }
            }
        }

        ChunkRepository chunkRepository = new ChunkRepository(sharedInMemoryConnectionManager);
        FileRepository fileRepository = new FileRepository(sharedInMemoryConnectionManager, null, null, null,
                chunkRepository);

        return new SqliteMetadataService(sharedInMemoryConnectionManager, fileRepository, chunkRepository);
    }

    /**
     * Creates a SQLite-based metadata service with encryption support.
     *
     * @param databasePath      path to the SQLite database file
     * @param encryptionService encryption service
     * @param blindIndexSearch  blind index search utility
     * @param keySupplier       key supplier
     * @return a new MetadataService instance
     * @throws IOException              if the service cannot be created
     * @throws IllegalArgumentException if databasePath is null or empty
     */
    public static MetadataService createEncryptedFileBasedService(String databasePath,
            com.justsyncit.network.encryption.EncryptionService encryptionService,
            com.justsyncit.metadata.BlindIndexSearch blindIndexSearch,
            java.util.function.Supplier<byte[]> keySupplier) throws IOException {
        if (databasePath == null || databasePath.trim().isEmpty()) {
            throw new IllegalArgumentException("Database path cannot be null or empty");
        }

        // Ensure parent directory exists
        Path dbPath = Paths.get(databasePath);
        Path parentDir = dbPath.getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
            try {
                Files.createDirectories(parentDir);
                logger.info("Created database directory: {}", parentDir);
            } catch (IOException e) {
                throw new IOException("Failed to create database directory: " + parentDir, e);
            }
        }

        logger.info("Creating encrypted file-based metadata service with database: {}", databasePath);

        DatabaseConnectionManager connectionManager = new SqliteConnectionManager(databasePath,
                DEFAULT_MAX_CONNECTIONS);

        ChunkRepository chunkRepository = new ChunkRepository(connectionManager);
        FileRepository fileRepository = new FileRepository(connectionManager, encryptionService, keySupplier,
                blindIndexSearch, chunkRepository);

        return new SqliteMetadataService(connectionManager, fileRepository, chunkRepository);
    }

    /**
     * Creates a SQLite-based metadata service with default configuration.
     *
     * @return a new MetadataService instance with default configuration
     * @throws IOException if the service cannot be created
     */
    public static MetadataService createDefaultService() throws IOException {
        String dbPath = System.getProperty("justsyncit.db.path");
        if (dbPath == null || dbPath.trim().isEmpty()) {
            dbPath = System.getProperty("user.home") + "/.justsyncit/metadata.db";
        }
        return createFileBasedService(dbPath);
    }
}