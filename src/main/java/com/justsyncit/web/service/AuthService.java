package com.justsyncit.web.service;

import com.justsyncit.web.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.util.Base64;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Service responsible for authentication and session management.
 * Delegates persistence to SqliteAuthStore.
 */
public class AuthService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthService.class);
    private static final long SESSION_TTL_MS = 24 * 60 * 60 * 1000L; // 24 hours
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final SqliteAuthStore authStore;

    // Session record definition
    public record Session(String userId, String username, String role, long expiryTime) {
    }

    public AuthService(SqliteAuthStore authStore) {
        this.authStore = authStore;
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

        try {
            authStore.createSession(session, token);
        } catch (SQLException e) {
            LOGGER.error("Failed to create session for user {}", user.getUsername(), e);
            throw new RuntimeException("Database error creating session", e);
        }
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
        try {
            Optional<Session> sessionOpt = authStore.getSession(token);
            if (sessionOpt.isEmpty())
                return false;

            Session session = sessionOpt.get();
            if (System.currentTimeMillis() > session.expiryTime()) {
                authStore.deleteSession(token);
                return false;
            }
            return true;
        } catch (SQLException e) {
            LOGGER.error("Error validating session", e);
            return false;
        }
    }

    /**
     * Gets session details if valid.
     *
     * @param token The session token
     * @return The session or null
     */
    public Session getSession(String token) {
        if (token == null)
            return null;
        try {
            // We could optimize this to single call if isValidSession wasn't separate
            Optional<Session> sessionOpt = authStore.getSession(token);
            if (sessionOpt.isPresent()) {
                Session session = sessionOpt.get();
                if (System.currentTimeMillis() > session.expiryTime()) {
                    authStore.deleteSession(token);
                    return null;
                }
                return session;
            }
        } catch (SQLException e) {
            LOGGER.error("Error retrieving session", e);
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
            try {
                authStore.deleteSession(token);
            } catch (SQLException e) {
                LOGGER.error("Error invalidating session", e);
            }
        }
    }

    /**
     * Cleans up expired sessions.
     */
    public void cleanup() {
        try {
            authStore.cleanupExpiredSessions(System.currentTimeMillis());
        } catch (SQLException e) {
            LOGGER.error("Error cleaning up sessions", e);
        }
    }
}
