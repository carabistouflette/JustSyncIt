package com.justsyncit.storage.metadata;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.justsyncit.storage.snapshot.MerkleNode;
import com.justsyncit.storage.snapshot.MerkleNode.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class MerkleRepository {

    private static final Logger logger = LoggerFactory.getLogger(MerkleRepository.class);
    private final DatabaseConnectionManager connectionManager;
    private final ObjectMapper objectMapper;

    public MerkleRepository(DatabaseConnectionManager connectionManager, ObjectMapper objectMapper) {
        if (connectionManager == null) {
            throw new IllegalArgumentException("Connection manager cannot be null");
        }
        if (objectMapper == null) {
            throw new IllegalArgumentException("ObjectMapper cannot be null");
        }
        this.connectionManager = connectionManager;
        this.objectMapper = objectMapper;
    }

    private static class StoredMerkleChild {
        public String hash;
        public String type;
        public String name;
        public long size;
        public String fileId;

        // Default constructor for Jackson
        public StoredMerkleChild() {
        }

        public StoredMerkleChild(MerkleNode node) {
            this.hash = node.getHash();
            this.type = node.getType().name();
            this.name = node.getName();
            this.size = node.getSize();
            this.fileId = node.getFileId();
        }
    }

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

    public Optional<MerkleNode> getMerkleNode(String hash) throws IOException {
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

    private String compress(String output) throws IOException {
        if (output == null)
            return null;
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
                GZIPOutputStream gzip = new GZIPOutputStream(bos)) {
            gzip.write(output.getBytes(StandardCharsets.UTF_8));
            gzip.finish();
            return java.util.Base64.getEncoder().encodeToString(bos.toByteArray());
        }
    }

    private String decompress(String compressed) throws IOException {
        if (compressed == null)
            return null;
        byte[] bytes = java.util.Base64.getDecoder().decode(compressed);
        try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
                GZIPInputStream gzip = new GZIPInputStream(bis)) {
            return new String(gzip.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
