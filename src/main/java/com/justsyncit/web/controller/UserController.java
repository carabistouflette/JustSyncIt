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

import com.justsyncit.web.dto.UserRequests.*;
import com.justsyncit.web.dto.UserRequests;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayList;
import java.util.Base64;
import java.security.SecureRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.justsyncit.network.encryption.Argon2idKeyDerivationService;
import com.justsyncit.network.encryption.EncryptionException;

/**
 * REST controller for user management and authentication.
 */
public final class UserController {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserController.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    // PBKDF2 constants
    // PBKDF2-SHA256
    private static final int ITERATIONS = 600000;
    private static final int KEY_LENGTH = 256;
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String ARGON2_PREFIX = "$ARGON2ID$";

    private final Argon2idKeyDerivationService argon2Service;

    private static final java.util.Set<String> ALLOWED_ROLES = java.util.Set.of("admin", "user", "viewer");

    private static final long SESSION_TTL_MS = 24 * 60 * 60 * 1000L;

    // In-memory user storage (now backed by JSON file)
    private final Map<String, User> users;
    private final Map<String, SessionInfo> sessions; // token -> SessionInfo with expiry

    private final Map<String, TicketInfo> wsTickets;

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
        this.wsTickets = new ConcurrentHashMap<>();
        this.objectMapper = new ObjectMapper();
        this.userDatabasePath = Paths.get("config", "users.json");
        this.argon2Service = new Argon2idKeyDerivationService();

        loadUsers();

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
            CreateUserRequest request;
            try {
                request = ctx.bodyAsClass(CreateUserRequest.class);
            } catch (Exception e) {
                ctx.status(400).json(ApiError.badRequest("Invalid JSON body", ctx.path()));
                return;
            }

            String username = request.username();
            String password = request.password();
            String displayName = request.displayName();
            String role = request.role() != null ? request.role() : "user";

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
            saveUsers();

            LOGGER.info("Created user: {}", username);

