package com.justsyncit.web.service;

import com.justsyncit.storage.metadata.DatabaseConnectionManager;
import com.justsyncit.web.model.User;
import com.justsyncit.web.service.AuthService.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * SQLite-backed store for Authentication data (Users and Sessions).
 * Replaces the legacy JSON persistence.
 */
public class SqliteAuthStore {
    private static final Logger LOGGER = LoggerFactory.getLogger(SqliteAuthStore.class);
    private final DatabaseConnectionManager connectionManager;

    public SqliteAuthStore(DatabaseConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
        initializeSchema();
    }

    private void initializeSchema() {
        try (Connection conn = connectionManager.getConnection();
                Statement stmt = conn.createStatement()) {

            for (String sql : AuthSchema.INIT_STATEMENTS) {
                stmt.execute(sql);
            }
            LOGGER.info("AuthStore schema initialized.");

        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize AuthStore schema", e);
        }
    }

    // --- User Operations ---

    public void createUser(User user) throws SQLException {
        String sql = "INSERT INTO users (id, username, display_name, role, password_hash, salt) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = connectionManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, user.getId());
            stmt.setString(2, user.getUsername());
            stmt.setString(3, user.getDisplayName());
            stmt.setString(4, user.getRole());
            stmt.setString(5, user.getPasswordHash());
            stmt.setString(6, user.getSalt()); // Might be null for legacy/plain, but schema allows null?

            stmt.executeUpdate();
        }
    }

    public void updateUser(User user) throws SQLException {
        String sql = "UPDATE users SET display_name = ?, role = ?, password_hash = ?, salt = ? WHERE id = ?";
        try (Connection conn = connectionManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, user.getDisplayName());
            stmt.setString(2, user.getRole());
            stmt.setString(3, user.getPasswordHash());
            stmt.setString(4, user.getSalt());
            stmt.setString(5, user.getId());

            int rows = stmt.executeUpdate();
            if (rows == 0) {
                LOGGER.warn("Attempted to update non-existent user: {}", user.getId());
            }
        }
    }

    public void deleteUser(String userId) throws SQLException {
        String sql = "DELETE FROM users WHERE id = ?";
        try (Connection conn = connectionManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userId);
            stmt.executeUpdate();
        }
    }

    public Optional<User> getUserById(String id) throws SQLException {
        String sql = "SELECT * FROM users WHERE id = ?";
        try (Connection conn = connectionManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowToUser(rs));
                }
            }
        }
        return Optional.empty();
    }

    public Optional<User> getUserByUsername(String username) throws SQLException {
        String sql = "SELECT * FROM users WHERE username = ?";
        try (Connection conn = connectionManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowToUser(rs));
                }
            }
        }
        return Optional.empty();
    }

    public List<User> listUsers() throws SQLException {
        String sql = "SELECT * FROM users";
        List<User> users = new ArrayList<>();
        try (Connection conn = connectionManager.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                users.add(mapRowToUser(rs));
            }
        }
        return users;
    }

    private User mapRowToUser(ResultSet rs) throws SQLException {
        User user = new User(
                rs.getString("id"),
                rs.getString("username"),
                rs.getString("display_name"),
                rs.getString("role"));
        user.setPasswordHash(rs.getString("password_hash"));
        user.setSalt(rs.getString("salt"));
        return user;
    }

    // --- Session Operations ---

    public void createSession(Session session, String token) throws SQLException {
        String sql = "INSERT INTO sessions (token, user_id, username, role, expiry_time) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = connectionManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, token);
            stmt.setString(2, session.userId());
            stmt.setString(3, session.username());
            stmt.setString(4, session.role());
            stmt.setLong(5, session.expiryTime());
            stmt.executeUpdate();
        }
    }

    public Optional<Session> getSession(String token) throws SQLException {
        String sql = "SELECT * FROM sessions WHERE token = ?";
        try (Connection conn = connectionManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, token);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new Session(
                            rs.getString("user_id"),
                            rs.getString("username"),
                            rs.getString("role"),
                            rs.getLong("expiry_time")));
                }
            }
        }
        return Optional.empty();
    }

    public void deleteSession(String token) throws SQLException {
        String sql = "DELETE FROM sessions WHERE token = ?";
        try (Connection conn = connectionManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, token);
            stmt.executeUpdate();
        }
    }

    public void cleanupExpiredSessions(long currentTime) throws SQLException {
        String sql = "DELETE FROM sessions WHERE expiry_time < ?";
        try (Connection conn = connectionManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, currentTime);
            int deleted = stmt.executeUpdate();
            if (deleted > 0) {
                LOGGER.info("Cleaned up {} expired sessions", deleted);
            }
        }
    }
}
