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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * REST controller for user management and authentication.
 */
public final class UserController {

    private static final Logger LOGGER = Logger.getLogger(UserController.class.getName());
    private static final SecureRandom RANDOM = new SecureRandom();

    private final WebServerContext context;

    // In-memory user storage (would typically use a database)
    private final Map<String, User> users;
    private final Map<String, String> sessions; // token -> userId

    public UserController(WebServerContext context) {
        this.context = context;
        this.users = new ConcurrentHashMap<>();
        this.sessions = new ConcurrentHashMap<>();

        // Create default admin user
        User admin = new User("admin", "admin", "Administrator", "admin");
        users.put(admin.getId(), admin);
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
    @SuppressWarnings("unchecked")
    public void createUser(Context ctx) {
        try {
            Map<String, String> body = ctx.bodyAsClass(Map.class);
            String username = body.get("username");
            String password = body.get("password");
            String displayName = body.get("displayName");
            String role = body.getOrDefault("role", "user");

            if (username == null || username.isEmpty()) {
                ctx.status(400).json(ApiError.badRequest("username is required", ctx.path()));
                return;
            }
            if (password == null || password.isEmpty()) {
                ctx.status(400).json(ApiError.badRequest("password is required", ctx.path()));
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
            user.setPasswordHash(hashPassword(password));
            users.put(user.getId(), user);

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
                user.setDisplayName(body.get("displayName"));
            }
            if (body.containsKey("role")) {
                user.setRole(body.get("role"));
            }
            if (body.containsKey("password") && !body.get("password").isEmpty()) {
                user.setPasswordHash(hashPassword(body.get("password")));
            }

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

            if (user == null || !verifyPassword(password, user.getPasswordHash())) {
                ctx.status(401).json(ApiError.of(401, "Unauthorized",
                        "Invalid username or password", ctx.path()));
                return;
            }

            // Generate session token
            String token = generateToken();
            sessions.put(token, user.getId());

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

    private String generateId() {
        byte[] bytes = new byte[8];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashPassword(String password) {
        // Simple hash for demo - in production use bcrypt or similar
        return Base64.getEncoder().encodeToString(password.getBytes());
    }

    private boolean verifyPassword(String password, String hash) {
        return hashPassword(password).equals(hash);
    }

    // User class

    private static class User {
        private final String id;
        private final String username;
        private String displayName;
        private String role;
        private String passwordHash;

        User(String id, String username, String displayName, String role) {
            this.id = id;
            this.username = username;
            this.displayName = displayName;
            this.role = role;
            this.passwordHash = hashDefaultPassword();
        }

        private String hashDefaultPassword() {
            return Base64.getEncoder().encodeToString("admin".getBytes());
        }

        String getId() {
            return id;
        }

        String getUsername() {
            return username;
        }

        String getDisplayName() {
            return displayName;
        }

        void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        String getRole() {
            return role;
        }

        void setRole(String role) {
            this.role = role;
        }

        String getPasswordHash() {
            return passwordHash;
        }

        void setPasswordHash(String passwordHash) {
            this.passwordHash = passwordHash;
        }
    }
}
