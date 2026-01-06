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

package com.justsyncit.web;

import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import io.javalin.websocket.WsContext;

import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.justsyncit.web.controller.BackupController;
import com.justsyncit.web.controller.SnapshotController;
import com.justsyncit.web.controller.RestoreController;
import com.justsyncit.web.controller.FileBrowserController;
import com.justsyncit.web.controller.ConfigController;
import com.justsyncit.web.controller.UserController;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Embedded web server for the JustSyncIt management interface.
 * Uses Javalin with embedded Jetty for REST API and WebSocket support.
 */
public final class WebServer {

    private static final Logger LOGGER = Logger.getLogger(WebServer.class.getName());
    private static final int DEFAULT_PORT = 8080;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final int port;
    private final WebServerContext context;
    private final AtomicBoolean running;
    private final ConcurrentHashMap<String, WsContext> wsClients;
    private Javalin app;
    private UserController userController;

    /**
     * Creates a new web server with default port.
     *
     * @param context the web server context containing services
     */
    public WebServer(WebServerContext context) {
        this(DEFAULT_PORT, context);
    }

    /**
     * Creates a new web server with specified port.
     *
     * @param port    the port to listen on
     * @param context the web server context containing services
     */
    public WebServer(int port, WebServerContext context) {
        this.port = port;
        this.context = context;
        this.running = new AtomicBoolean(false);
        this.wsClients = new ConcurrentHashMap<>();
    }

