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

import com.justsyncit.metadata.BlindIndexSearch;
import com.justsyncit.network.encryption.EncryptionException;
import com.justsyncit.network.encryption.EncryptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.justsyncit.storage.snapshot.MerkleNode;
import com.justsyncit.storage.snapshot.MerkleNode.Type;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * SQLite implementation of MetadataService.
 * Provides metadata management for snapshots, files, and chunks using SQLite
 * database.
 * Follows Single Responsibility Principle by focusing only on metadata
 * operations.
 */
public final class SqliteMetadataService implements MetadataService {

    /** Logger instance. */
    private static final Logger logger = LoggerFactory.getLogger(SqliteMetadataService.class);

    /** Database connection manager. */
    private final DatabaseConnectionManager connectionManager;
    /** Schema migrator for database management. */

    /** Encryption service (optional). */
    private final EncryptionService encryptionService;
    /** Blind index search utility (optional). */
    private final BlindIndexSearch blindIndexSearch;
    /** Object mapper for JSON serialization. */
    private final ObjectMapper objectMapper;
    /** Key supplier (optional). */
    private final Supplier<byte[]> keySupplier;

    /** Flag indicating if the service has been closed. */
    private volatile boolean closed;

    /**
     * Creates a new SqliteMetadataService with encryption support.
     *
     * @param connectionManager database connection manager
     * @param schemaMigrator    schema migrator
     * @param encryptionService encryption service (can be null)
     * @param blindIndexSearch  blind index search (can be null)
     * @param keySupplier       key supplier (can be null)
     * @throws IllegalArgumentException if required parameters are null
     */
    public SqliteMetadataService(DatabaseConnectionManager connectionManager,
            SchemaMigrator schemaMigrator,
            EncryptionService encryptionService,
            BlindIndexSearch blindIndexSearch,
            Supplier<byte[]> keySupplier) throws IOException {
        if (connectionManager == null) {
            throw new IllegalArgumentException("Connection manager cannot be null");
        }
        if (schemaMigrator == null) {
            throw new IllegalArgumentException("Schema migrator cannot be null");
        }

        this.connectionManager = connectionManager;

        this.encryptionService = encryptionService;
        this.blindIndexSearch = blindIndexSearch;
        this.objectMapper = new ObjectMapper(); // Initialize ObjectMapper
        this.keySupplier = keySupplier;
        this.closed = false;

        // Initialize database schema
        try (Connection connection = connectionManager.getConnection()) {
            // Enable foreign keys and performance optimizations for this connection
            try (var stmt = connection.createStatement()) {
                stmt.execute("PRAGMA foreign_keys=ON");
                // Note: Journal mode and timeouts are handled by ConnectionManager
                stmt.execute("PRAGMA synchronous=NORMAL");
                stmt.execute("PRAGMA cache_size=10000");
                stmt.execute("PRAGMA temp_store=MEMORY");
                stmt.execute("PRAGMA mmap_size=268435456"); // 256MB memory-mapped I/O
                stmt.execute("PRAGMA busy_timeout=5000"); // 5s timeout for lock acquisition
                stmt.execute("PRAGMA optimize");
            }

            // Only migrate if not already up to date to avoid redundant migrations
            int currentVersion = schemaMigrator.getCurrentVersion(connection);
            int targetVersion = schemaMigrator.getTargetVersion();
            if (currentVersion < targetVersion) {
                schemaMigrator.migrate(connection);
            } else {
                logger.debug("Database schema is already up to date, skipping migration");
            }
        } catch (SQLException e) {
            throw new IOException("Failed to initialize database schema", e);
        }

        logger.info("Initialized SQLite metadata service (Encryption: {})",
                encryptionService != null ? "Enabled" : "Disabled");
    }

    /**
     * Creates a new SqliteMetadataService without encryption support.
     * Kept for backward compatibility.
     */
    public SqliteMetadataService(DatabaseConnectionManager connectionManager,
            SchemaMigrator schemaMigrator) throws IOException {
        this(connectionManager, schemaMigrator, null, null, null);
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

        // Use the provided name as the ID for consistency with FileProcessor
        // expectations
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
    public void updateSnapshot(Snapshot snapshot) throws IOException {
        validateNotClosed();
        if (snapshot == null) {
            throw new IllegalArgumentException("Snapshot cannot be null");
        }

        String sql = "UPDATE snapshots SET total_files = ?, total_size = ? WHERE id = ?";

        try (Connection connection = connectionManager.getConnection();
                PreparedStatement stmt = connection.prepareStatement(sql)) {

            stmt.setLong(1, snapshot.getTotalFiles());
            stmt.setLong(2, snapshot.getTotalSize());
            stmt.setString(3, snapshot.getId());

            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                logger.debug("Updated snapshot stats: {}", snapshot);
            } else {
                logger.warn("Snapshot not found for update: {}", snapshot.getId());
            }

        } catch (SQLException e) {
            throw new IOException("Failed to update snapshot", e);
        }
    }

