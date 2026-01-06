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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Snapshot CRUD operations.
 */
public final class SnapshotRepository {

    private static final Logger logger = LoggerFactory.getLogger(SnapshotRepository.class);

    private final DatabaseConnectionManager connectionManager;

    /**
     * Creates a new SnapshotRepository.
     *
     * @param connectionManager database connection manager
     */
    public SnapshotRepository(DatabaseConnectionManager connectionManager) {
        if (connectionManager == null) {
            throw new IllegalArgumentException("Connection manager cannot be null");
        }
        this.connectionManager = connectionManager;
    }

    /**
     * Creates a new snapshot.
     *
     * @param name        snapshot name (used as ID)
     * @param description snapshot description
     * @return created snapshot
     * @throws IOException if database operation fails
     */
    public Snapshot createSnapshot(String name, String description) throws IOException {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Snapshot name cannot be null or empty");
        }

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

            int rows = stmt.executeUpdate();
            if (rows == 0) {
                throw new IOException("Failed to create snapshot, no rows affected.");
            }

            Snapshot snapshot = new Snapshot(id, name, description, now, 0, 0);
            logger.debug("Created snapshot: {}", snapshot);
            return snapshot;

        } catch (SQLException e) {
            throw new IOException("Failed to create snapshot", e);
        }
    }

    /**
     * Updates snapshot statistics.
     *
     * @param snapshot snapshot to update
     * @throws IOException if database operation fails
     */
    public void updateSnapshot(Snapshot snapshot) throws IOException {
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

    /**
     * Gets a snapshot by ID.
     *
     * @param id snapshot ID
     * @return snapshot if found
     * @throws IOException if database operation fails
     */
    public Optional<Snapshot> getSnapshot(String id) throws IOException {
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

    /**
     * Lists all snapshots ordered by creation time (newest first).
     *
     * @return list of snapshots
     * @throws IOException if database operation fails
     */
    public List<Snapshot> listSnapshots() throws IOException {
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

    /**
     * Deletes a snapshot.
     *
     * @param id snapshot ID
     * @throws IOException if database operation fails
     */
    public void deleteSnapshot(String id) throws IOException {
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

    /**
     * Sets the merkle root hash for a snapshot.
     *
     * @param snapshotId snapshot ID
     * @param rootHash   merkle root hash
     * @throws IOException if database operation fails
     */
    public void setSnapshotRoot(String snapshotId, String rootHash) throws IOException {
        if (snapshotId == null || snapshotId.trim().isEmpty()) {
            throw new IllegalArgumentException("Snapshot ID cannot be null or empty");
        }

        String sql = "UPDATE snapshots SET merkle_root = ? WHERE id = ?";

        try (Connection connection = connectionManager.getConnection();
                PreparedStatement stmt = connection.prepareStatement(sql)) {

            stmt.setString(1, rootHash);
            stmt.setString(2, snapshotId);
            stmt.executeUpdate();
            logger.debug("Set merkle root for snapshot {}: {}", snapshotId, rootHash);

        } catch (SQLException e) {
            throw new IOException("Failed to set snapshot root", e);
        }
    }

    /**
     * Gets the merkle root hash for a snapshot.
     *
     * @param snapshotId snapshot ID
     * @return merkle root hash if set
     * @throws IOException if database operation fails
     */
    public Optional<String> getSnapshotRoot(String snapshotId) throws IOException {
        if (snapshotId == null || snapshotId.trim().isEmpty()) {
            throw new IllegalArgumentException("Snapshot ID cannot be null or empty");
        }

        String sql = "SELECT merkle_root FROM snapshots WHERE id = ?";

        try (Connection connection = connectionManager.getConnection();
                PreparedStatement stmt = connection.prepareStatement(sql)) {

            stmt.setString(1, snapshotId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.ofNullable(rs.getString("merkle_root"));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new IOException("Failed to get snapshot root", e);
        }
    }

    /**
     * Gets the parent snapshot ID for a given snapshot.
     *
     * @param snapshotId snapshot ID
     * @return parent snapshot ID or null if not found/no parent
     * @throws IOException if database operation fails
     */
    public String getParentSnapshotId(String snapshotId) throws IOException {
        String sql = "SELECT parent_id FROM snapshots WHERE id = ?";
        try (Connection conn = connectionManager.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, snapshotId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("parent_id");
                }
            }
        } catch (SQLException e) {
            throw new IOException("Failed to get parent snapshot ID", e);
        }
        return null;
    }

    /**
     * Maps a database row to a Snapshot object.
     */
    private Snapshot mapRowToSnapshot(ResultSet rs) throws SQLException {
        return new Snapshot(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("description"),
                Instant.ofEpochMilli(rs.getLong("created_at")),
                rs.getLong("total_files"),
                rs.getLong("total_size"));
    }
}
