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

    private final int port;
    private final WebServerContext context;
    private final AtomicBoolean running;
    private final ConcurrentHashMap<String, WsContext> wsClients;
    private Javalin app;

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
                // Enable CORS for development
                config.bundledPlugins.enableCors(cors -> {
                    cors.addRule(it -> {
                        it.anyHost();
                    });
                });

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
        } else {
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
            ws.onConnect(ctx -> {
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
        BackupController backupController = new BackupController(context, this);
        SnapshotController snapshotController = new SnapshotController(context);
        RestoreController restoreController = new RestoreController(context, this);
        FileBrowserController fileBrowserController = new FileBrowserController();
        ConfigController configController = new ConfigController(context);
        UserController userController = new UserController(context);

        // Backup endpoints
        app.post("/api/backup", backupController::startBackup);
        app.get("/api/backup/status", backupController::getStatus);
        app.get("/api/backup/history", backupController::getHistory);
        app.post("/api/backup/cancel", backupController::cancelBackup);

        // Snapshot endpoints
        app.get("/api/snapshots", snapshotController::listSnapshots);
        app.get("/api/snapshots/{id}", snapshotController::getSnapshot);
        app.get("/api/snapshots/{id}/files", snapshotController::getSnapshotFiles);
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

    private String serializeToJson(Object data) {
        if (data == null) {
            return "null";
        }
        if (data instanceof String) {
            return "\"" + data + "\"";
        }
        // For complex objects, use Jackson (already available in project)
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.writeValueAsString(data);
        } catch (Exception e) {
            LOGGER.warning("Failed to serialize to JSON: " + e.getMessage());
            return "{}";
        }
    }
}
