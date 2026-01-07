package com.justsyncit.web.controller;

import com.justsyncit.web.model.User;
import com.justsyncit.web.service.AuthService;
import com.justsyncit.web.service.SqliteAuthStore;
import com.justsyncit.web.dto.ApiError;
import io.javalin.http.Context;

import com.justsyncit.web.dto.UserRequests.*;
import com.justsyncit.web.dto.UserRequests;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Optional;
import java.security.SecureRandom;
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

    private final AuthService authService;
    private final SqliteAuthStore authStore;

    // sessions are now in DB via authService/authStore
    private final Map<String, TicketInfo> wsTickets;

    private static class TicketInfo {
        final String userId;
        final long createdAt;

        TicketInfo(String userId, long createdAt) {
            this.userId = userId;
            this.createdAt = createdAt;
        }
    }

    public UserController(SqliteAuthStore authStore, AuthService authService) {
        this.authStore = authStore;
        this.authService = authService;
        this.wsTickets = new ConcurrentHashMap<>();
        this.argon2Service = new Argon2idKeyDerivationService();

        // Check/Create Default Admin
        try {
            if (authStore.listUsers().isEmpty()) {
                createDefaultAdmin();
            }
        } catch (Exception e) {
            LOGGER.error("Failed to check or create default admin", e);
        }
    }

    /**
     * GET /api/users - List all users.
     */
    public void listUsers(Context ctx) {
        try {
            List<User> users = authStore.listUsers();
            List<Map<String, Object>> userList = new ArrayList<>();
            for (User user : users) {
                userList.add(Map.of(
                        "id", user.getId(),
                        "username", user.getUsername(),
                        "displayName", user.getDisplayName(),
                        "role", user.getRole()));
            }
            ctx.json(Map.of("users", userList));
        } catch (Exception e) {
            LOGGER.error("Failed to list users", e);
            ctx.status(500).json(ApiError.internalError("Failed to list users", ctx.path()));
        }
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
            if (authStore.getUserByUsername(username).isPresent()) {
                ctx.status(409).json(ApiError.of(409, "Conflict",
                        "User already exists: " + username, ctx.path()));
                return;
            }

            User user = new User(generateId(), username,
                    displayName != null ? displayName : username, role);
            user.setPassword(password);

            authStore.createUser(user);

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
    public void updateUser(Context ctx) {
        try {
            String userId = ctx.pathParam("id");
            Optional<User> userOpt = authStore.getUserById(userId);

            if (userOpt.isEmpty()) {
                ctx.status(404).json(ApiError.notFound("User not found: " + userId, ctx.path()));
                return;
            }
            User user = userOpt.get();

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

            authStore.updateUser(user);

            LOGGER.info("Updated user: {}", user.getUsername());

            ctx.json(new UserRequests.UserResponse(
                    user.getId(),
                    user.getUsername(),
                    user.getDisplayName(),
                    user.getRole()));

        } catch (Exception e) {
            LOGGER.error("Failed to update user: {}", e.getMessage(), e);
            ctx.status(500).json(ApiError.internalError(e.getMessage(), ctx.path()));
        }
    }

    /**
     * DELETE /api/users/{id} - Delete a user.
     */
    public void deleteUser(Context ctx) {
        String userId = ctx.pathParam("id");
        try {
            Optional<User> userOpt = authStore.getUserById(userId);
            if (userOpt.isEmpty()) {
                ctx.status(404).json(ApiError.notFound("User not found: " + userId, ctx.path()));
                return;
            }
            User user = userOpt.get();

            authStore.deleteUser(userId);

            LOGGER.info("Deleted user: {}", user.getUsername());
            ctx.json(Map.of("status", "deleted", "id", userId));
        } catch (Exception e) {
            LOGGER.error("Failed to delete user", e);
            ctx.status(500).json(ApiError.internalError("Failed to delete user", ctx.path()));
        }
    }

    /**
     * POST /api/auth/login - User login.
     */
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
            Optional<User> userOpt = authStore.getUserByUsername(username);

            // Defensive check + Verify
            User user = userOpt.orElse(null);

            if (user == null || !verifyPassword(password, user.getPasswordHash(), user.getSalt())) {
                ctx.status(401).json(ApiError.of(401, "Unauthorized",
                        "Invalid username or password", ctx.path()));
                return;
            }

            // Auto-migrate legacy PBKDF2 users to Argon2id
            migrateToArgon2(user, password);

            // Generate persistent session token
            String token = authService.createSession(user);

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
            authService.invalidateSession(token);
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
            try {
                authStore.updateUser(user);
                LOGGER.info("User {} migrated to Argon2id successfully", user.getUsername());
            } catch (Exception e) {
                LOGGER.error("Failed to migrate user password", e);
            }
        }
    }

    public boolean isValidSession(String token) {
        return authService.isValidSession(token);
    }

    public String getUserIdForSession(String token) {
        var session = authService.getSession(token);
        return session != null ? session.userId() : null;
    }

    public String getUserRole(String userId) {
        if (userId == null)
            return null;
        try {
            Optional<User> user = authStore.getUserById(userId);
            return user.map(User::getRole).orElse(null);
        } catch (Exception e) {
            LOGGER.error("Error getting user role", e);
            return null;
        }
    }

    // Tickets are short-lived (30 seconds) and can only be used once
    private static final long WS_TICKET_EXPIRY_MS = 30_000;

    /**
     * Creates a short-lived ticket for WebSocket authentication.
     */
    public String createWsTicket(String sessionToken) {
        var session = authService.getSession(sessionToken);
        if (session == null) {
            return null;
        }
        String ticket = generateToken();
        wsTickets.put(ticket, new TicketInfo(session.userId(), System.currentTimeMillis()));
        return ticket;
    }

    /**
     * Validates and consumes a WebSocket ticket (one-time use).
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
     */
    public boolean validateToken(String token) {
        return isValidSession(token);
    }

    /**
     * Cleans up expired sessions and tickets.
     */
    public void cleanup() {
        authService.cleanup();
        long now = System.currentTimeMillis();
        wsTickets.entrySet().removeIf(entry -> now - entry.getValue().createdAt > WS_TICKET_EXPIRY_MS);
    }

    private void createDefaultAdmin() {
        // Check for password from environment variable first
        String envPassword = System.getenv("JUSTSYNCIT_ADMIN_PASSWORD");
        boolean passwordFromEnv = envPassword != null && !envPassword.isBlank();

        String tempPass = passwordFromEnv ? envPassword : java.util.UUID.randomUUID().toString();
        User admin = new User(generateId(), "admin", "Administrator", "admin");
        admin.setPassword(tempPass);

        try {
            authStore.createUser(admin);
        } catch (Exception e) {
            LOGGER.error("Failed to create default admin", e);
            return;
        }

        // Print to console (stdout) specifically so it can be seen in the terminal or
        // captured by container logs
        // but NOT persisted in application logs which might be rotated/stored
        // insecurely.
        System.out.println("\n");
        System.out.println("===================================================================");
        System.out.println("            JUSTSYNCIT DEFAULT ADMIN ACCOUNT CREATED               ");
        System.out.println("===================================================================");
        System.out.println(" Username: admin");
        System.out.println(" Password: " + tempPass);
        System.out.println("===================================================================");
        System.out.println(" IMPORTANT: This password is ephemeral and generated randomly.     ");
        System.out.println(" Set JUSTSYNCIT_ADMIN_PASSWORD env var to configure a fixed password.");
        System.out.println("===================================================================\n");

        LOGGER.warn("Default admin account created. Credentials printed to stdout.");
    }
}
