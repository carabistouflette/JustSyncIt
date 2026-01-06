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

package com.justsyncit.web.controller;

import com.justsyncit.web.WebServerContext;
import com.justsyncit.web.dto.ApiError;

import io.javalin.http.Context;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
// [Omega Remediation] Cleaned up unused imports
import java.util.logging.Logger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

/**
 * REST controller for user management and authentication.
 */
public final class UserController {

    private static final Logger LOGGER = Logger.getLogger(UserController.class.getName());
    private static final SecureRandom RANDOM = new SecureRandom();

    // PBKDF2 constants
    // [Omega Remediation] SEC-001: OWASP 2024 recommends 600k iterations for
    // PBKDF2-SHA256
    private static final int ITERATIONS = 600000;
    private static final int KEY_LENGTH = 256;
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";

    // [Omega Remediation] SEC-003: Allowed roles whitelist
    private static final java.util.Set<String> ALLOWED_ROLES = java.util.Set.of("admin", "user", "viewer");

    // [Omega Remediation v2] SEC-C03: Session expiry (24 hours)
    private static final long SESSION_TTL_MS = 24 * 60 * 60 * 1000L;

    // In-memory user storage (now backed by JSON file)
    private final Map<String, User> users;
    private final Map<String, SessionInfo> sessions; // token -> SessionInfo with expiry

    // [Omega Remediation] SEC-002: Short-lived WebSocket tickets (30 second expiry)
    private final Map<String, TicketInfo> wsTickets;

    // [Omega Remediation v2] SEC-C03: Session with timestamp for expiry
    private static class SessionInfo {
        final String userId;
        final long createdAt;

        SessionInfo(String userId, long createdAt) {
            this.userId = userId;
            this.createdAt = createdAt;
        }

        boolean isExpired() {
            return System.currentTimeMillis() - createdAt > SESSION_TTL_MS;
        }
    }

    private static class TicketInfo {
        final String userId;
        final long createdAt;

        TicketInfo(String userId, long createdAt) {
            this.userId = userId;
            this.createdAt = createdAt;
        }
    }

    private final ObjectMapper objectMapper;
    private final Path userDatabasePath;

    public UserController(WebServerContext context) {
        // Context kept for API compatibility
        this.users = new ConcurrentHashMap<>();
        this.sessions = new ConcurrentHashMap<>();
        this.wsTickets = new ConcurrentHashMap<>(); // [Omega Remediation] SEC-002
        this.objectMapper = new ObjectMapper();
        this.userDatabasePath = Paths.get("config", "users.json");

        loadUsers();

        // [Omega Remediation] P0 Security
        // If no users exist, create a safe default admin with a RANDOM password.
        if (users.isEmpty()) {
            createDefaultAdmin();
        }
    }

    /**
     * GET /api/users - List all users.
     */
    public void listUsers(Context ctx) {
        List<Map<String, Object>> userList = new ArrayList<>();
        for (User user : users.values()) {
            userList.add(Map.of(
                    "id", user.getId(),
                    "username", user.getUsername(),
                    "displayName", user.getDisplayName(),
                    "role", user.getRole()));
        }
        ctx.json(Map.of("users", userList));
    }