    @Override
    public Optional<Snapshot> getSnapshot(String id) throws IOException {
        validateNotClosed();
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Snapshot ID cannot be null or empty");
        }

        String sql = "SELECT id, name, created_at, description, total_files, total_size, merkle_root "
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

        String sql = "SELECT id, name, created_at, description, total_files, total_size, merkle_root "
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

        // Apply encryption if enabled
        boolean encryptionEnabled = false;
        String originalPath = file.getPath();
        byte[] key = null;

        if (encryptionService != null && keySupplier != null) {
            key = keySupplier.get();
            if (key != null) {
                encryptionEnabled = true;
                try {
                    // Deterministic encryption for path to ensure uniqueness constraint works
                    // IV Seed = SHA-256(path)
                    // We use the path bytes as seed directly (service handles hashing/truncation if
                    // needed,
                    // or we should hash it. AesGcmEncryptionService expects truncated hash usually,
                    // but let's check contract. It expects a byte array seed.

                    // Actually, to be safe and consistent with EncryptedContentStore, let's use the
                    // path hash.
                    // But we don't have blake3 service here easily.
                    // Let's rely on BlindIndexSearch or standard MessageDigest if needed?
                    // AesGcmEncryptionService.encryptDeterministic truncates the seed.
                    // Passing the path bytes directly as seed maintains determinism.

                    byte[] pathBytes = originalPath.getBytes(StandardCharsets.UTF_8);
                    byte[] encryptedPathFn = encryptionService.encryptDeterministic(pathBytes, key, pathBytes, null);
                    String encryptedPath = Base64.getEncoder().encodeToString(encryptedPathFn);

                    // Create modified file metadata with encrypted path
                    file = new FileMetadata(
                            file.getId(),
                            file.getSnapshotId(),
                            encryptedPath,
                            file.getSize(),
                            file.getModifiedTime(),
                            file.getFileHash(),
                            file.getChunkHashes());
                } catch (EncryptionException e) {
                    throw new IOException("Failed to encrypt file path", e);
                }
            }
        }

        String sql = "INSERT INTO files (id, snapshot_id, path, size, modified_time, file_hash, encryption_mode) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";

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
                // Ensure chunks exist (important for FK constraints)
                if (file.getChunkHashes() != null && !file.getChunkHashes().isEmpty()) {
                    ensureChunksExist(connection, file.getChunkHashes());
                }

                stmt.setString(1, file.getId());
                stmt.setString(2, file.getSnapshotId());
                stmt.setString(3, file.getPath());
                stmt.setLong(4, file.getSize());
                stmt.setLong(5, file.getModifiedTime().toEpochMilli());
                stmt.setString(6, file.getFileHash());
                stmt.setString(7, encryptionEnabled ? "AES" : "NONE");

                stmt.executeUpdate();

                // Insert file chunks
                insertFileChunks(connection, file);

                // Insert file keywords for blind index search
                if (encryptionEnabled && blindIndexSearch != null) {
                    insertFileKeywords(connection, file.getId(), originalPath);
                }

                logger.debug("Inserted file: {} (Encrypted: {})", originalPath, encryptionEnabled);
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

        // Prepare encryption context once
        boolean encryptionEnabled = false;
        byte[] key = null;
        if (encryptionService != null && keySupplier != null) {
            key = keySupplier.get();
            if (key != null) {
                encryptionEnabled = true;
            }
        }

        // 1. Prepare all chunk hashes from all files for bulk processing
        java.util.Set<String> allChunkHashes = new java.util.HashSet<>();
        // Also map files to their processed (possibly encrypted) version and original
        // path
        List<FileMetadata> processedFiles = new ArrayList<>(files.size());
        List<String> originalPaths = new ArrayList<>(files.size());

        for (FileMetadata file : files) {
            if (file.getChunkHashes() != null) {
                allChunkHashes.addAll(file.getChunkHashes());
            }

            originalPaths.add(file.getPath());
            FileMetadata processedFile = file;

            if (encryptionEnabled) {
                try {
                    byte[] pathBytes = file.getPath().getBytes(StandardCharsets.UTF_8);
                    byte[] encryptedPathFn = encryptionService.encryptDeterministic(pathBytes, key, pathBytes, null);
                    String encryptedPath = Base64.getEncoder().encodeToString(encryptedPathFn);

                    processedFile = new FileMetadata(
                            file.getId(),
                            file.getSnapshotId(),
                            encryptedPath,
                            file.getSize(),
                            file.getModifiedTime(),
                            file.getFileHash(),
                            file.getChunkHashes());
                } catch (EncryptionException e) {
                    throw new IOException("Failed to encrypt file path", e);
                }
            }
            processedFiles.add(processedFile);
        }