    /**
     * Starts the web server.
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            LOGGER.info("Starting web server on port " + port);

            app = Javalin.create(config -> {
                // Only enable CORS if explicitly requested via env var.
                String corsOrigin = System.getenv("CORS_ALLOWED_ORIGIN");
                if (corsOrigin != null && !corsOrigin.isBlank()) {
                    config.bundledPlugins.enableCors(cors -> {
                        cors.addRule(it -> {
                            it.allowHost(corsOrigin.trim());
                        });
                    });
                    LOGGER.info("CORS enabled for origin: " + corsOrigin);
                } else {
                    LOGGER.info("CORS disabled (default secure). Set CORS_ALLOWED_ORIGIN to enable.");
                }

                // Serve static files from web-ui/dist
                config.staticFiles.add(staticFiles -> {
                    staticFiles.hostedPath = "/";
                    staticFiles.directory = "web-ui/dist";
                    staticFiles.location = Location.EXTERNAL;
                    staticFiles.precompress = false;
                });

                // Enable request logging
                config.requestLogger.http((ctx, executionTimeMs) -> {
                    LOGGER.fine(String.format("%s %s - %.0fms",
                            ctx.method(), ctx.path(), executionTimeMs));
                });

                String keystorePath = System.getenv("SSL_KEYSTORE_PATH");
                String keystorePass = System.getenv("SSL_KEYSTORE_PASSWORD");

                if (keystorePath != null && !keystorePath.isBlank() && keystorePass != null) {
                    LOGGER.info("Enabling HTTPS with keystore: " + keystorePath);
                    config.jetty.modifyServer(server -> {
                        // SSL Context
                        SslContextFactory.Server sslContextFactory = new SslContextFactory.Server();
                        sslContextFactory.setKeyStorePath(keystorePath);
                        sslContextFactory.setKeyStorePassword(keystorePass);

                        // SSL Connector
                        ServerConnector sslConnector = new ServerConnector(server, sslContextFactory);
                        sslConnector.setPort(port);
                        server.addConnector(sslConnector);
                    });
                    LOGGER.info("HTTPS configured on port " + port);
                }
            });

            // Initialize User Controller
            this.userController = new UserController(context);

            // Auth Middleware
            app.before("/api/*", ctx -> {
                if (ctx.method().toString().equals("OPTIONS"))
                    return; // Allow CORS preflight

                String path = ctx.path();
                // Public endpoints
                if (path.equals("/api/auth/login") ||
                        path.equals("/api/auth/logout") ||
                        path.equals("/api/health")) {
                    return;
                }

                String authHeader = ctx.header("Authorization");
                if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                    ctx.status(401)
                            .json(java.util.Map.of("error", "Unauthorized", "message",
                                    "Missing or invalid or expired ticket"));
                    ctx.skipRemainingHandlers();
                    return;
                }

                String token = authHeader.substring(7);
                if (!userController.isValidSession(token)) {
                    ctx.status(401)
                            .json(java.util.Map.of("error", "Unauthorized", "message", "Invalid or expired token"));
                    ctx.skipRemainingHandlers();
                    return;
                }
            });

            java.util.concurrent.ConcurrentHashMap<String, long[]> loginAttempts = new java.util.concurrent.ConcurrentHashMap<>();
            final int MAX_ATTEMPTS = 5;
            final long WINDOW_MS = 60_000; // 1 minute

            app.before("/api/auth/login", ctx -> {
                if (ctx.method().toString().equals("OPTIONS"))
                    return;

                String clientIp = ctx.ip();
                long now = System.currentTimeMillis();

                loginAttempts.compute(clientIp, (ip, record) -> {
                    if (record == null) {
                        return new long[] { 1, now }; // [count, windowStart]
                    }
                    if (now - record[1] > WINDOW_MS) {
                        return new long[] { 1, now }; // Reset window
                    }
                    record[0]++;
                    return record;
                });

                long[] record = loginAttempts.get(clientIp);
                if (record != null && record[0] > MAX_ATTEMPTS && (now - record[1]) <= WINDOW_MS) {
                    ctx.status(429).json(java.util.Map.of(
                            "error", "Too Many Requests",
                            "message", "Rate limit exceeded. Try again in 1 minute."));
                    ctx.skipRemainingHandlers();
                }
            });

            // Admin-only endpoints
            app.before("/api/users/*", ctx -> {
                if (ctx.method().toString().equals("OPTIONS"))
                    return;
                requireRole(ctx, "admin");
            });
            app.before("/api/config/*", ctx -> {
                if (ctx.method().toString().equals("OPTIONS"))
                    return;
                requireRole(ctx, "admin");
            });
            app.before("/api/schedules/*", ctx -> {
                if (ctx.method().toString().equals("OPTIONS"))
                    return;
                requireRole(ctx, "admin", "user");
            });
            app.before("/api/schedules", ctx -> {
                if (ctx.method().toString().equals("OPTIONS"))
                    return;
                requireRole(ctx, "admin", "user");
            });
            app.before("/api/backup/*", ctx -> {
                if (ctx.method().toString().equals("OPTIONS"))
                    return;
                requireRole(ctx, "admin", "user");
            });
            app.before("/api/backup", ctx -> {
                if (ctx.method().toString().equals("OPTIONS"))
                    return;
                requireRole(ctx, "admin", "user");
            });
            app.before("/api/restore/*", ctx -> {
                if (ctx.method().toString().equals("OPTIONS"))
                    return;
                requireRole(ctx, "admin", "user");
            });
            app.before("/api/restore", ctx -> {
                if (ctx.method().toString().equals("OPTIONS"))
                    return;
                requireRole(ctx, "admin", "user");
            });
            app.before("/api/snapshots/*", ctx -> {
                if (ctx.method().toString().equals("OPTIONS"))
                    return;
                // DELETE requires admin/user, GET allows viewer too
                if (ctx.method().toString().equals("DELETE")) {
                    requireRole(ctx, "admin", "user");
                } else {
                    requireRole(ctx, "admin", "user", "viewer");
                }
            });
            app.before("/api/snapshots", ctx -> {
                if (ctx.method().toString().equals("OPTIONS"))
                    return;
                requireRole(ctx, "admin", "user", "viewer");
            });
            app.before("/api/files/*", ctx -> {
                if (ctx.method().toString().equals("OPTIONS"))
                    return;
                requireRole(ctx, "admin", "user", "viewer");
            });
            app.before("/api/files", ctx -> {
                if (ctx.method().toString().equals("OPTIONS"))
                    return;
                requireRole(ctx, "admin", "user", "viewer");
            });

            // Configure WebSocket
            configureWebSocket();

            // Configure REST API routes
            configureRoutes();

            // Handle SPA routing - serve index.html for unmatched routes
            app.get("/{path}", ctx -> {
                ctx.redirect("/");
            });

            app.start(port);
            LOGGER.info("Web server started successfully at http://localhost:" + port);
        } else

        {
            LOGGER.warning("Web server is already running");
        }
    }

    /**
     * Stops the web server.
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            LOGGER.info("Stopping web server");
            if (app != null) {
                app.stop();
                app = null;
            }
            wsClients.clear();
            LOGGER.info("Web server stopped");
        }
    }

    /**
     * Returns whether the server is running.
     *
     * @return true if the server is running
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Returns the server port.
     *
     * @return the port number
     */
    public int getPort() {
        return port;
    }

    /**
     * Returns the actual bound port.
     * Useful when starting with port 0 (random port).
     *
     * @return the bound port or -1 if not running
     */
    public int getBoundPort() {
        return app != null ? app.port() : -1;
    }

    /**
     * Broadcasts a message to all connected WebSocket clients.
     *
     * @param eventType the event type
     * @param data      the event data
     */
    public void broadcast(String eventType, Object data) {
        String message = String.format("{\"type\":\"%s\",\"data\":%s}",
                eventType, serializeToJson(data));
        wsClients.values().forEach(ws -> {
            try {
                ws.send(message);
            } catch (Exception e) {
                LOGGER.warning("Failed to broadcast to client: " + e.getMessage());
            }
        });
    }

