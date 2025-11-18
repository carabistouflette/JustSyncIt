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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * SQLite implementation of MetadataService.
 * Provides metadata management for snapshots, files, and chunks using SQLite database.
 * Follows Single Responsibility Principle by focusing only on metadata operations.
 */
public final class SqliteMetadataService implements MetadataService {

    /** Logger instance. */
    private static final Logger logger = LoggerFactory.getLogger(SqliteMetadataService.class);

    /** Database connection manager. */
    private final DatabaseConnectionManager connectionManager;
    /** Schema migrator for database management. */
    private final SchemaMigrator schemaMigrator;
    /** Flag indicating if the service has been closed. */
    private volatile boolean closed;

    /**
     * Creates a new SqliteMetadataService.
     *
     * @param connectionManager database connection manager
     * @param schemaMigrator schema migrator
     * @throws IllegalArgumentException if any parameter is null
     */
    public SqliteMetadataService(DatabaseConnectionManager connectionManager,
                      SchemaMigrator schemaMigrator) throws IOException {
        if (connectionManager == null) {
            throw new IllegalArgumentException("Connection manager cannot be null");
        }
        if (schemaMigrator == null) {
            throw new IllegalArgumentException("Schema migrator cannot be null");
        }

        this.connectionManager = connectionManager;
        this.schemaMigrator = schemaMigrator;
        this.closed = false;

        // Initialize database schema
        try (Connection connection = connectionManager.getConnection()) {
            // Enable foreign keys and performance optimizations for this connection
            try (var stmt = connection.createStatement()) {
                stmt.execute("PRAGMA foreign_keys=ON");
                // Use DELETE journal mode instead of WAL to avoid connection isolation issues in tests
                stmt.execute("PRAGMA journal_mode=DELETE");
                stmt.execute("PRAGMA synchronous=NORMAL");
                stmt.execute("PRAGMA cache_size=10000");
                stmt.execute("PRAGMA temp_store=MEMORY");
                stmt.execute("PRAGMA mmap_size=268435456"); // 256MB memory-mapped I/O
                stmt.execute("PRAGMA optimize");
            }
            schemaMigrator.migrate(connection);
        } catch (SQLException e) {
            throw new IOException("Failed to initialize database schema", e);
        }

        logger.info("Initialized SQLite metadata service");
    }

    @Override
    public Transaction beginTransaction() throws IOException {
        validateNotClosed();
        try {
            Connection connection = connectionManager.beginTransaction();
            return new SqliteTransaction(connection, connectionManager);
        } catch (SQLException e) {
            throw new IOException("Failed to begin transaction", e);
        }
    }

    @Override
    public Snapshot createSnapshot(String name, String description) throws IOException {
        validateNotClosed();
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Snapshot name cannot be null or empty");
        }

        // Use the provided name as the ID for consistency with FileProcessor expectations
        String id = name;
        Instant now = Instant.now();

        String sql = "INSERT INTO snapshots (id, name, created_at, description, total_files, total_size) "
                + "VALUES (?, ?, ?, ?, 0, 0)";