    /**
     * POST /api/users - Create a new user.
     */
    public void createUser(Context ctx) {
        try {
            Map<String, String> body;
            try {
                @SuppressWarnings("unchecked")
                Map<String, String> parsed = ctx.bodyAsClass(Map.class);
                body = parsed;
            } catch (Exception e) {
                ctx.status(400).json(ApiError.badRequest("Invalid JSON body", ctx.path()));
                return;
            }

            String username = body.get("username");
            String password = body.get("password");
            String displayName = body.get("displayName");
            String role = body.getOrDefault("role", "user");

            // Validate username
            if (username == null || username.isEmpty()) {
                ctx.status(400).json(ApiError.badRequest("username is required", ctx.path()));
                return;
            }
            if (username.length() > 255) {
                ctx.status(400).json(ApiError.badRequest("username must be 255 characters or less", ctx.path()));
                return;
            }
            if (!username.matches("^[a-zA-Z0-9_.-]+$")) {
                ctx.status(400)
                        .json(ApiError.badRequest(
                                "username must contain only alphanumeric characters, underscores, dots, and hyphens",
                                ctx.path()));
                return;
            }

            // Validate password
            if (password == null || password.isEmpty()) {
                ctx.status(400).json(ApiError.badRequest("password is required", ctx.path()));
                return;
            }
            if (password.length() < 8) {
                ctx.status(400).json(ApiError.badRequest("password must be at least 8 characters", ctx.path()));
                return;
            }

            // Validate role (whitelist)
            if (!Set.of("admin", "user", "viewer").contains(role)) {
                ctx.status(400).json(ApiError.badRequest("role must be one of: admin, user, viewer", ctx.path()));
                return;
            }

            // Check for duplicate username
            boolean exists = users.values().stream()
                    .anyMatch(u -> u.getUsername().equals(username));
            if (exists) {
                ctx.status(409).json(ApiError.of(409, "Conflict",
                        "User already exists: " + username, ctx.path()));
                return;
            }

            User user = new User(generateId(), username,
                    displayName != null ? displayName : username, role);
            user.setPassword(password);
            users.put(user.getId(), user);
            saveUsers(); // [Omega Remediation] Persist changes

            LOGGER.info("Created user: " + username);

            ctx.status(201).json(Map.of(
                    "id", user.getId(),
                    "username", user.getUsername(),
                    "displayName", user.getDisplayName(),
                    "role", user.getRole()));

        } catch (Exception e) {
            LOGGER.severe("Failed to create user: " + e.getMessage());
            ctx.status(500).json(ApiError.internalError(e.getMessage(), ctx.path()));
        }
    }

    /**
     * PUT /api/users/{id} - Update a user.
     */
    @SuppressWarnings("unchecked")
    public void updateUser(Context ctx) {
        try {
            String userId = ctx.pathParam("id");
            User user = users.get(userId);

            if (user == null) {
                ctx.status(404).json(ApiError.notFound("User not found: " + userId, ctx.path()));
                return;
            }

            Map<String, String> body = ctx.bodyAsClass(Map.class);

            if (body.containsKey("displayName")) {
                String displayName = body.get("displayName");
                // [Omega Remediation v2] SEC-H03: Validate displayName length
                if (displayName != null && displayName.length() > 255) {
                    ctx.status(400).json(ApiError.badRequest(
                            "displayName must be 255 characters or less", ctx.path()));
                    return;
                }
                user.setDisplayName(displayName);
            }
            if (body.containsKey("role")) {
                String newRole = body.get("role");
                // [Omega Remediation] SEC-003: Validate role against whitelist
                if (!ALLOWED_ROLES.contains(newRole)) {
                    ctx.status(400).json(ApiError.badRequest(
                            "Invalid role. Allowed roles: " + ALLOWED_ROLES, ctx.path()));
                    return;
                }
                user.setRole(newRole);
            }
            if (body.containsKey("password") && !body.get("password").isEmpty()) {
                user.setPassword(body.get("password"));
            }
            saveUsers(); // [Omega Remediation] Persist changes

            LOGGER.info("Updated user: " + user.getUsername());

            ctx.json(Map.of(
                    "id", user.getId(),
                    "username", user.getUsername(),
                    "displayName", user.getDisplayName(),
                    "role", user.getRole()));

        } catch (Exception e) {
            LOGGER.severe("Failed to update user: " + e.getMessage());
            ctx.status(500).json(ApiError.internalError(e.getMessage(), ctx.path()));
        }
    }

    /**
     * DELETE /api/users/{id} - Delete a user.
     */
    public void deleteUser(Context ctx) {
        String userId = ctx.pathParam("id");
        User user = users.remove(userId);

        if (user == null) {
            ctx.status(404).json(ApiError.notFound("User not found: " + userId, ctx.path()));
            return;
        }

        // Remove any sessions for this user
        sessions.entrySet().removeIf(e -> e.getValue().equals(userId));
        saveUsers(); // [Omega Remediation] Persist changes

        LOGGER.info("Deleted user: " + user.getUsername());
        ctx.json(Map.of("status", "deleted", "id", userId));
    }