    private void configureWebSocket() {
        app.ws("/ws", ws -> {
            // Authenticate before WebSocket upgrade
            ws.onConnect(ctx -> {
                // Validate token from query parameter
                String token = ctx.queryParam("token");
                if (token != null && !token.isEmpty()) {
                    LOGGER.warning("WebSocket auth using query parameter (potential leak in proxy logs). Client: "
                            + ctx.sessionId());
                } else {
                    // Try Header (Standard for some clients, difficult for Browsers)
                    token = ctx.header("X-Auth-Token");
                }

                if (token == null || token.isEmpty()) {
                    LOGGER.warning("WebSocket connection rejected: missing token");
                    ctx.closeSession(4001, "Authentication required");
                    return;
                }

                // Prefer X-Auth-Token header where possible.
                if (userController.validateAndConsumeTicket(token) == null) {
                    LOGGER.warning("WebSocket connection rejected: invalid or expired ticket");
                    ctx.closeSession(4003, "Invalid token");
                    return;
                }

                String clientId = ctx.sessionId();
                wsClients.put(clientId, ctx);
                LOGGER.info("WebSocket client connected: " + clientId);
            });

            ws.onClose(ctx -> {
                String clientId = ctx.sessionId();
                wsClients.remove(clientId);
                LOGGER.info("WebSocket client disconnected: " + clientId);
            });

            ws.onMessage(ctx -> {
                LOGGER.fine("WebSocket message received: " + ctx.message());
                // Handle incoming messages if needed
            });

            ws.onError(ctx -> {
                LOGGER.warning("WebSocket error: " + ctx.error());
            });
        });
    }

    private void configureRoutes() {
        // Create controllers
        ConfigController configController = new ConfigController(context);
        FileBrowserController fileBrowserController = new FileBrowserController(configController);
        BackupController backupController = new BackupController(context, this);
        SnapshotController snapshotController = new SnapshotController(context);
        RestoreController restoreController = new RestoreController(context, this, configController);
        com.justsyncit.web.controller.SchedulerController schedulerController = new com.justsyncit.web.controller.SchedulerController(
                context);
        // UserController is already initialized in start()

        // Backup endpoints
        app.post("/api/backup", backupController::startBackup);
        app.get("/api/backup/status", backupController::getStatus);
        app.get("/api/backup/history", backupController::getHistory);
        app.post("/api/backup/cancel", backupController::cancelBackup);

        // Schedule endpoints
        app.get("/api/schedules", schedulerController::listSchedules);
        app.post("/api/schedules", schedulerController::createSchedule);
        app.delete("/api/schedules/{id}", schedulerController::deleteSchedule);

        // Snapshot endpoints
        app.get("/api/snapshots", snapshotController::listSnapshots);
        app.get("/api/snapshots/{id}", snapshotController::getSnapshot);
        app.get("/api/snapshots/{id}/files", snapshotController::getSnapshotFiles);
        app.get("/api/snapshots/{id}/stats", snapshotController::getSnapshotStats);
        app.delete("/api/snapshots/{id}", snapshotController::deleteSnapshot);
        app.post("/api/snapshots/{id}/verify", snapshotController::verifySnapshot);

        // Restore endpoints
        app.post("/api/restore", restoreController::startRestore);
        app.get("/api/restore/status", restoreController::getStatus);
        app.post("/api/restore/cancel", restoreController::cancelRestore);

        // File browser endpoints
        app.get("/api/files", fileBrowserController::browse);
        app.get("/api/files/search", fileBrowserController::search);

        // Config endpoints
        app.get("/api/config", configController::getConfig);
        app.put("/api/config", configController::updateConfig);
        app.get("/api/config/backup-sources", configController::getBackupSources);
        app.post("/api/config/backup-sources", configController::addBackupSource);

        // User endpoints
        app.get("/api/users", userController::listUsers);
        app.post("/api/users", userController::createUser);
        app.put("/api/users/{id}", userController::updateUser);
        app.delete("/api/users/{id}", userController::deleteUser);

        // Auth endpoints
        app.post("/api/auth/login", userController::login);
        app.post("/api/auth/logout", userController::logout);

        // Health check
        app.get("/api/health", ctx -> {
            ctx.json(java.util.Map.of("status", "ok", "timestamp", System.currentTimeMillis()));
        });
    }

    private void requireRole(io.javalin.http.Context ctx, String... allowedRoles) {
        String authHeader = ctx.header("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return; // Auth middleware will handle this
        }

        String token = authHeader.substring(7);
        String userId = userController.getUserIdForSession(token);
        String userRole = userController.getUserRole(userId);

        if (userRole == null) {
            ctx.status(403).json(java.util.Map.of("error", "Forbidden",
                    "message", "User role not found"));
            ctx.skipRemainingHandlers();
            return;
        }

        for (String role : allowedRoles) {
            if (role.equals(userRole)) {
                return; // Role matches, allow access
            }
        }

        ctx.status(403).json(java.util.Map.of("error", "Forbidden",
                "message", "Insufficient permissions. Required: " + java.util.Arrays.toString(allowedRoles)));
        ctx.skipRemainingHandlers();
    }

    private String serializeToJson(Object data) {
        if (data == null) {
            return "null";
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(data);
        } catch (Exception e) {
            LOGGER.warning("Failed to serialize to JSON: " + e.getMessage());
            return "{}";
        }
    }
}
