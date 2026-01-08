package com.justsyncit.web.controller;

import com.justsyncit.auth.MasterPasswordService;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Controller for authentication and initial setup.
 */
public class AuthController {
    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);
    private final MasterPasswordService authService;
    private final Map<String, Long> sessions = new ConcurrentHashMap<>();
    private static final long SESSION_TIMEOUT_MS = 24 * 60 * 60 * 1000; // 24 hours
    private static final int MAX_SESSIONS = 10000; // Hard limit to prevent OOM

    // Cleanup scheduler
    private final java.util.concurrent.ScheduledExecutorService cleanupExecutor;

    public AuthController(MasterPasswordService authService) {
        this.authService = authService;
        this.cleanupExecutor = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "AuthSessionCleanup");
            t.setDaemon(true);
            return t;
        });

        // Schedule periodic cleanup (e.g., every hour)
        this.cleanupExecutor.scheduleAtFixedRate(this::cleanupSessions, 1, 1, java.util.concurrent.TimeUnit.HOURS);
    }

    private void cleanupSessions() {
        long now = System.currentTimeMillis();
        sessions.entrySet().removeIf(entry -> now - entry.getValue() > SESSION_TIMEOUT_MS);
        logger.debug("Cleaned up expired sessions. Current count: {}", sessions.size());
    }

    /**
     * GET /api/auth/status
     * Returns whether a master password is set.
     */
    public void getStatus(Context ctx) {
        if (authService == null) {
            ctx.json(Map.of(
                    "isSet", false,
                    "isSetup", false,
                    "warning", "Authentication service not initialized"));
            return;
        }
        boolean isSet = authService.isPasswordSet();
        ctx.json(Map.of(
                "isSet", isSet,
                "isSetup", isSet));
    }

    /**
     * POST /api/auth/setup
     * Sets the initial master password.
     */
    public void setup(Context ctx) {
        if (authService.isPasswordSet()) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "Password already set"));
            return;
        }

        @SuppressWarnings("unchecked")
        Map<String, String> body = ctx.bodyAsClass(Map.class);
        String password = body.get("password");

        if (password == null || password.length() < 8) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "Password too short (min 8 chars)"));
            return;
        }

        try {
            authService.setupPassword(password.toCharArray());
            ctx.status(HttpStatus.CREATED).json(Map.of("status", "success"));
        } catch (Exception e) {
            logger.error("Failed to setup password", e);
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(Map.of("error", "Setup failed"));
        }
    }

    /**
     * POST /api/auth/login
     * Verifies the master password and creates a session.
     */
    public void login(Context ctx) {
        // Prevent OOM attack
        if (sessions.size() >= MAX_SESSIONS) {
            // Force a cleanup
            cleanupSessions();
            if (sessions.size() >= MAX_SESSIONS) {
                ctx.status(HttpStatus.SERVICE_UNAVAILABLE).json(Map.of("error", "Too many active sessions"));
                return;
            }
        }

        @SuppressWarnings("unchecked")
        Map<String, String> body = ctx.bodyAsClass(Map.class);
        String password = body.get("password");

        try {
            if (authService.verifyAndDerive(password.toCharArray())) {
                String token = UUID.randomUUID().toString();
                sessions.put(token, System.currentTimeMillis());
                ctx.json(Map.of("token", token));
            } else {
                ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("error", "Invalid password"));
            }
        } catch (Exception e) {
            logger.error("Login failed", e);
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(Map.of("error", "Login failed"));
        }
    }

    /**
     * Validates a session token.
     */
    public boolean isValidSession(String token) {
        if (token == null)
            return false;
        Long lastSeen = sessions.get(token);
        if (lastSeen == null)
            return false;

        if (System.currentTimeMillis() - lastSeen > SESSION_TIMEOUT_MS) {
            sessions.remove(token);
            return false;
        }

        // Slide session window
        sessions.put(token, System.currentTimeMillis());
        return true;
    }

    public void logout(Context ctx) {
        String token = ctx.header("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            sessions.remove(token.substring(7));
        }
        ctx.status(HttpStatus.NO_CONTENT);
    }
}