        try (Connection connection = connectionManager.getConnection()) {
            // Disable auto-commit for the entire batch operation
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);

            try {
                // 2. Ensure all chunks exist using efficient batch INSERT OR IGNORE
                if (!allChunkHashes.isEmpty()) {
                    ensureChunksExist(connection, new ArrayList<>(allChunkHashes));
                }

                // 3. Insert files
                String fileSql = "INSERT INTO files (id, snapshot_id, path, size, modified_time, file_hash, encryption_mode) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)";

                try (PreparedStatement stmt = connection.prepareStatement(fileSql)) {
                    int batchCount = 0;
                    for (FileMetadata file : processedFiles) {
                        stmt.setString(1, file.getId());
                        stmt.setString(2, file.getSnapshotId());
                        stmt.setString(3, file.getPath());
                        stmt.setLong(4, file.getSize());
                        stmt.setLong(5, file.getModifiedTime().toEpochMilli());
                        stmt.setString(6, file.getFileHash());
                        stmt.setString(7, encryptionEnabled ? "AES" : "NONE");
                        stmt.addBatch();
                        batchCount++;
                        insertedIds.add(file.getId());

                        if (batchCount >= 500) {
                            stmt.executeBatch();
                            batchCount = 0;
                        }
                    }
                    if (batchCount > 0) {
                        stmt.executeBatch();
                    }
                }

                // 4. Insert file_chunks mappings
                String chunkSql = "INSERT INTO file_chunks (file_id, chunk_hash, chunk_order, chunk_size) "
                        + "VALUES (?, ?, ?, ?)";

                try (PreparedStatement stmt = connection.prepareStatement(chunkSql)) {
                    int batchCount = 0;
                    for (FileMetadata file : processedFiles) {
                        List<String> chunkHashes = file.getChunkHashes();
                        if (chunkHashes != null) {
                            for (int i = 0; i < chunkHashes.size(); i++) {
                                String chunkHash = chunkHashes.get(i);
                                stmt.setString(1, file.getId());
                                stmt.setString(2, chunkHash);
                                stmt.setInt(3, i);
                                stmt.setInt(4, 65536); // Default estimation
                                stmt.addBatch();
                                batchCount++;

                                if (batchCount >= 500) {
                                    stmt.executeBatch();
                                    batchCount = 0;
                                }
                            }
                        }
                    }
                    if (batchCount > 0) {
                        stmt.executeBatch();
                    }
                }

                // 5. Insert file keywords if enabled
                if (encryptionEnabled && blindIndexSearch != null) {
                    insertFileKeywordsBatch(connection, insertedIds, originalPaths);
                }

                connection.commit();
                return insertedIds;

            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }

        } catch (SQLException e) {
            throw new IOException("Failed to insert files batch", e);
        }
    }

    @Override
    public Optional<FileMetadata> getFile(String id) throws IOException {
        validateNotClosed();
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("File ID cannot be null or empty");
        }

        String sql = "SELECT id, snapshot_id, path, size, modified_time, file_hash, encryption_mode "
                + "FROM files WHERE id = ?";

        try (Connection connection = connectionManager.getConnection();
                PreparedStatement stmt = connection.prepareStatement(sql)) {

            stmt.setString(1, id);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    List<String> chunkHashes = getFileChunks(connection, id);
                    FileMetadata file = mapRowToFileMetadata(rs, chunkHashes);

                    // Decrypt path if needed
                    String encryptionMode = rs.getString("encryption_mode");
                    if ("AES".equals(encryptionMode)) {
                        String decryptedPath = decryptPath(file.getPath(), encryptionMode);
                        file = new FileMetadata(
                                file.getId(),
                                file.getSnapshotId(),
                                decryptedPath,
                                file.getSize(),
                                file.getModifiedTime(),
                                file.getFileHash(),
                                file.getChunkHashes());
                    }

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

        // We can't order by path in SQL if it's encrypted.
        // We'll have to sort in Java if encryption is used.
        String sql = "SELECT id, snapshot_id, path, size, modified_time, file_hash, encryption_mode "
                + "FROM files WHERE snapshot_id = ?";

        try (Connection connection = connectionManager.getConnection();
                PreparedStatement stmt = connection.prepareStatement(sql)) {

            stmt.setString(1, snapshotId);

            try (ResultSet rs = stmt.executeQuery()) {
                List<FileMetadata> files = new ArrayList<>();
                while (rs.next()) {
                    String fileId = rs.getString("id");
                    List<String> chunkHashes = getFileChunks(connection, fileId);
                    FileMetadata file = mapRowToFileMetadata(rs, chunkHashes);

                    // Decrypt path if needed
                    String encryptionMode = rs.getString("encryption_mode");
                    if ("AES".equals(encryptionMode)) {
                        String decryptedPath = decryptPath(file.getPath(), encryptionMode);
                        file = new FileMetadata(
                                file.getId(),
                                file.getSnapshotId(),
                                decryptedPath,
                                file.getSize(),
                                file.getModifiedTime(),
                                file.getFileHash(),
                                file.getChunkHashes());
                    }
                    files.add(file);
                }

                // Sort by path in Java since SQL sort on encrypted paths is meaningless
                files.sort((f1, f2) -> f1.getPath().compareToIgnoreCase(f2.getPath()));

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

                // Calculate total logical size of all files
                long totalLogicalSize = 0;
                try (Statement stmt = connection.createStatement();
                        ResultSet rs = stmt.executeQuery(
                                "SELECT SUM(size) FROM files")) {
                    if (rs.next()) {
                        totalLogicalSize = rs.getLong(1);
                    }
                }

                // Calculate deduplication ratio: Logical Size / Physical Storage Size
                if (totalChunkSize > 0) {
                    deduplicationRatio = (double) totalLogicalSize / totalChunkSize;
                }
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

    // Merkle Tree operations

    private static class StoredMerkleChild {
        public String hash;
        public String type;
        public String name;
        public long size;
        public String fileId;

        public StoredMerkleChild(MerkleNode node) {
            this.hash = node.getHash();
            this.type = node.getType().name();
            this.name = node.getName();
            this.size = node.getSize();
            this.fileId = node.getFileId();
        }
    }

    @Override
    public void upsertMerkleNode(MerkleNode node) throws IOException {
        String sql = "INSERT OR REPLACE INTO merkle_nodes (hash, type, name, size, children, file_id, compression) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Connection connection = connectionManager.getConnection();
                PreparedStatement stmt = connection.prepareStatement(sql)) {

            stmt.setString(1, node.getHash());
            stmt.setString(2, node.getType().name());
            stmt.setString(3, node.getName());
            stmt.setLong(4, node.getSize());

            String childrenData = null;
            String compression = "NONE";

            if (node.getType() == Type.DIRECTORY && node.getChildren() != null) {
                List<StoredMerkleChild> storedChildren = new ArrayList<>();
                for (MerkleNode child : node.getChildren()) {
                    storedChildren.add(new StoredMerkleChild(child));
                }
                String json = objectMapper.writeValueAsString(storedChildren);

                // Compress if larger than threshold (e.g., 100 bytes)
                if (json.length() > 100) {
                    childrenData = compress(json);
                    compression = "GZIP";
                } else {
                    childrenData = json;
                }
            }
            stmt.setString(5, childrenData);
            stmt.setString(6, node.getFileId());
            stmt.setString(7, compression);

            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new IOException("Failed to upsert Merkle node: " + node.getHash(), e);
        }
    }

    @Override
    public Optional<MerkleNode> getMerkleNode(String hash) throws IOException {
        // Query both 'children' and 'compression' columns
        // NOTE: Older schema versions/rows might have NULL compression. We treat NULL
        // as "NONE".
        String sql = "SELECT hash, type, name, size, children, file_id, compression FROM merkle_nodes WHERE hash = ?";

        try (Connection connection = connectionManager.getConnection();
                PreparedStatement stmt = connection.prepareStatement(sql)) {

            stmt.setString(1, hash);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String typeStr = rs.getString("type");
                    Type type = Type.valueOf(typeStr);
                    String name = rs.getString("name");
                    long size = rs.getLong("size");
                    String childrenData = rs.getString("children");
                    String fileId = rs.getString("file_id");
                    String compression = rs.getString("compression");

                    List<MerkleNode> children = null;
                    if (childrenData != null && !childrenData.isEmpty()) {
                        String json;
                        if ("GZIP".equals(compression)) {
                            json = decompress(childrenData);
                        } else {
                            json = childrenData;
                        }

                        List<StoredMerkleChild> storedChildren = objectMapper.readValue(
                                json,
                                new TypeReference<List<StoredMerkleChild>>() {
                                });
                        children = new ArrayList<>();
                        for (StoredMerkleChild child : storedChildren) {
                            children.add(new MerkleNode(
                                    child.hash,
                                    Type.valueOf(child.type),
                                    child.name,
                                    child.size,
                                    null, // Lazy loaded children
                                    child.fileId));
                        }
                    }

                    return Optional.of(new MerkleNode(hash, type, name, size, children, fileId));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new IOException("Failed to get Merkle node: " + hash, e);
        }
    }

    @Override
    public void setSnapshotRoot(String snapshotId, String rootHash) throws IOException {
        String sql = "UPDATE snapshots SET merkle_root = ? WHERE id = ?";

        try (Connection connection = connectionManager.getConnection();
                PreparedStatement stmt = connection.prepareStatement(sql)) {

            stmt.setString(1, rootHash);
            stmt.setString(2, snapshotId);

            int rows = stmt.executeUpdate();
            if (rows == 0) {
                throw new IOException("Snapshot not found: " + snapshotId);
            }
        } catch (SQLException e) {
            throw new IOException("Failed to set snapshot root for: " + snapshotId, e);
        }
    }

    @Override
    public Optional<String> getSnapshotRoot(String snapshotId) throws IOException {
        String sql = "SELECT merkle_root FROM snapshots WHERE id = ?";

        try (Connection connection = connectionManager.getConnection();
                PreparedStatement stmt = connection.prepareStatement(sql)) {

            stmt.setString(1, snapshotId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String rootHash = rs.getString("merkle_root");
                    return Optional.ofNullable(rootHash);
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new IOException("Failed to get snapshot root for: " + snapshotId, e);
        }
    }

    @Override
    public void copyUnchangedFiles(String sourceSnapshotId, String targetSnapshotId, List<String> changedPaths)
            throws IOException {
        validateNotClosed();
        if (sourceSnapshotId == null || targetSnapshotId == null) {
            throw new IllegalArgumentException("Snapshot IDs cannot be null");
        }

        // We can use a temporary table or a WHERE NOT IN clause.
        // For distinct paths, NOT IN is good but strict limit on params (SQLite limit
        // ~999).
        // If changedPaths is large, we should batch or use temp table.
        // Given incremental backup, changed paths might be small or large.
        // Safest is to treat "changedPaths" as exclusions.

        // If changedPaths is empty, copy all.
        // If changedPaths is small, use NOT IN.
        // If large, create temp table.

        // Optimisation: "INSERT INTO files ... SELECT ... FROM files WHERE snapshot_id
        // = ? AND path NOT IN (...)"

        // Note: We need to handle IDs. New files need new unique IDs.
        // Generating UUIDs in SQLite is not standard.
        // We can append a suffix or use hex(randomblob(16)).

        // Actually, just copying the ID might violate PK if ID is global unique?
        // files table: id TEXT PRIMARY KEY.
        // So we MUST generate new IDs.
        // SQLite: lower(hex(randomblob(16))) produces random UUID-like strings.

        try (Connection connection = connectionManager.getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);

            try {
                // If we have changed paths, we can construct the query dynamically or use a
                // temp table.
                if (changedPaths != null && !changedPaths.isEmpty()) {
                    try (Statement stmt = connection.createStatement()) {
                        stmt.execute("CREATE TEMPORARY TABLE IF NOT EXISTS excluded_paths (path TEXT PRIMARY KEY)");
                        stmt.execute("DELETE FROM excluded_paths");
                    }

                    String insertExcluded = "INSERT INTO excluded_paths (path) VALUES (?)";
                    try (PreparedStatement stmt = connection.prepareStatement(insertExcluded)) {
                        int batchCount = 0;
                        for (String path : changedPaths) {
                            stmt.setString(1, path);
                            stmt.addBatch();
                            batchCount++;
                            if (batchCount >= 500) {
                                stmt.executeBatch();
                                batchCount = 0;
                            }
                        }
                        if (batchCount > 0)
                            stmt.executeBatch();
                    }
                }

                // Prepare INSERT statement
                // Generate new IDs using randomblob and hex
                // Note: We copy encryption_mode, etc.
                // We MUST perform this copy for: files, and also file_chunks?
                // Yes, file_chunks need to be copied for the new file IDs.
                // This is complex in SQL because we need the mapping from old_file_id to
                // new_file_id.
                // Doing this purely in SQL is hard if we generate IDs on the fly.

                // ALTERNATIVE:
                // Generate IDs in Java? Too slow for 100k.
                //
                // Better approach:
                // Use a mapping table for the copy:
                // CREATE TEMP TABLE file_copy_map (old_id TEXT, new_id TEXT);
                // INSERT INTO file_copy_map SELECT id, lower(hex(randomblob(16))) FROM files
                // WHERE snapshot_id = OLD AND path NOT IN excluded.
                // INSERT INTO files ... SELECT new_id, NEW_SNAP ... FROM files JOIN
                // file_copy_map ON ...
                // INSERT INTO file_chunks ... SELECT ... FROM file_chunks JOIN file_copy_map
                // ...

                // Let's implement this mapping approach.

                try (Statement stmt = connection.createStatement()) {
                    stmt.execute(
                            "CREATE TEMPORARY TABLE IF NOT EXISTS file_copy_map (old_id TEXT PRIMARY KEY, new_id TEXT)");
                    stmt.execute("DELETE FROM file_copy_map"); // Clear previous runs

                    String mappingSql = "INSERT INTO file_copy_map (old_id, new_id) " +
                            "SELECT id, lower(hex(randomblob(16))) FROM files " +
                            "WHERE snapshot_id = '" + sourceSnapshotId + "' " +
                            (changedPaths != null && !changedPaths.isEmpty()
                                    ? "AND path NOT IN (SELECT path FROM excluded_paths)"
                                    : "");

                    stmt.execute(mappingSql);

                    // Copy files
                    String copyFilesSql = "INSERT INTO files (id, snapshot_id, path, size, modified_time, file_hash, encryption_mode) "
                            +
                            "SELECT m.new_id, ?, f.path, f.size, f.modified_time, f.file_hash, f.encryption_mode " +
                            "FROM files f JOIN file_copy_map m ON f.id = m.old_id";

                    try (PreparedStatement ps = connection.prepareStatement(copyFilesSql)) {
                        ps.setString(1, targetSnapshotId);
                        ps.executeUpdate();
                    }

                    // Copy file chunks
                    String copyChunksSql = "INSERT INTO file_chunks (file_id, chunk_hash, chunk_order, chunk_size) " +
                            "SELECT m.new_id, fc.chunk_hash, fc.chunk_order, fc.chunk_size " +
                            "FROM file_chunks fc JOIN file_copy_map m ON fc.file_id = m.old_id";

                    stmt.execute(copyChunksSql);

                    // Copy file keywords if needed
                    String copyKeywordsSql = "INSERT INTO file_keywords (file_id, keyword_hash) " +
                            "SELECT m.new_id, fk.keyword_hash " +
                            "FROM file_keywords fk JOIN file_copy_map m ON fk.file_id = m.old_id";

                    stmt.execute(copyKeywordsSql);

                    // Clean up
                    stmt.execute("DROP TABLE IF EXISTS file_copy_map");
                    if (changedPaths != null && !changedPaths.isEmpty()) {
                        stmt.execute("DROP TABLE IF EXISTS excluded_paths");
                    }
                }

                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }
        } catch (SQLException e) {
            throw new IOException("Failed to copy unchanged files", e);
        }
    }

    @Override
    public List<com.justsyncit.storage.snapshot.MerkleTreeDiffer.DiffEntry> compareSnapshots(String snapshotId1,
            String snapshotId2) throws IOException {
        String rootHash1 = getSnapshotRoot(snapshotId1).orElse(null);
        String rootHash2 = getSnapshotRoot(snapshotId2).orElse(null);

        com.justsyncit.storage.snapshot.MerkleNode root1 = rootHash1 != null ? getMerkleNode(rootHash1).orElse(null)
                : null;
        com.justsyncit.storage.snapshot.MerkleNode root2 = rootHash2 != null ? getMerkleNode(rootHash2).orElse(null)
                : null;

        com.justsyncit.storage.snapshot.MerkleTreeDiffer differ = new com.justsyncit.storage.snapshot.MerkleTreeDiffer();
        return differ.diff(root1, root2);
    }

    @Override
    public boolean validateSnapshotChain(String snapshotId) throws IOException {
        Optional<Snapshot> snapshotOpt = getSnapshot(snapshotId);
        if (snapshotOpt.isEmpty()) {
            return false;
        }
        // Snapshot object doesn't have parentId, so we query it via SQL
        // Snapshot snapshot = snapshotOpt.get(); // Not needed if we query parent
        // separately

        // 1. Check Merkle Root
        Optional<String> rootHashOpt = getSnapshotRoot(snapshotId);
        if (rootHashOpt.isEmpty()) {
            // It's possible old snapshots don't have merkle roots if created before this
            // feature?
            // But going forward required. Let's assume invalid if missing for now, or warn?
            // Sticking to strict validation: invalid.
            return false;
        }
        String rootHash = rootHashOpt.get();
        if (getMerkleNode(rootHash).isEmpty()) {
            return false; // Root hash stored but node not found
        }

        // 2. Check Parent
        String parentId = getParentSnapshotId(snapshotId);
        if (parentId != null) {
            // Verify parent exists
            if (getSnapshot(parentId).isEmpty()) {
                return false;
            }
            // Recursive validation
            return validateSnapshotChain(parentId);
        }

        return true;
    }

    private String getParentSnapshotId(String snapshotId) throws IOException {
        String sql = "SELECT parent_id FROM snapshots WHERE id = ?";
        try (java.sql.Connection conn = connectionManager.getConnection();
                java.sql.PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, snapshotId);
            try (java.sql.ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("parent_id");
                }
            }
        } catch (SQLException e) {
            throw new IOException("Failed to get parent snapshot ID", e);
        }
        return null;
    }

    @Override
    public List<FileMetadata> searchFiles(String query) throws IOException {
        validateNotClosed();
        if (query == null || query.trim().isEmpty()) {
            throw new IllegalArgumentException("Search query cannot be null or empty");
        }

        // Prepare encryption context
        boolean encryptionEnabled = (encryptionService != null && keySupplier != null && keySupplier.get() != null);

        if (encryptionEnabled && blindIndexSearch != null) {
            // Use Blind Index Search
            // Tokenize query and search for matches
            Set<String> searchTokens = blindIndexSearch.tokenizeAndHash(query);
            if (searchTokens.isEmpty()) {
                return new ArrayList<>();
            }

            // Build query: JOIN file_keywords. return distinct files.
            StringBuilder sqlBuilder = new StringBuilder();
            sqlBuilder.append(
                    "SELECT DISTINCT f.id, f.snapshot_id, f.path, f.size, f.modified_time, f.file_hash, f.encryption_mode ");
            sqlBuilder.append("FROM files f ");
            sqlBuilder.append("JOIN file_keywords k ON f.id = k.file_id ");
            sqlBuilder.append("WHERE k.keyword_hash IN (");

            for (int i = 0; i < searchTokens.size(); i++) {
                if (i > 0)
                    sqlBuilder.append(",");
                sqlBuilder.append("?");
            }
            sqlBuilder.append(") LIMIT 100");

            List<String> tokenList = new ArrayList<>(searchTokens);

            try (Connection connection = connectionManager.getConnection();
                    PreparedStatement stmt = connection.prepareStatement(sqlBuilder.toString())) {

                for (int i = 0; i < tokenList.size(); i++) {
                    stmt.setString(i + 1, tokenList.get(i));
                }

                try (ResultSet rs = stmt.executeQuery()) {
                    List<FileMetadata> files = new ArrayList<>();
                    while (rs.next()) {
                        String fileId = rs.getString("id");
                        List<String> chunkHashes = getFileChunks(connection, fileId);
                        FileMetadata file = mapRowToFileMetadata(rs, chunkHashes);

                        // Decrypt path
                        String encryptionMode = rs.getString("encryption_mode");
                        if ("AES".equals(encryptionMode)) {
                            String decryptedPath = decryptPath(file.getPath(), encryptionMode);
                            file = new FileMetadata(
                                    file.getId(),
                                    file.getSnapshotId(),
                                    decryptedPath,
                                    file.getSize(),
                                    file.getModifiedTime(),
                                    file.getFileHash(),
                                    file.getChunkHashes());
                        }
                        files.add(file);
                    }
                    logger.debug("Encrypted search for '{}' returned {} results", query, files.size());
                    return files;
                }
            } catch (SQLException e) {
                throw new IOException("Failed to search files (Blind Index)", e);
            }

        } else {
            // Legacy FTS5 Search (Plaintext)
            // Prepare the FTS query
            // Escape special characters and wrap in quotes for exact phrase matching if
            // needed,
            // but for now, we'll assume the user provides a valid FTS5 query string OR
            // simple terms.
            // To be safe and support partial matches better with trigram, we can just pass
            // the query.
            // However, robust implementations often sanitizing.
            // For this version, we pass the query directly to FTS5 MATCH operator.

            String sql = "SELECT f.id, f.snapshot_id, f.path, f.size, f.modified_time, f.file_hash, f.encryption_mode "
                    + "FROM files f "
                    + "JOIN files_search fs ON f.id = fs.file_id "
                    + "WHERE fs.path MATCH ? "
                    + "ORDER BY rank "
                    + "LIMIT 100";

            try (Connection connection = connectionManager.getConnection();
                    PreparedStatement stmt = connection.prepareStatement(sql)) {

                stmt.setString(1, query);

                try (ResultSet rs = stmt.executeQuery()) {
                    List<FileMetadata> files = new ArrayList<>();
                    while (rs.next()) {
                        String fileId = rs.getString("id");
                        // retrieving chunks might be expensive for just search results,
                        // but FileMetadata requires it.
                        // Optimisation: Lazily load chunks? Or just fetch them.
                        // For < 100 results, fetching chunks is probably fine.
                        List<String> chunkHashes = getFileChunks(connection, fileId);
                        FileMetadata file = mapRowToFileMetadata(rs, chunkHashes);

                        // Handle decryption even in legacy mode (e.g. mixed content)
                        String encryptionMode = rs.getString("encryption_mode");
                        if ("AES".equals(encryptionMode)) {
                            String decryptedPath = decryptPath(file.getPath(), encryptionMode);
                            file = new FileMetadata(
                                    file.getId(),
                                    file.getSnapshotId(),
                                    decryptedPath,
                                    file.getSize(),
                                    file.getModifiedTime(),
                                    file.getFileHash(),
                                    file.getChunkHashes());
                        }

                        files.add(file);
                    }

                    logger.debug("Search for '{}' returned {} results", query, files.size());
                    return files;
                }
            } catch (SQLException e) {
                throw new IOException("Failed to search files", e);
            }
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
     * Inserts file keywords for blind index search.
     */
    private void insertFileKeywords(Connection connection, String fileId, String path) throws SQLException {
        Set<String> keywords = blindIndexSearch.tokenizeAndHash(path);
        if (keywords.isEmpty()) {
            return;
        }

        String sql = "INSERT INTO file_keywords (file_id, keyword_hash) VALUES (?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            for (String keywordHash : keywords) {
                stmt.setString(1, fileId);
                stmt.setString(2, keywordHash);
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }

    /**
     * Inserts file keywords in batch.
     */
    private void insertFileKeywordsBatch(Connection connection, List<String> fileIds, List<String> paths)
            throws SQLException {
        String sql = "INSERT INTO file_keywords (file_id, keyword_hash) VALUES (?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            int batchCount = 0;
            for (int i = 0; i < fileIds.size(); i++) {
                String fileId = fileIds.get(i);
                String path = paths.get(i);

                Set<String> keywords = blindIndexSearch.tokenizeAndHash(path);
                for (String keywordHash : keywords) {
                    stmt.setString(1, fileId);
                    stmt.setString(2, keywordHash);
                    stmt.addBatch();
                    batchCount++;

                    if (batchCount >= 500) {
                        stmt.executeBatch();
                        batchCount = 0;
                    }
                }
            }
            if (batchCount > 0) {
                stmt.executeBatch();
            }
        }
    }

    /**
     * Decrypts a path if it was encrypted.
     */
    private String decryptPath(String pathStr, String encryptionMode) {
        if (!"AES".equals(encryptionMode) || encryptionService == null || keySupplier == null) {
            return pathStr;
        }

        try {
            byte[] key = keySupplier.get();
            if (key == null) {
                return pathStr; // Cannot decrypt
            }

            byte[] encryptedBytes = Base64.getDecoder().decode(pathStr);
            byte[] decryptedBytes = encryptionService.decrypt(encryptedBytes, key);
            return new String(decryptedBytes, StandardCharsets.UTF_8);

        } catch (Exception e) {
            logger.error("Failed to decrypt path: " + pathStr, e);
            return pathStr + " (Decryption Failed)";
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
            int batchCount = 0;
            for (int i = 0; i < chunkHashes.size(); i++) {
                String chunkHash = chunkHashes.get(i);

                stmt.setString(1, file.getId());
                stmt.setString(2, chunkHash);
                stmt.setInt(3, i);
                // Use estimated chunk size to avoid foreign key constraint issues
                // The actual size will be updated when the chunk is accessed
                stmt.setInt(4, 65536); // Default chunk size
                stmt.addBatch();
                batchCount++;

                if (batchCount >= 500) {
                    stmt.executeBatch();
                    batchCount = 0;
                }
            }
            if (batchCount > 0) {
                stmt.executeBatch();
            }
        }
    }

    /**
     * Ensures all chunks exist in the chunks table.
     * Uses INSERT OR IGNORE to efficiently handle existing chunks.
     */
    private void ensureChunksExist(Connection connection, List<String> chunkHashes) throws SQLException {
        // Use INSERT OR IGNORE to skip existing chunks without a separate SELECT
        String insertSql = "INSERT OR IGNORE INTO chunks (hash, size, first_seen, reference_count, last_accessed) "
                + "VALUES (?, ?, ?, ?, ?)";

        try (PreparedStatement insertStmt = connection.prepareStatement(insertSql)) {
            long now = System.currentTimeMillis();
            int batchCount = 0;

            for (String chunkHash : chunkHashes) {
                insertStmt.setString(1, chunkHash);
                insertStmt.setLong(2, 65536); // Default chunk size
                insertStmt.setLong(3, now); // first_seen
                insertStmt.setLong(4, 1); // reference_count
                insertStmt.setLong(5, now); // last_accessed
                insertStmt.addBatch();
                batchCount++;

                if (batchCount >= 500) {
                    insertStmt.executeBatch();
                    batchCount = 0;
                }
            }
            if (batchCount > 0) {
                insertStmt.executeBatch();
            }
        }
    }

    /**
     * Gets the current foreign key setting.
     */
    @SuppressWarnings("unused")
    private boolean getForeignKeySetting(Connection connection) throws SQLException {
        try (var stmt = connection.createStatement();
                ResultSet rs = stmt.executeQuery("PRAGMA foreign_keys")) {
            return rs.getBoolean(1);
        }
    }

    /**
     * Sets the foreign key setting.
     */
    @SuppressWarnings("unused")
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

    /**
     * Compresses a string using GZIP and encoding to Base64.
     */
    private String compress(String str) throws IOException {
        if (str == null || str.isEmpty()) {
            return str;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(str.getBytes(StandardCharsets.UTF_8));
        }
        return Base64.getEncoder().encodeToString(out.toByteArray());
    }

    /**
     * Decompresses a Base64 encoded GZIP string.
     */
    private String decompress(String str) throws IOException {
        if (str == null || str.isEmpty()) {
            return str;
        }
        byte[] bytes = Base64.getDecoder().decode(str);
        try (GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(bytes));
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = gis.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }
            return out.toString(StandardCharsets.UTF_8);
        }
    }
}