    /**
     * POST /api/auth/login - User login.
     */
    @SuppressWarnings("unchecked")
    public void login(Context ctx) {
        try {
            Map<String, String> body = ctx.bodyAsClass(Map.class);
            String username = body.get("username");
            String password = body.get("password");

            if (username == null || password == null) {
                ctx.status(400).json(ApiError.badRequest("username and password are required", ctx.path()));
                return;
            }

            // Find user by username
            User user = users.values().stream()
                    .filter(u -> u.getUsername().equals(username))
                    .findFirst()
                    .orElse(null);

            if (user == null || !verifyPassword(password, user.getPasswordHash(), user.getSalt())) {
                ctx.status(401).json(ApiError.of(401, "Unauthorized",
                        "Invalid username or password", ctx.path()));
                return;
            }

            // Generate session token with timestamp for expiry
            String token = generateToken();
            sessions.put(token, new SessionInfo(user.getId(), System.currentTimeMillis()));

            LOGGER.info("User logged in: " + username);

            ctx.json(Map.of(
                    "token", token,
                    "user", Map.of(
                            "id", user.getId(),
                            "username", user.getUsername(),
                            "displayName", user.getDisplayName(),
                            "role", user.getRole())));

        } catch (Exception e) {
            LOGGER.severe("Login failed: " + e.getMessage());
            ctx.status(500).json(ApiError.internalError(e.getMessage(), ctx.path()));
        }
    }

    /**
     * POST /api/auth/logout - User logout.
     */
    public void logout(Context ctx) {
        String authHeader = ctx.header("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            sessions.remove(token);
        }
        ctx.json(Map.of("status", "logged_out"));
    }

    // Helper methods

    // [Omega Remediation v2] SEC-H02: Use 16-byte IDs to avoid collision risk
    private String generateId() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hashPassword(String password, byte[] salt) {
        try {
            javax.crypto.spec.PBEKeySpec spec = new javax.crypto.spec.PBEKeySpec(
                    password.toCharArray(), salt, ITERATIONS, KEY_LENGTH);
            javax.crypto.SecretKeyFactory skf = javax.crypto.SecretKeyFactory.getInstance(ALGORITHM);
            byte[] hash = skf.generateSecret(spec).getEncoded();
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Error hashing password", e);
        }
    }

