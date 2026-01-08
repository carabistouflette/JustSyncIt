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
import com.justsyncit.web.controller.AuthController;
import com.justsyncit.web.controller.NetworkController;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Embedded web server for the JustSyncIt management interface.
 * Uses Javalin with embedded Jetty for REST API and WebSocket support.
 */
public final class WebServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(WebServer.class);
    private static final int DEFAULT_PORT = 8080;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
            LOGGER.info("Starting web server on port {}", port);

            app = Javalin.create(config -> {
                // Only enable CORS if explicitly requested via env var.
                String corsOrigin = System.getenv("CORS_ALLOWED_ORIGIN");
                if (corsOrigin != null && !corsOrigin.isBlank()) {
                    config.bundledPlugins.enableCors(cors -> {
                        cors.addRule(it -> {
                            it.allowHost(corsOrigin.trim());
                        });
                    });
                    LOGGER.info("CORS enabled for origin: {}", corsOrigin);
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
                    LOGGER.debug("{} {} - {}ms", ctx.method(), ctx.path(), Math.round(executionTimeMs));
                });

                String keystorePath = System.getenv("SSL_KEYSTORE_PATH");
                String keystorePass = System.getenv("SSL_KEYSTORE_PASSWORD");

                if (keystorePath != null && !keystorePath.isBlank() && keystorePass != null) {
                    LOGGER.info("Enabling HTTPS with keystore: {}", keystorePath);
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
                    LOGGER.info("HTTPS configured on port {}", port);
                }
            });

            // Configure WebSocket
            configureWebSocket();

            // Configure REST API routes
            configureRoutes();

            // Handle SPA routing using error handler for 404s
            app.error(404, ctx -> {
                String path = ctx.path();
                // Only serve index.html for non-API, non-static paths (SPA client routes)
                if (!path.startsWith("/api/") && !path.contains(".")) {
                    java.nio.file.Path indexPath = java.nio.file.Paths.get("web-ui/dist/index.html");
                    if (java.nio.file.Files.exists(indexPath)) {
                        ctx.status(200);
                        ctx.contentType("text/html");
                        try {
                            ctx.result(java.nio.file.Files.readString(indexPath));
                        } catch (java.io.IOException e) {
                            ctx.result("Error reading index.html");
                        }
                        return;
                    }
                }
                // Default 404 behavior for API routes and static files
                ctx.result("Not Found: " + path);
            });

            // Authentication filter
            app.before("/api/*", ctx -> {
                String path = ctx.path();
                // Allow setup, status, login and health without authentication
                if (path.equals("/api/auth/status") || path.equals("/api/auth/login") || path.equals("/api/auth/setup")
                        || path.equals("/api/health")) {
                    return;
                }

                String authHeader = ctx.header("Authorization");
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    String token = authHeader.substring(7);
                    if (context.getAuthController() == null || !context.getAuthController().isValidSession(token)) {
                        ctx.status(401).json(Map.of("error", "Unauthorized"));
                    }
                } else {
                    ctx.status(401).json(Map.of("error", "Unauthorized"));
                }
            });

            app.start(port);
            LOGGER.info("Web server started successfully at http://localhost:{}", port);
            LOGGER.info("Registered API routes:");
            LOGGER.info("  GET  /api/auth/status");
            LOGGER.info("  POST /api/auth/setup");
            LOGGER.info("  POST /api/auth/login");
            LOGGER.info("  GET  /api/health");
        } else {
            LOGGER.warn("Web server is already running");
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
                LOGGER.warn("Failed to broadcast to client: {}", e.getMessage());
            }
        });
    }

    private void configureWebSocket() {
        app.ws("/ws", ws -> {
            ws.onConnect(ctx -> {
                String clientId = ctx.sessionId();
                wsClients.put(clientId, ctx);
                LOGGER.info("WebSocket client connected: {}", clientId);
            });

            ws.onMessage(ctx -> {
                String clientId = ctx.sessionId();
                LOGGER.debug("WebSocket message from {}: {}", clientId, ctx.message());
            });

            ws.onClose(ctx -> {
                String clientId = ctx.sessionId();
                wsClients.remove(clientId);
                LOGGER.info("WebSocket client disconnected: {}", clientId);
            });

            ws.onError(ctx -> {
                LOGGER.warn("WebSocket error: {}", ctx.error());
            });
        });
    }

    private void configureRoutes() {
        // Create controllers
        AuthController authController = new AuthController(context.getMasterPasswordService());
        context.setAuthController(authController);

        ConfigController configController = new ConfigController(context);
        FileBrowserController fileBrowserController = new FileBrowserController();
        BackupController backupController = new BackupController(context, this, configController);
        SnapshotController snapshotController = new SnapshotController(context);
        RestoreController restoreController = new RestoreController(context, this, configController);
        com.justsyncit.web.controller.SchedulerController schedulerController = new com.justsyncit.web.controller.SchedulerController(
                context);
        NetworkController networkController = new NetworkController(context);

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

        // Network endpoints
        app.get("/api/network/stats", networkController::getNetworkStats);
        app.get("/api/network/status", networkController::getNetworkStatus);

        // Config endpoints
        app.get("/api/config", configController::getConfig);
        app.put("/api/config", configController::updateConfig);
        app.get("/api/config/backup-sources", configController::getBackupSources);
        app.post("/api/config/backup-sources", configController::addBackupSource);

        // Auth endpoints
        app.get("/api/auth/status", authController::getStatus);
        app.post("/api/auth/setup", authController::setup);
        app.post("/api/auth/login", authController::login);
        app.post("/api/auth/logout", authController::logout);

        // Health check
        app.get("/api/health", ctx -> {
            ctx.json(java.util.Map.of("status", "ok", "timestamp", System.currentTimeMillis()));
        });
    }

    private String serializeToJson(Object data) {
        if (data == null) {
            return "null";
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(data);
        } catch (Exception e) {
            LOGGER.warn("Failed to serialize to JSON: {}", e.getMessage());
            return "{}";
        }
    }
}