            ctx.status(201).json(new UserRequests.UserResponse(
                    user.getId(),
                    user.getUsername(),
                    user.getDisplayName(),
                    user.getRole()));

        } catch (Exception e) {
            LOGGER.error("Failed to create user: {}", e.getMessage(), e);
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

            UpdateUserRequest request = ctx.bodyAsClass(UpdateUserRequest.class);

            if (request.displayName() != null) {
                String displayName = request.displayName();
                if (displayName.length() > 255) {
                    ctx.status(400).json(ApiError.badRequest(
                            "displayName must be 255 characters or less", ctx.path()));
                    return;
                }
                user.setDisplayName(displayName);
            }
            if (request.role() != null) {
                String newRole = request.role();
                if (!ALLOWED_ROLES.contains(newRole)) {
                    ctx.status(400).json(ApiError.badRequest(
                            "Invalid role. Allowed roles: " + ALLOWED_ROLES, ctx.path()));
                    return;
                }
                user.setRole(newRole);
            }
            if (request.password() != null && !request.password().isEmpty()) {
                user.setPassword(request.password());
            }
            saveUsers();

            LOGGER.info("Updated user: {}", user.getUsername());

            ctx.json(new UserRequests.UserResponse(
                    user.getId(),
                    user.getUsername(),
                    user.getDisplayName(),
                    user.getRole()));

        } catch (

        Exception e) {
            LOGGER.error("Failed to update user: {}", e.getMessage(), e);
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
        saveUsers();

        LOGGER.info("Deleted user: {}", user.getUsername());
        ctx.json(Map.of("status", "deleted", "id", userId));
    }

    /**
     * POST /api/auth/login - User login.
     */
    @SuppressWarnings("unchecked")
    public void login(Context ctx) {
        try {
            LoginRequest request = ctx.bodyAsClass(LoginRequest.class);
            String username = request.username();
            String password = request.password();

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

            // Auto-migrate legacy PBKDF2 users to Argon2id
            migrateToArgon2(user, password);

            // Generate session token with timestamp for expiry
            String token = generateToken();
            sessions.put(token, new SessionInfo(user.getId(), System.currentTimeMillis()));

            LOGGER.info("User logged in: {}", username);

            // Set HttpOnly cookie for XSS protection
            boolean isSecure = ctx.scheme().equals("https");
            String cookieFlags = "; HttpOnly; SameSite=Strict; Path=/";
            if (isSecure) {
                cookieFlags += "; Secure";
            }
            ctx.header("Set-Cookie", "session=" + token + cookieFlags);

            ctx.json(Map.of(
                    "user", new UserRequests.UserResponse(
                            user.getId(),
                            user.getUsername(),
                            user.getDisplayName(),
                            user.getRole())));

        } catch (Exception e) {
            LOGGER.error("Login failed: {}", e.getMessage(), e);
            ctx.status(500).json(ApiError.internalError(e.getMessage(), ctx.path()));
        }
    }

    /**
     * POST /api/auth/logout - User logout.
     */
    public void logout(Context ctx) {
        String authHeader = ctx.header("Authorization");
        String token = null;

        // Try Bearer token first
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        }

        // Also check cookie
        if (token == null) {
            token = ctx.cookie("session");
        }

        if (token != null) {
            sessions.remove(token);
        }

        // Clear the session cookie
        ctx.header("Set-Cookie", "session=; HttpOnly; SameSite=Strict; Path=/; Max-Age=0");
        ctx.json(Map.of("status", "logged_out"));
    }

    // Helper methods

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

    private String hashPassword(String password, byte[] salt) {
        // Default to Argon2id for new hashes
        try {
            byte[] hash = argon2Service.deriveKey(password.toCharArray(), salt, 32);
            return ARGON2_PREFIX + Base64.getEncoder().encodeToString(hash);
        } catch (EncryptionException e) {
            throw new RuntimeException("Error hashing password with Argon2id", e);
        }
    }

    private static String hashPasswordPBKDF2(String password, byte[] salt) {
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

    private boolean verifyPassword(String password, String storedHash, String storedSalt) {
        byte[] salt = Base64.getDecoder().decode(storedSalt);

        if (storedHash.startsWith(ARGON2_PREFIX)) {
            // Argon2id verification
            String rawHash = storedHash.substring(ARGON2_PREFIX.length());
            try {
                byte[] calculatedHash = argon2Service.deriveKey(password.toCharArray(), salt, 32);
                String calculatedHashStr = Base64.getEncoder().encodeToString(calculatedHash);
                return java.security.MessageDigest.isEqual(
                        calculatedHashStr.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        rawHash.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            } catch (EncryptionException e) {
                LOGGER.error("Argon2 verify failed: {}", e.getMessage(), e);
                return false;
            }
        } else {
            // Legacy PBKDF2 verification - DEPRECATED, will migrate on success
            LOGGER.warn("PBKDF2 legacy hash detected - will migrate to Argon2id on successful auth");
            String newHash = hashPasswordPBKDF2(password, salt);
            return java.security.MessageDigest.isEqual(
                    newHash.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    storedHash.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    /**
     * Migrates a user from PBKDF2 to Argon2id hash.
     * Called after successful login with legacy hash.
     */
    private void migrateToArgon2(User user, String plainPassword) {
        if (!user.getPasswordHash().startsWith(ARGON2_PREFIX)) {
            LOGGER.info("Migrating user {} from PBKDF2 to Argon2id", user.getUsername());
            user.setPassword(plainPassword); // Re-hashes with Argon2id
            saveUsers();
            LOGGER.info("User {} migrated to Argon2id successfully", user.getUsername());
        }
    }

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
            LOGGER.warn("WebSocket ticket expired");
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

    /**
     * Cleans up expired sessions and tickets.
     */
    public void cleanup() {
        long now = System.currentTimeMillis();
        sessions.entrySet().removeIf(entry -> entry.getValue().isExpired());
        wsTickets.entrySet().removeIf(entry -> now - entry.getValue().createdAt > WS_TICKET_EXPIRY_MS);
    }

    private void loadUsers() {
        try {
            if (Files.exists(userDatabasePath)) {
                List<User> loaded = objectMapper.readValue(userDatabasePath.toFile(), new TypeReference<List<User>>() {
                });
                for (User u : loaded) {
                    users.put(u.getId(), u);
                }
                LOGGER.info("Loaded {} users from disk.", users.size());
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load users: {}", e.getMessage(), e);
        }
    }

    private synchronized void saveUsers() {
        try {
            Files.createDirectories(userDatabasePath.getParent());
            objectMapper.writeValue(userDatabasePath.toFile(), new ArrayList<>(users.values()));
        } catch (Exception e) {
            LOGGER.error("Failed to save users: {}", e.getMessage(), e);
        }
    }

    private void createDefaultAdmin() {
        String tempPass = java.util.UUID.randomUUID().toString();
        User admin = new User(generateId(), "admin", "Administrator", "admin");
        admin.setPassword(tempPass);
        users.put(admin.getId(), admin);
        saveUsers();

        // [SEC-001] Security Fix: Log password to console ONLY, do NOT write to disk.
        // This prevents credential leakage in the filesystem.
        LOGGER.warn("\n==================================================\n" +
                "  [SECURITY] Default Admin Account Created\n" +
                "  Username: admin\n" +
                "  Password: {}\n" +
                "  Please change this password immediately on login.\n" +
                "==================================================", tempPass);
    }

    // User class

    // User class - Made public for Jackson support
    public static class User {
        private static final Argon2idKeyDerivationService SHARED_ARGON2_SERVICE = new Argon2idKeyDerivationService();

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
            // [SEC-002] Use shared service instance instead of creating new one
            byte[] saltBytes = SHARED_ARGON2_SERVICE.generateSalt();
            this.salt = Base64.getEncoder().encodeToString(saltBytes);
            try {
                byte[] hash = SHARED_ARGON2_SERVICE.deriveKey(password.toCharArray(), saltBytes, 32);
                this.passwordHash = ARGON2_PREFIX + Base64.getEncoder().encodeToString(hash);
            } catch (EncryptionException e) {
                throw new RuntimeException(e);
            }
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