    // [Omega Remediation] SEC-101: Constant-time comparison to prevent timing
    // attacks
    private static boolean verifyPassword(String password, String storedHash, String storedSalt) {
        byte[] salt = Base64.getDecoder().decode(storedSalt);
        String newHash = hashPassword(password, salt);
        // Use constant-time comparison to prevent timing attacks
        return java.security.MessageDigest.isEqual(
                newHash.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                storedHash.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    // [Omega Remediation v2] SEC-C03: Check session expiry
    public boolean isValidSession(String token) {
        SessionInfo session = sessions.get(token);
        if (session == null) {
            return false;
        }
        if (session.isExpired()) {
            sessions.remove(token); // Cleanup expired session
            return false;
        }
        return true;
    }

    // [Omega Remediation] SEC-012: Role-based access control helper methods
    public String getUserIdForSession(String token) {
        SessionInfo session = sessions.get(token);
        if (session == null || session.isExpired()) {
            return null;
        }
        return session.userId;
    }

    public String getUserRole(String userId) {
        if (userId == null)
            return null;
        User user = users.get(userId);
        return user != null ? user.getRole() : null;
    }

    // [Omega Remediation] SEC-002: WebSocket ticket-based authentication
    // Tickets are short-lived (30 seconds) and can only be used once
    private static final long WS_TICKET_EXPIRY_MS = 30_000;

    /**
     * Creates a short-lived ticket for WebSocket authentication.
     * Client should call this, then immediately connect to WebSocket with the
     * ticket.
     * 
     * @param sessionToken the user's session token
     * @return the one-time WebSocket ticket, or null if session is invalid
     */
    public String createWsTicket(String sessionToken) {
        SessionInfo session = sessions.get(sessionToken);
        if (session == null || session.isExpired()) {
            return null;
        }
        String ticket = generateToken();
        wsTickets.put(ticket, new TicketInfo(session.userId, System.currentTimeMillis()));
        return ticket;
    }

    /**
     * Validates and consumes a WebSocket ticket (one-time use).
     * 
     * @param ticket the one-time ticket
     * @return the userId if valid, null otherwise
     */
    public String validateAndConsumeTicket(String ticket) {
        if (ticket == null) {
            return null;
        }
        TicketInfo info = wsTickets.remove(ticket);
        if (info == null) {
            return null;
        }
        // Check if ticket has expired
        if (System.currentTimeMillis() - info.createdAt > WS_TICKET_EXPIRY_MS) {
            LOGGER.warning("WebSocket ticket expired");
            return null;
        }
        return info.userId;
    }

    /**
     * Validates a token for WebSocket authentication.
     * 
     * @param token the session token to validate
     * @return true if the token is valid, false otherwise
     */
    public boolean validateToken(String token) {
        return isValidSession(token);
    }

    // [Omega Remediation] Persistence Methods

    private void loadUsers() {
        try {
            if (Files.exists(userDatabasePath)) {
                List<User> loaded = objectMapper.readValue(userDatabasePath.toFile(), new TypeReference<List<User>>() {
                });
                for (User u : loaded) {
                    users.put(u.getId(), u);
                }
                LOGGER.info("Loaded " + users.size() + " users from disk.");
            }
        } catch (Exception e) {
            LOGGER.severe("Failed to load users: " + e.getMessage());
        }
    }

    private synchronized void saveUsers() {
        try {
            Files.createDirectories(userDatabasePath.getParent());
            objectMapper.writeValue(userDatabasePath.toFile(), new ArrayList<>(users.values()));
        } catch (Exception e) {
            LOGGER.severe("Failed to save users: " + e.getMessage());
        }
    }

    // [Omega Remediation] SEC-102: Write password to secure file instead of logging
    private void createDefaultAdmin() {
        String tempPass = java.util.UUID.randomUUID().toString().substring(0, 8);
        User admin = new User(generateId(), "admin", "Administrator", "admin");
        admin.setPassword(tempPass);
        users.put(admin.getId(), admin);
        saveUsers();

        // Write password to secure file instead of logging to console
        try {
            java.nio.file.Path passwordFile = java.nio.file.Paths.get("config", ".admin-password");
            java.nio.file.Files.createDirectories(passwordFile.getParent());
            java.nio.file.Files.writeString(passwordFile, tempPass,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
            // Set restrictive permissions (owner read/write only)
            passwordFile.toFile().setReadable(false, false);
            passwordFile.toFile().setReadable(true, true);
            passwordFile.toFile().setWritable(false, false);
            passwordFile.toFile().setWritable(true, true);

            LOGGER.warning("\n==================================================\n" +
                    "  [SECURITY] Admin password written to: config/.admin-password\n" +
                    "  Please read the file and change this password immediately.\n" +
                    "==================================================");
        } catch (java.io.IOException e) {
            // [Omega Remediation v2] SEC-C04: Never log passwords or hints
            LOGGER.severe("CRITICAL: Failed to write admin password file. " +
                    "Application cannot start securely. Error: " + e.getMessage());
            throw new RuntimeException("Failed to create admin password file. " +
                    "Ensure config directory is writable.", e);
        }
    }

    // User class

    // User class - Made public for Jackson support
    public static class User {
        private String id;
        private String username;
        private String displayName;
        private String role;
        private String passwordHash;
        private String salt;

        // Default constructor for Jackson
        public User() {
        }

        public User(String id, String username, String displayName, String role) {
            this.id = id;
            this.username = username;
            this.displayName = displayName;
            this.role = role;
        }

        public void setPassword(String password) {
            byte[] saltBytes = new byte[16];
            RANDOM.nextBytes(saltBytes);
            this.salt = Base64.getEncoder().encodeToString(saltBytes);
            this.passwordHash = UserController.hashPassword(password, saltBytes);
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getPasswordHash() {
            return passwordHash;
        }

        public void setPasswordHash(String passwordHash) {
            this.passwordHash = passwordHash;
        }

        public String getSalt() {
            return salt;
        }

        public void setSalt(String salt) {
            this.salt = salt;
        }
    }
}
