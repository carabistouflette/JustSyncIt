package com.justsyncit.storage.metadata;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.HashSet;

/**
 * Repository for Chunk metadata operations.
 * Handles deduplicated chunks and file-chunk mappings.
 */
public final class ChunkRepository {

    private static final Logger logger = LoggerFactory.getLogger(ChunkRepository.class);

    private final DatabaseConnectionManager connectionManager;

    public ChunkRepository(DatabaseConnectionManager connectionManager) {
        if (connectionManager == null) {
            throw new IllegalArgumentException("Connection manager cannot be null");
        }
        this.connectionManager = connectionManager;
    }

    /**
     * Ensures that the given chunk hashes exist in the chunks table.
     * Uses INSERT OR IGNORE for efficiency.
     */
    public void ensureChunksExist(Connection connection, List<String> chunkHashes) throws SQLException {
        if (chunkHashes == null || chunkHashes.isEmpty()) {
            return;
        }

        // last_accessed is NOT NULL in schema, so we must provide it.
        String sql = "INSERT OR IGNORE INTO chunks (hash, size, first_seen, last_accessed) VALUES (?, 0, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            Set<String> uniqueHashes = new HashSet<>(chunkHashes);
            int batchCount = 0;
            long now = System.currentTimeMillis();
            for (String hash : uniqueHashes) {
                stmt.setString(1, hash);
                stmt.setLong(2, now);
                stmt.setLong(3, now);
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

    public void insertFileChunks(Connection connection, FileMetadata file) throws SQLException {
        List<String> chunkHashes = file.getChunkHashes();
        if (chunkHashes == null || chunkHashes.isEmpty()) {
            return;
        }

        String sql = "INSERT INTO file_chunks (file_id, chunk_hash, chunk_order, chunk_size) VALUES (?, ?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            int batchCount = 0;
            for (int i = 0; i < chunkHashes.size(); i++) {
                String chunkHash = chunkHashes.get(i);
                stmt.setString(1, file.getId());
                stmt.setString(2, chunkHash);
                stmt.setInt(3, i);
                stmt.setInt(4, 65536); // Default/Approx size if not tracked per chunk
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

    public void deleteFileChunks(Connection connection, String fileId) throws SQLException {
        String sql = "DELETE FROM file_chunks WHERE file_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, fileId);
            stmt.executeUpdate();
        }
    }

    public List<String> getFileChunks(Connection connection, String fileId) throws SQLException {
        String sql = "SELECT chunk_hash FROM file_chunks WHERE file_id = ? ORDER BY chunk_order ASC";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, fileId);
            try (ResultSet rs = stmt.executeQuery()) {
                List<String> chunks = new ArrayList<>();
                while (rs.next()) {
                    chunks.add(rs.getString("chunk_hash"));
                }
                return chunks;
            }
        }
    }

    public java.util.Map<String, List<String>> getFileChunksForFiles(Connection connection, List<String> fileIds)
            throws SQLException {
        if (fileIds == null || fileIds.isEmpty()) {
            return java.util.Collections.emptyMap();
        }

        // SQLite limit is often 999. We'll batch to be safe, e.g. 500.
        java.util.Map<String, List<String>> result = new java.util.HashMap<>();
        int batchSize = 500;

        for (int i = 0; i < fileIds.size(); i += batchSize) {
            int end = Math.min(i + batchSize, fileIds.size());
            List<String> batch = fileIds.subList(i, end);

            StringBuilder sql = new StringBuilder("SELECT file_id, chunk_hash FROM file_chunks WHERE file_id IN (");
            for (int j = 0; j < batch.size(); j++) {
                sql.append(j == 0 ? "?" : ",?");
            }
            sql.append(") ORDER BY file_id, chunk_order ASC");

            try (PreparedStatement stmt = connection.prepareStatement(sql.toString())) {
                for (int j = 0; j < batch.size(); j++) {
                    stmt.setString(j + 1, batch.get(j));
                }

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String fileId = rs.getString("file_id");
                        String chunkHash = rs.getString("chunk_hash");
                        result.computeIfAbsent(fileId, k -> new ArrayList<>()).add(chunkHash);
                    }
                }
            }
        }
        return result;
    }

    public Optional<ChunkMetadata> getChunkMetadata(String hash) throws SQLException {
        String sql = "SELECT hash, size, first_seen, reference_count, last_accessed FROM chunks WHERE hash = ?";
        try (Connection conn = connectionManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, hash);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new ChunkMetadata(
                            rs.getString("hash"),
                            rs.getLong("size"),
                            Instant.ofEpochMilli(rs.getLong("first_seen")),
                            rs.getLong("reference_count"),
                            Instant.ofEpochMilli(rs.getLong("last_accessed"))));
                }
                return Optional.empty();
            }
        }
    }

    public void upsertChunk(ChunkMetadata chunk) throws SQLException {
        String sql = "INSERT OR REPLACE INTO chunks (hash, size, first_seen, reference_count, last_accessed) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = connectionManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, chunk.getHash());
            stmt.setLong(2, chunk.getSize());
            stmt.setLong(3, chunk.getFirstSeen().toEpochMilli());
            stmt.setLong(4, chunk.getReferenceCount());
            stmt.setLong(5, chunk.getLastAccessed().toEpochMilli());
            stmt.executeUpdate();
        }
    }

    public boolean deleteChunk(String hash) throws SQLException {
        String sql = "DELETE FROM chunks WHERE hash = ?";
        try (Connection conn = connectionManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, hash);
            return stmt.executeUpdate() > 0;
        }
    }

    public void recordChunkAccess(String hash) throws SQLException {
        String sql = "UPDATE chunks SET last_accessed = ? WHERE hash = ?";
        try (Connection conn = connectionManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, System.currentTimeMillis());
            stmt.setString(2, hash);
            stmt.executeUpdate();
        }
    }

    public java.util.stream.Stream<ChunkMetadata> streamAllChunks() throws java.io.IOException, SQLException {
        Connection connection = connectionManager.getConnection();
        java.sql.Statement stmt = connection.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT hash, size, first_seen, reference_count, last_accessed FROM chunks");

        java.util.Iterator<ChunkMetadata> iterator = new java.util.Iterator<>() {
            boolean hasNext = rs.next();

            @Override
            public boolean hasNext() {
                return hasNext;
            }

            @Override
            public ChunkMetadata next() {
                if (!hasNext)
                    throw new java.util.NoSuchElementException();
                try {
                    ChunkMetadata m = new ChunkMetadata(
                            rs.getString("hash"),
                            rs.getLong("size"),
                            Instant.ofEpochMilli(rs.getLong("first_seen")),
                            rs.getLong("reference_count"),
                            Instant.ofEpochMilli(rs.getLong("last_accessed")));
                    hasNext = rs.next();
                    return m;
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }
        };

        return java.util.stream.StreamSupport.stream(
                java.util.Spliterators.spliteratorUnknownSize(iterator,
                        java.util.Spliterator.ORDERED | java.util.Spliterator.NONNULL),
                false)
                .onClose(() -> {
                    try {
                        rs.close();
                        stmt.close();
                        connection.close();
                    } catch (SQLException e) {
                        logger.error("Failed to close stream resources", e);
                    }
                });
    }
}