        try (Connection connection = connectionManager.getConnection();
                PreparedStatement stmt = connection.prepareStatement(sql)) {

            stmt.setString(1, id);
            stmt.setString(2, name);
            stmt.setLong(3, now.toEpochMilli());
            stmt.setString(4, description);

            stmt.executeUpdate();

            Snapshot snapshot = new Snapshot(id, name, description, now, 0, 0);
            logger.debug("Created snapshot: {}", snapshot);
            return snapshot;

        } catch (SQLException e) {
            throw new IOException("Failed to create snapshot", e);
        }
    }

    @Override
    public Optional<Snapshot> getSnapshot(String id) throws IOException {
        validateNotClosed();
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Snapshot ID cannot be null or empty");
        }

        String sql = "SELECT id, name, created_at, description, total_files, total_size "
                + "FROM snapshots WHERE id = ?";

        try (Connection connection = connectionManager.getConnection();
                PreparedStatement stmt = connection.prepareStatement(sql)) {

            stmt.setString(1, id);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Snapshot snapshot = mapRowToSnapshot(rs);
                    logger.debug("Retrieved snapshot: {}", snapshot);
                    return Optional.of(snapshot);
                } else {
                    logger.debug("Snapshot not found: {}", id);
                    return Optional.empty();
                }
            }
        } catch (SQLException e) {
            throw new IOException("Failed to get snapshot", e);
        }
    }

    @Override
    public List<Snapshot> listSnapshots() throws IOException {
        validateNotClosed();

        String sql = "SELECT id, name, created_at, description, total_files, total_size "
                + "FROM snapshots ORDER BY created_at DESC";

        try (Connection connection = connectionManager.getConnection();
                PreparedStatement stmt = connection.prepareStatement(sql);
                ResultSet rs = stmt.executeQuery()) {

            List<Snapshot> snapshots = new ArrayList<>();
            while (rs.next()) {
                snapshots.add(mapRowToSnapshot(rs));
            }

            logger.debug("Listed {} snapshots", snapshots.size());
            return snapshots;

        } catch (SQLException e) {
            throw new IOException("Failed to list snapshots", e);
        }
    }

    @Override
    public void deleteSnapshot(String id) throws IOException {
        validateNotClosed();
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Snapshot ID cannot be null or empty");
        }

        String sql = "DELETE FROM snapshots WHERE id = ?";

        try (Connection connection = connectionManager.getConnection();
                PreparedStatement stmt = connection.prepareStatement(sql)) {

            stmt.setString(1, id);
            int rowsAffected = stmt.executeUpdate();

            if (rowsAffected > 0) {
                logger.debug("Deleted snapshot: {}", id);
            } else {
                logger.warn("Snapshot not found for deletion: {}", id);
            }

        } catch (SQLException e) {
            throw new IOException("Failed to delete snapshot", e);
        }
    }

    @Override
    public String insertFile(FileMetadata file) throws IOException {
        validateNotClosed();
        if (file == null) {
            throw new IllegalArgumentException("File metadata cannot be null");
        }

        String sql = "INSERT INTO files (id, snapshot_id, path, size, modified_time, file_hash) "
                + "VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection connection = connectionManager.getConnection()) {
            // First verify that the snapshot exists
            String checkSnapshotSql = "SELECT id FROM snapshots WHERE id = ?";
            try (PreparedStatement checkStmt = connection.prepareStatement(checkSnapshotSql)) {
                checkStmt.setString(1, file.getSnapshotId());
                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (!rs.next()) {
                        logger.error("Snapshot {} does not exist when trying to insert file {}",
                                file.getSnapshotId(), file.getPath());
                        throw new IOException("Snapshot does not exist: " + file.getSnapshotId());
                    }
                }
            }
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, file.getId());
                stmt.setString(2, file.getSnapshotId());
                stmt.setString(3, file.getPath());
                stmt.setLong(4, file.getSize());
                stmt.setLong(5, file.getModifiedTime().toEpochMilli());
                stmt.setString(6, file.getFileHash());

                stmt.executeUpdate();

                // Insert file chunks
                insertFileChunks(connection, file);

                logger.debug("Inserted file: {}", file.getPath());
                return file.getId();
            }

        } catch (SQLException e) {
            throw new IOException("Failed to insert file", e);
        }
    }

    @Override
    public List<String> insertFiles(List<FileMetadata> files) throws IOException {
        validateNotClosed();
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("Files list cannot be null or empty");
        }
        if (files.stream().anyMatch(f -> f == null)) {
            throw new IllegalArgumentException("Files list cannot contain null elements");
        }

        List<String> insertedIds = new ArrayList<>();

        String sql = "INSERT INTO files (id, snapshot_id, path, size, modified_time, file_hash) "
                + "VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection connection = connectionManager.getConnection()) {
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                for (FileMetadata file : files) {
                    stmt.setString(1, file.getId());
                    stmt.setString(2, file.getSnapshotId());
                    stmt.setString(3, file.getPath());
                    stmt.setLong(4, file.getSize());
                    stmt.setLong(5, file.getModifiedTime().toEpochMilli());
                    stmt.setString(6, file.getFileHash());
                    logger.debug("Adding file to batch: {} with hash: {}", file.getPath(), file.getFileHash());
                    stmt.addBatch();
                }

                logger.debug("Executing batch insert for {} files", files.size());
                int[] results = stmt.executeBatch();
                logger.debug("Batch insert results: {}", results.length);
                // Insert file chunks for all files
                for (FileMetadata file : files) {
                    insertFileChunks(connection, file);
                    insertedIds.add(file.getId());
                    logger.debug("Inserted file: {}", file.getPath());
                }
                return insertedIds;
            }
        } catch (SQLException e) {
            throw new IOException("Failed to insert files", e);
        }
    }

    @Override
    public Optional<FileMetadata> getFile(String id) throws IOException {
        validateNotClosed();
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("File ID cannot be null or empty");
        }

        String sql = "SELECT id, snapshot_id, path, size, modified_time, file_hash "
                + "FROM files WHERE id = ?";

        try (Connection connection = connectionManager.getConnection();
                PreparedStatement stmt = connection.prepareStatement(sql)) {

            stmt.setString(1, id);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    List<String> chunkHashes = getFileChunks(connection, id);
                    FileMetadata file = mapRowToFileMetadata(rs, chunkHashes);
                    logger.debug("Retrieved file: {}", file.getPath());
                    return Optional.of(file);
                } else {
                    logger.debug("File not found: {}", id);
                    return Optional.empty();
                }
            }
        } catch (SQLException e) {
            throw new IOException("Failed to get file", e);
        }
    }

    @Override
    public List<FileMetadata> getFilesInSnapshot(String snapshotId) throws IOException {
        validateNotClosed();
        if (snapshotId == null || snapshotId.trim().isEmpty()) {
            throw new IllegalArgumentException("Snapshot ID cannot be null or empty");
        }

        String sql = "SELECT id, snapshot_id, path, size, modified_time, file_hash "
                + "FROM files WHERE snapshot_id = ? ORDER BY path";

        try (Connection connection = connectionManager.getConnection();
                PreparedStatement stmt = connection.prepareStatement(sql)) {

            stmt.setString(1, snapshotId);

            try (ResultSet rs = stmt.executeQuery()) {
                List<FileMetadata> files = new ArrayList<>();
                while (rs.next()) {
                    String fileId = rs.getString("id");
                    List<String> chunkHashes = getFileChunks(connection, fileId);
                    files.add(mapRowToFileMetadata(rs, chunkHashes));
                }

                logger.debug("Retrieved {} files for snapshot {}", files.size(), snapshotId);
                return files;
            }
        } catch (SQLException e) {
            throw new IOException("Failed to get files in snapshot", e);
        }
    }

    @Override
    public void updateFile(FileMetadata file) throws IOException {
        validateNotClosed();
        if (file == null) {
            throw new IllegalArgumentException("File metadata cannot be null");
        }

        String sql = "UPDATE files SET path = ?, size = ?, modified_time = ?, file_hash = ? "
                + "WHERE id = ?";

        try (Connection connection = connectionManager.getConnection();
                PreparedStatement stmt = connection.prepareStatement(sql)) {

            stmt.setString(1, file.getPath());
            stmt.setLong(2, file.getSize());
            stmt.setLong(3, file.getModifiedTime().toEpochMilli());
            stmt.setString(4, file.getFileHash());
            stmt.setString(5, file.getId());

            int rowsAffected = stmt.executeUpdate();

            if (rowsAffected > 0) {
                // Update file chunks
                deleteFileChunks(connection, file.getId());
                insertFileChunks(connection, file);
                logger.debug("Updated file: {}", file.getPath());
            } else {
                logger.warn("File not found for update: {}", file.getId());
            }

        } catch (SQLException e) {
            throw new IOException("Failed to update file", e);
        }
    }

    @Override
    public void deleteFile(String id) throws IOException {
        validateNotClosed();
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("File ID cannot be null or empty");
        }

        try (Connection connection = connectionManager.getConnection()) {
            // Delete file chunks first (foreign key constraint)
            deleteFileChunks(connection, id);

            String sql = "DELETE FROM files WHERE id = ?";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, id);
                int rowsAffected = stmt.executeUpdate();

                if (rowsAffected > 0) {
                    logger.debug("Deleted file: {}", id);
                } else {
                    logger.warn("File not found for deletion: {}", id);
                }
            }
        } catch (SQLException e) {
            throw new IOException("Failed to delete file", e);
        }
    }

    @Override
    public void recordChunkAccess(String chunkHash) throws IOException {
        validateNotClosed();
        if (chunkHash == null || chunkHash.trim().isEmpty()) {
            throw new IllegalArgumentException("Chunk hash cannot be null or empty");
        }

        String sql = "UPDATE chunks SET last_accessed = ? WHERE hash = ?";

        try (Connection connection = connectionManager.getConnection()) {
            // Disable auto-commit for better performance on single updates
            connection.setAutoCommit(false);
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setLong(1, Instant.now().toEpochMilli());
                stmt.setString(2, chunkHash);
                stmt.executeUpdate();
            }
            connection.commit();
            logger.debug("Recorded access for chunk: {}", chunkHash);

        } catch (SQLException e) {
            throw new IOException("Failed to record chunk access", e);
        }
    }

    @Override
    public Optional<ChunkMetadata> getChunkMetadata(String hash) throws IOException {
        validateNotClosed();
        if (hash == null || hash.trim().isEmpty()) {
            throw new IllegalArgumentException("Chunk hash cannot be null or empty");
        }

        String sql = "SELECT hash, size, first_seen, reference_count, last_accessed "
                + "FROM chunks WHERE hash = ?";

        try (Connection connection = connectionManager.getConnection();
                PreparedStatement stmt = connection.prepareStatement(sql)) {

            stmt.setString(1, hash);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    ChunkMetadata chunk = mapRowToChunkMetadata(rs);
                    logger.debug("Retrieved chunk metadata: {}", hash);
                    return Optional.of(chunk);
                } else {
                    logger.debug("Chunk metadata not found: {}", hash);
                    return Optional.empty();
                }
            }
        } catch (SQLException e) {
            throw new IOException("Failed to get chunk metadata", e);
        }
    }

    @Override
    public void upsertChunk(ChunkMetadata chunk) throws IOException {
        validateNotClosed();
        if (chunk == null) {
            throw new IllegalArgumentException("Chunk metadata cannot be null");
        }

        String sql = "INSERT OR REPLACE INTO chunks (hash, size, first_seen, reference_count, last_accessed) "
                + "VALUES (?, ?, ?, ?, ?)";

        try (Connection connection = connectionManager.getConnection();
                PreparedStatement stmt = connection.prepareStatement(sql)) {

            stmt.setString(1, chunk.getHash());
            stmt.setLong(2, chunk.getSize());
            stmt.setLong(3, chunk.getFirstSeen().toEpochMilli());
            stmt.setLong(4, chunk.getReferenceCount());
            stmt.setLong(5, chunk.getLastAccessed().toEpochMilli());

            stmt.executeUpdate();
            logger.debug("Upserted chunk metadata: {}", chunk.getHash());

        } catch (SQLException e) {
            throw new IOException("Failed to upsert chunk metadata", e);
        }
    }

    @Override
    public boolean deleteChunk(String hash) throws IOException {
        validateNotClosed();
        if (hash == null || hash.trim().isEmpty()) {
            throw new IllegalArgumentException("Chunk hash cannot be null or empty");
        }

        String sql = "DELETE FROM chunks WHERE hash = ?";

        try (Connection connection = connectionManager.getConnection();
                PreparedStatement stmt = connection.prepareStatement(sql)) {

            stmt.setString(1, hash);
            int rowsAffected = stmt.executeUpdate();

            if (rowsAffected > 0) {
                logger.debug("Deleted chunk metadata: {}", hash);
                return true;
            } else {
                logger.debug("Chunk metadata not found for deletion: {}", hash);
                return false;
            }

        } catch (SQLException e) {
            throw new IOException("Failed to delete chunk metadata", e);
        }
    }

    @Override
    public MetadataStats getStats() throws IOException {
        validateNotClosed();

        try (Connection connection = connectionManager.getConnection()) {
            // Get snapshot count
            long totalSnapshots = 0;
            try (Statement stmt = connection.createStatement();
                    ResultSet rs = stmt.executeQuery(
                            "SELECT COUNT(*) FROM snapshots")) {
                if (rs.next()) {
                    totalSnapshots = rs.getLong(1);
                }
            }

            // Get file count
            long totalFiles = 0;
            try (Statement stmt = connection.createStatement();
                    ResultSet rs = stmt.executeQuery(
                            "SELECT COUNT(*) FROM files")) {
                if (rs.next()) {
                    totalFiles = rs.getLong(1);
                }
            }

            // Get chunk statistics
            long totalChunks = 0;
            long totalChunkSize = 0;
            double avgChunkSize = 0;
            try (Statement stmt = connection.createStatement();
                    ResultSet rs = stmt.executeQuery(
                            "SELECT COUNT(*), SUM(size), AVG(size) FROM chunks")) {
                if (rs.next()) {
                    totalChunks = rs.getLong(1);
                    totalChunkSize = rs.getLong(2);
                    avgChunkSize = rs.getDouble(3);
                }
            }

            // Calculate average chunks per file and deduplication ratio
            double avgChunksPerFile = 0.0;
            double deduplicationRatio = 1.0;

            if (totalFiles > 0) {
                avgChunksPerFile = (double) totalChunks / totalFiles;
                // FIXME: Calculate actual deduplication ratio when we have more data
                deduplicationRatio = 1.0;
            }

            MetadataStats stats = new MetadataStats(
                    totalSnapshots, totalFiles, totalChunks,
                    totalChunkSize, avgChunksPerFile, avgChunkSize, deduplicationRatio);

            logger.debug("Generated metadata stats: {}", stats);
            return stats;

        } catch (SQLException e) {
            throw new IOException("Failed to get metadata statistics", e);
        }
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            connectionManager.close();
            closed = true;
            logger.info("Closed SQLite metadata service");
        }
    }

    /**
     * Inserts file chunks for a file.
     */
    private void insertFileChunks(Connection connection, FileMetadata file) throws SQLException {
        // First ensure all chunks exist in the chunks table
        ensureChunksExist(connection, file.getChunkHashes());

        String sql = "INSERT INTO file_chunks (file_id, chunk_hash, chunk_order, chunk_size) "
                + "VALUES (?, ?, ?, ?)";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            List<String> chunkHashes = file.getChunkHashes();
            for (int i = 0; i < chunkHashes.size(); i++) {
                String chunkHash = chunkHashes.get(i);

                stmt.setString(1, file.getId());
                stmt.setString(2, chunkHash);
                stmt.setInt(3, i);
                // Use estimated chunk size to avoid foreign key constraint issues
                // The actual size will be updated when the chunk is accessed
                stmt.setInt(4, 65536); // Default chunk size
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }

    /**
     * Ensures all chunks exist in the chunks table.
     * Creates missing chunks with default metadata.
     */
    private void ensureChunksExist(Connection connection, List<String> chunkHashes) throws SQLException {
        String checkSql = "SELECT hash FROM chunks WHERE hash = ?";
        String insertSql = "INSERT OR IGNORE INTO chunks (hash, size, first_seen, reference_count, last_accessed) "
                + "VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement checkStmt = connection.prepareStatement(checkSql);
                PreparedStatement insertStmt = connection.prepareStatement(insertSql)) {

            long now = System.currentTimeMillis();

            for (String chunkHash : chunkHashes) {
                // Check if chunk exists
                checkStmt.setString(1, chunkHash);
                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (!rs.next()) {
                        // Chunk doesn't exist, create it with default metadata
                        insertStmt.setString(1, chunkHash);
                        insertStmt.setLong(2, 65536); // Default chunk size
                        insertStmt.setLong(3, now); // first_seen
                        insertStmt.setLong(4, 1); // reference_count
                        insertStmt.setLong(5, now); // last_accessed
                        insertStmt.addBatch();
                    }
                }
            }
            // Execute batch insert for missing chunks
            insertStmt.executeBatch();
        }
    }

    /**
     * Gets the current foreign key setting.
     */
    private boolean getForeignKeySetting(Connection connection) throws SQLException {
        try (var stmt = connection.createStatement();
                ResultSet rs = stmt.executeQuery("PRAGMA foreign_keys")) {
            return rs.getBoolean(1);
        }
    }

    /**
     * Sets the foreign key setting.
     */
    private void setForeignKeySetting(Connection connection, boolean enabled) throws SQLException {
        try (var stmt = connection.createStatement()) {
            if (enabled) {
                stmt.execute("PRAGMA foreign_keys=ON");
            } else {
                stmt.execute("PRAGMA foreign_keys=OFF");
            }
        }
    }

    /**
     * Gets chunk hashes for a file.
     */
    private List<String> getFileChunks(Connection connection, String fileId) throws SQLException {
        String sql = "SELECT chunk_hash FROM file_chunks WHERE file_id = ? ORDER BY chunk_order";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, fileId);

            try (ResultSet rs = stmt.executeQuery()) {
                List<String> chunkHashes = new ArrayList<>();
                while (rs.next()) {
                    chunkHashes.add(rs.getString("chunk_hash"));
                }
                return chunkHashes;
            }
        }
    }

    /**
     * Deletes file chunks for a file.
     */
    private void deleteFileChunks(Connection connection, String fileId) throws SQLException {
        String sql = "DELETE FROM file_chunks WHERE file_id = ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, fileId);
            stmt.executeUpdate();
        }
    }

    /**
     * Maps a database row to a Snapshot object.
     */
    private Snapshot mapRowToSnapshot(ResultSet rs) throws SQLException {
        String id = rs.getString("id");
        String name = rs.getString("name");
        String description = rs.getString("description");
        Instant createdAt = Instant.ofEpochMilli(rs.getLong("created_at"));
        long totalFiles = rs.getLong("total_files");
        long totalSize = rs.getLong("total_size");

        return new Snapshot(id, name, description, createdAt, totalFiles, totalSize);
    }

    /**
     * Maps a database row to a FileMetadata object.
     */
    private FileMetadata mapRowToFileMetadata(ResultSet rs, List<String> chunkHashes) throws SQLException {
        String id = rs.getString("id");
        String snapshotId = rs.getString("snapshot_id");
        String path = rs.getString("path");
        long size = rs.getLong("size");
        Instant modifiedTime = Instant.ofEpochMilli(rs.getLong("modified_time"));
        String fileHash = rs.getString("file_hash");

        return new FileMetadata(id, snapshotId, path, size, modifiedTime, fileHash, chunkHashes);
    }

    /**
     * Maps a database row to a ChunkMetadata object.
     */
    private ChunkMetadata mapRowToChunkMetadata(ResultSet rs) throws SQLException {
        String hash = rs.getString("hash");
        long size = rs.getLong("size");
        Instant firstSeen = Instant.ofEpochMilli(rs.getLong("first_seen"));
        long referenceCount = rs.getLong("reference_count");
        Instant lastAccessed = Instant.ofEpochMilli(rs.getLong("last_accessed"));

        return new ChunkMetadata(hash, size, firstSeen, referenceCount, lastAccessed);
    }

    /**
     * Validates that the service is not closed.
     */
    private void validateNotClosed() throws IOException {
        if (closed) {
            throw new IOException("Metadata service has been closed");
        }
    }
}