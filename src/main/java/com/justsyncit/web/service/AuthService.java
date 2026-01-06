/*
 * JustSyncIt - Backup solution
 * Copyright (C) 2023 JustSyncIt Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.justsyncit.web.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.justsyncit.web.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service responsible for authentication and session management.
 * Handles session persistence to ensure users stay logged in across restarts.
 */
public class AuthService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthService.class);
    private static final long SESSION_TTL_MS = 24 * 60 * 60 * 1000L; // 24 hours
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Path sessionsFile;

    public record Session(String userId, String username, String role, long expiryTime) {
    }

    public AuthService(Path configDir) {
        this.sessionsFile = configDir.resolve("sessions.json");
        loadSessions();
    }

    /**
     * Creates a new session for the given user.
     *
     * @param user The authenticated user
     * @return The session token
     */
    public String createSession(User user) {
        byte[] tokenBytes = new byte[32];
        SECURE_RANDOM.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);

        Session session = new Session(
                user.getId(),
                user.getUsername(),
                user.getRole(),
                System.currentTimeMillis() + SESSION_TTL_MS);

        sessions.put(token, session);
        saveSessions();
        return token;
    }

    /**
     * Validates a session token.
     *
     * @param token The session token
     * @return true if valid and not expired
     */
    public boolean isValidSession(String token) {
        if (token == null)
            return false;
        Session session = sessions.get(token);
        if (session == null)
            return false;

        if (System.currentTimeMillis() > session.expiryTime()) {
            sessions.remove(token);
            saveSessions();
            return false;
        }
        return true;
    }

    /**
     * Gets session details if valid.
     *
     * @param token The session token
     * @return The session or null
     */
    public Session getSession(String token) {
        if (isValidSession(token)) {
            return sessions.get(token);
        }
        return null;
    }

    /**
     * Invalidates (logs out) a session.
     *
     * @param token The session token
     */
    public void invalidateSession(String token) {
        if (token != null) {
            sessions.remove(token);
            saveSessions();
        }
    }

    /**
     * Cleans up expired sessions.
     */
    public void cleanup() {
        long now = System.currentTimeMillis();
        boolean changed = sessions.entrySet().removeIf(entry -> now > entry.getValue().expiryTime());
        if (changed) {
            saveSessions();
            LOGGER.info("Cleaned up expired sessions");
        }
    }

    private void loadSessions() {
        if (Files.exists(sessionsFile)) {
            try {
                Map<String, Session> loaded = objectMapper.readValue(sessionsFile.toFile(),
                        new TypeReference<Map<String, Session>>() {
                        });
                sessions.putAll(loaded);
                LOGGER.info("Loaded {} sessions from persistence", loaded.size());
            } catch (IOException e) {
                LOGGER.error("Failed to load sessions: {}", e.getMessage());
            }
        }
    }

    private synchronized void saveSessions() {
        try {
            // Write to temp file first then atomic move could be better, but simple write
            // is okay for now
            // We use synchronized to prevent torn writes
            objectMapper.writeValue(sessionsFile.toFile(), sessions);
        } catch (IOException e) {
            LOGGER.error("Failed to save sessions: {}", e.getMessage());
        }
    }
}
