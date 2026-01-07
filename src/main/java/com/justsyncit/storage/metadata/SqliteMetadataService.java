package com.justsyncit.storage.metadata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.justsyncit.network.encryption.EncryptionService;
import com.justsyncit.metadata.BlindIndexSearch;
import com.justsyncit.storage.snapshot.MerkleNode;
import com.justsyncit.storage.snapshot.MerkleTreeDiffer;
// Snapshot is in same package
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * MetadataService implementation using SQLite.
 * This class now acts as a coordinator, delegating persistence logic to:
 * - SnapshotRepository
 * - FileRepository
 * - ChunkRepository
 * - MerkleRepository
 * ```java
 * package com.justsyncit.storage.metadata;
 * 
 * import com.fasterxml.jackson.databind.ObjectMapper;
 * import com.justsyncit.network.encryption.EncryptionService;
 * import com.justsyncit.metadata.BlindIndexSearch;
 * import com.justsyncit.storage.snapshot.MerkleNode;
 * import com.justsyncit.storage.snapshot.MerkleTreeDiffer;
 * // Snapshot is in same package
 * import org.slf4j.Logger;
 * import org.slf4j.LoggerFactory;
 * 
 * import java.io.IOException;
 * import java.sql.Connection;
 * import java.sql.ResultSet;
 * import java.sql.SQLException;
 * import java.sql.Statement;
 * import java.util.List;
 * import java.util.Optional;
 * import java.util.function.Supplier;
 * import java.util.stream.Stream;
 * 
 * /**
 * MetadataService implementation using SQLite.
 * This class now acts as a coordinator, delegating persistence logic to:
 * - SnapshotRepository
 * - FileRepository
 * - ChunkRepository
 * - MerkleRepository
 */
public class SqliteMetadataService implements MetadataService {

    private static final Logger logger = LoggerFactory.getLogger(SqliteMetadataService.class);

    private final DatabaseConnectionManager connectionManager;
    private final SnapshotRepository snapshotRepository;
    private final FileRepository fileRepository;
    private final ChunkRepository chunkRepository;
    private final MerkleRepository merkleRepository;

    @Override
    public Transaction beginTransaction() throws IOException {
        validateNotClosed();
        try {
            Connection connection = connectionManager.getConnection();
            connection.setAutoCommit(false);
            return new SqliteTransaction(connection, connectionManager);
        } catch (SQLException e) {
            throw new IOException("Failed to begin transaction", e);
        }
    }

    // Dependencies needed for repo initialization or migration?
    // Kept for now if legacy logic needs them, but most should be in Repos.
    private final ObjectMapper objectMapper;

    private volatile boolean closed = false;

    /**
     * Modern constructor with dependency injection for Repositories.
     */
    public SqliteMetadataService(DatabaseConnectionManager connectionManager,
            FileRepository fileRepository,
            ChunkRepository chunkRepository) {
        this.connectionManager = connectionManager;
        this.fileRepository = fileRepository;
        this.chunkRepository = chunkRepository;

        // Initialize other repositories internally for now, as they are not yet passed
        // in by Factory
        this.snapshotRepository = new SnapshotRepository(connectionManager);
        this.merkleRepository = new MerkleRepository(connectionManager,
                new com.fasterxml.jackson.databind.ObjectMapper());
        this.objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

        // Perform Schema Migration / Initialization
        initializeDatabase();
    }

    /**
     * Legacy constructor for backward compatibility and tests.
     * Initializes repositories internally.
     */
    public SqliteMetadataService(DatabaseConnectionManager connectionManager,
            EncryptionService encryptionService,
            Supplier<byte[]> keySupplier,
            BlindIndexSearch blindIndexSearch,
            ObjectMapper objectMapper) {
        this.connectionManager = connectionManager;
        this.objectMapper = objectMapper;

        this.chunkRepository = new ChunkRepository(connectionManager);
        this.fileRepository = new FileRepository(connectionManager, encryptionService, keySupplier, blindIndexSearch,
                this.chunkRepository);
        this.snapshotRepository = new SnapshotRepository(connectionManager);
        this.merkleRepository = new MerkleRepository(connectionManager, objectMapper);

        initializeDatabase();
    }

    private void initializeDatabase() {
        try (Connection connection = connectionManager.getConnection()) {
            // Enable foreign keys
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA foreign_keys = ON;");
                stmt.execute("PRAGMA journal_mode = WAL;"); // Performance
            }

            // Create Tables
            createTables(connection);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize SQLite database", e);
        }
    }

    private void createTables(Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            // Snapshots
            stmt.execute("CREATE TABLE IF NOT EXISTS snapshots (" +
                    "id TEXT PRIMARY KEY, " +
                    "name TEXT, " +
                    "created_at INTEGER NOT NULL, " +
                    "description TEXT, " +
                    "total_files INTEGER DEFAULT 0, " +
                    "total_size INTEGER DEFAULT 0, " +
                    "parent_id TEXT, " +
                    "merkle_root TEXT, " +
                    "FOREIGN KEY(parent_id) REFERENCES snapshots(id))");

            // Chunks (Deduplication)
            stmt.execute("CREATE TABLE IF NOT EXISTS chunks (" +
                    "hash TEXT PRIMARY KEY, " +
                    "size INTEGER NOT NULL, " +
                    "first_seen INTEGER, " +
                    "reference_count INTEGER DEFAULT 1, " +
                    "last_accessed INTEGER)");

            // Files
            stmt.execute("CREATE TABLE IF NOT EXISTS files (" +
                    "id TEXT PRIMARY KEY, " +
                    "snapshot_id TEXT NOT NULL, " +
                    "path TEXT NOT NULL, " + // Encrypted if AES enabled
                    "size INTEGER NOT NULL, " +
                    "modified_time INTEGER NOT NULL, " +
                    "file_hash TEXT, " +
                    "encryption_mode TEXT DEFAULT 'NONE', " +
                    "FOREIGN KEY(snapshot_id) REFERENCES snapshots(id))");

            // Indices for Files
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_files_snapshot ON files(snapshot_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_files_path ON files(path)");

            // File -> Chunks specific mapping
            stmt.execute("CREATE TABLE IF NOT EXISTS file_chunks (" +
                    "file_id TEXT, " +
                    "chunk_hash TEXT, " +
                    "chunk_order INTEGER NOT NULL, " +
                    "chunk_size INTEGER, " +
                    "PRIMARY KEY(file_id, chunk_order), " +
                    "FOREIGN KEY(file_id) REFERENCES files(id) ON DELETE CASCADE, " +
                    "FOREIGN KEY(chunk_hash) REFERENCES chunks(hash))");

            // Merkle Nodes
            stmt.execute("CREATE TABLE IF NOT EXISTS merkle_nodes (" +
                    "hash TEXT PRIMARY KEY, " +
                    "type TEXT NOT NULL, " +
                    "name TEXT, " +
                    "size INTEGER, " +
                    "children TEXT, " + // JSON
                    "file_id TEXT, " +
                    "compression TEXT DEFAULT 'NONE')"); // GZIP support

            // Search Tables
            // Blind Index
            stmt.execute("CREATE TABLE IF NOT EXISTS file_keywords (" +
                    "file_id TEXT, " +
                    "keyword_hash TEXT, " +
                    "PRIMARY KEY(file_id, keyword_hash), " +
                    "FOREIGN KEY(file_id) REFERENCES files(id) ON DELETE CASCADE)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_keywords_hash ON file_keywords(keyword_hash)");

            // FTS5 (Legacy/Fallback)
            // Note: FTS5 might not be available in all SQLite builds, handle gracefully?
            try {
                stmt.execute("CREATE VIRTUAL TABLE IF NOT EXISTS files_search USING fts5(file_id, path)");
            } catch (SQLException e) {
                logger.warn("FTS5 not supported; search functionality may be limited.", e);
            }
        }
    }

    private void validateNotClosed() throws IOException {
        if (closed) {
            throw new IOException("Metadata service is closed");
        }
    }

    // --- Delegation to Repositories ---

    @Override
    public Snapshot createSnapshot(String name, String description) throws IOException {
        validateNotClosed();
        // Create Snapshot object here
        Snapshot snapshot = new Snapshot(
                name, // ID can be name for now or UUID? Original code used name as ID in repo.
                name,
                description,
                java.time.Instant.now(),
                0,
                0);
        // Wait, original SnapshotRepository.createSnapshot(String name...) used name as
        // ID.
        // My new one accepts Snapshot object.
        // I should ensure ID is set.
        // Snapshot constructor: id, name, description, ...
        // So I'll use name as ID to preserve legacy behavior or generate UUID?
        // Original Repo code: String id = name;

        return snapshotRepository.createSnapshot(snapshot);
    }

    @Override
    public void updateSnapshot(Snapshot snapshot) throws IOException {
        validateNotClosed();
        snapshotRepository.updateSnapshot(snapshot);
    }

    @Override
    public Optional<Snapshot> getSnapshot(String id) throws IOException {
        validateNotClosed();
        return snapshotRepository.getSnapshot(id);
    }

    @Override
    public List<Snapshot> listSnapshots() throws IOException {
        validateNotClosed();
        return snapshotRepository.listSnapshots();
    }

    @Override
    public void deleteSnapshot(String id) throws IOException {
        validateNotClosed();
        snapshotRepository.deleteSnapshot(id);
    }

    @Override
    public String insertFile(FileMetadata file) throws IOException {
        validateNotClosed();
        return fileRepository.insertFile(file);
    }

    @Override
    public List<String> insertFiles(List<FileMetadata> files) throws IOException {
        validateNotClosed();
        // Delegate to repository batch insert for performance
        return fileRepository.insertFiles(files);
    }

    @Override
    public Optional<FileMetadata> getFile(String id) throws IOException {
        validateNotClosed();
        return fileRepository.getFile(id);
    }

    @Override
    public List<FileMetadata> getFilesInSnapshot(String snapshotId) throws IOException {
        return getFilesInSnapshot(snapshotId, true);
    }

    @Override
    public List<FileMetadata> getFilesInSnapshot(String snapshotId, boolean includeChunks) throws IOException {
        validateNotClosed();
        // Delegate to the more comprehensive getFilesInSnapshot method with default
        // values
        // for pathPrefix, limit, and offset.
        // The FileRepository's getFilesInSnapshot(String snapshotId, String pathPrefix,
        // int limit, int offset, boolean includeChunks)
        // is expected to handle the includeChunks parameter correctly.
        return fileRepository.getFilesInSnapshot(snapshotId, null, -1, 0, includeChunks);
    }

    @Override
    public List<FileMetadata> getFilesInSnapshot(String snapshotId, String pathPrefix, int limit, int offset,
            boolean includeChunks) throws IOException {
        validateNotClosed();
        return fileRepository.getFilesInSnapshot(snapshotId, pathPrefix, limit, offset, includeChunks);
    }

    @Override
    public int countFilesInSnapshot(String snapshotId, String pathPrefix) throws IOException {
        validateNotClosed();
        return fileRepository.countFilesInSnapshot(snapshotId, pathPrefix);
    }

    @Override
    public void updateFile(FileMetadata file) throws IOException {
        validateNotClosed();
        fileRepository.updateFile(file);
    }

    @Override
    public void deleteFile(String id) throws IOException {
        validateNotClosed();
        fileRepository.deleteFile(id);
    }

    @Override
    public Stream<ChunkMetadata> streamAllChunks() throws IOException {
        validateNotClosed();
        try {
            return chunkRepository.streamAllChunks();
        } catch (SQLException e) {
            throw new IOException("Failed to stream chunks", e);
        }
    }

    @Override
    public void recordChunkAccess(String chunkHash) throws IOException {
        validateNotClosed();
        if (chunkHash == null || chunkHash.isEmpty()) {
            throw new IllegalArgumentException("Chunk hash cannot be null or empty");
        }
        try {
            chunkRepository.recordChunkAccess(chunkHash);
        } catch (SQLException e) {
            throw new IOException("Failed to record chunk access", e);
        }
    }

    @Override
    public Optional<ChunkMetadata> getChunkMetadata(String hash) throws IOException {
        validateNotClosed();
        try {
            return chunkRepository.getChunkMetadata(hash);
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
        try {
            chunkRepository.upsertChunk(chunk);
        } catch (SQLException e) {
            throw new IOException("Failed to upsert chunk", e);
        }
    }

    @Override
    public boolean deleteChunk(String hash) throws IOException {
        validateNotClosed();
        try {
            return chunkRepository.deleteChunk(hash);
        } catch (SQLException e) {
            throw new IOException("Failed to delete chunk", e);
        }
    }

    @Override
    public MetadataStats getStats() throws IOException {
        validateNotClosed();
        try (Connection connection = connectionManager.getConnection()) {
            // Aggregate queries - can keep here or move to a ReportingRepository
            long totalSnapshots = 0;
            try (Statement s = connection.createStatement();
                    ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM snapshots")) {
                if (rs.next())
                    totalSnapshots = rs.getLong(1);
            }
            long totalFiles = 0;
            long totalLogicalSize = 0;
            try (Statement s = connection.createStatement();
                    ResultSet rs = s.executeQuery("SELECT COUNT(*), SUM(size) FROM files")) {
                if (rs.next()) {
                    totalFiles = rs.getLong(1);
                    totalLogicalSize = rs.getLong(2);
                }
            }
            long totalChunks = 0;
            long totalChunkSize = 0;
            double avgChunkSize = 0;
            try (Statement s = connection.createStatement();
                    ResultSet rs = s.executeQuery("SELECT COUNT(*), SUM(size), AVG(size) FROM chunks")) {
                if (rs.next()) {
                    totalChunks = rs.getLong(1);
                    totalChunkSize = rs.getLong(2);
                    avgChunkSize = rs.getDouble(3);
                }
            }
            double avgChunksPerFile = totalFiles > 0 ? (double) totalChunks / totalFiles : 0;
            double deduplicationRatio = totalChunkSize > 0 ? (double) totalLogicalSize / totalChunkSize : 1.0;

            return new MetadataStats(totalSnapshots, totalFiles, totalChunks, totalChunkSize, avgChunksPerFile,
                    avgChunkSize, deduplicationRatio);

        } catch (SQLException e) {
            throw new IOException("Failed to get stats", e);
        }
    }

    @Override
    public void upsertMerkleNode(MerkleNode node) throws IOException {
        validateNotClosed();
        merkleRepository.upsertMerkleNode(node);
    }

    @Override
    public Optional<MerkleNode> getMerkleNode(String hash) throws IOException {
        validateNotClosed();
        return merkleRepository.getMerkleNode(hash);
    }

    @Override
    public void setSnapshotRoot(String snapshotId, String rootHash) throws IOException {
        validateNotClosed();
        snapshotRepository.setSnapshotRoot(snapshotId, rootHash);
    }

    @Override
    public Optional<String> getSnapshotRoot(String snapshotId) throws IOException {
        validateNotClosed();
        return snapshotRepository.getSnapshotRoot(snapshotId);
    }

    @Override
    public void copyUnchangedFiles(String sourceSnapshotId, String targetSnapshotId, List<String> changedPaths)
            throws IOException {
        validateNotClosed();
        fileRepository.copyUnchangedFiles(sourceSnapshotId, targetSnapshotId, changedPaths);
    }

    @Override
    public List<MerkleTreeDiffer.DiffEntry> compareSnapshots(String snapshotId1, String snapshotId2)
            throws IOException {
        // Logic remains same - strictly service level orchestration
        String root1 = getSnapshotRoot(snapshotId1).orElse(null);
        String root2 = getSnapshotRoot(snapshotId2).orElse(null);
        MerkleNode n1 = root1 != null ? getMerkleNode(root1).orElse(null) : null;
        MerkleNode n2 = root2 != null ? getMerkleNode(root2).orElse(null) : null;
        return new MerkleTreeDiffer().diff(n1, n2);
    }

    @Override
    public boolean validateSnapshotChain(String snapshotId) throws IOException {
        validateNotClosed();
        // Validation logic - strictly service
        if (getSnapshot(snapshotId).isEmpty())
            return false;
        Optional<String> rootOpt = getSnapshotRoot(snapshotId);
        if (rootOpt.isEmpty())
            return false;
        if (getMerkleNode(rootOpt.get()).isEmpty())
            return false;

        String parent = snapshotRepository.getParentSnapshotId(snapshotId);
        if (parent != null) {
            return validateSnapshotChain(parent);
        }
        return true;
    }

    @Override
    public List<FileMetadata> searchFiles(String query) throws IOException {
        validateNotClosed();
        return fileRepository.searchFiles(query);
    }

    @Override
    public Optional<ChunkParityEntry> getChunkParityEntry(String chunkHash) throws IOException {
        return Optional.empty();
    }

    @Override
    public long createParityGroup(String algorithm) throws IOException {
        // Not implemented in this refactoring phase
        return 0;
    }

    @Override
    public void addChunkToParityGroup(long groupId, String chunkHash, int index, boolean isParity) throws IOException {
        // Not implemented in this refactoring phase
    }

    @Override
    public Optional<ParityGroupMetadata> getParityGroup(long groupId) throws IOException {
        return Optional.empty();
    }

    @Override
    public java.util.List<ChunkParityEntry> getChunksInParityGroup(long groupId) throws IOException {
        return java.util.Collections.emptyList();
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            // chunkRepository won't be closed? Pools handle it.
            // We just ensure connectionManager is closed if we own it?
            // Usually ServiceFactory closes manager.
            // But here we might want to flag close.
            connectionManager.close();
            closed = true;
        }
    }
}