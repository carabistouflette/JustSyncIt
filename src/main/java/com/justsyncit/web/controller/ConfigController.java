package com.justsyncit.web.controller;

import com.justsyncit.web.WebServerContext;
import com.justsyncit.web.dto.ApiError;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import io.javalin.http.Context;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.justsyncit.web.dto.ConfigRequests.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * REST controller for configuration management.
 */
public class ConfigController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigController.class);
    private static final Path CONFIG_FILE = Paths.get("config", "app-config.json");
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final WebServerContext context;

    // Config storage - now persisted to disk
    private final Map<String, Object> config;
    private final List<String> backupSources;

    public ConfigController(WebServerContext context) {
        this.context = context;
        this.config = new HashMap<>();
        this.backupSources = new ArrayList<>();

        // Initialize with defaults
        config.put("webPort", 8080);
        config.put("defaultChunkSize", 4 * 1024 * 1024);
        config.put("compressionEnabled", true);
        config.put("compressionLevel", 3);
        config.put("encryptionEnabled", false);
        config.put("maxConcurrentBackups", 1);
        config.put("retentionDays", 30);

        // Load saved config (overwrites defaults if file exists)
        loadConfig();
    }

    /**
     * GET /api/config - Get current configuration.
     */
    public void getConfig(Context ctx) {
        ctx.json(config);
    }

    /**
     * PUT /api/config - Update configuration.
     */
    public void updateConfig(Context ctx) {
        try {
            UpdateConfigRequest request = ctx.bodyAsClass(UpdateConfigRequest.class);
            Map<String, Object> updates = new HashMap<>();

            // Validate and apply updates
            if (request.webPort() != null) {
                int port = request.webPort();
                if (port < 1 || port > 65535) {
                    ctx.status(400).json(ApiError.badRequest(
                            "webPort must be between 1 and 65535", ctx.path()));
                    return;
                }
                config.put("webPort", port);
                updates.put("webPort", port);
            }

            if (request.defaultChunkSize() != null) {
                long size = request.defaultChunkSize();
                if (size < 1024 || size > 64 * 1024 * 1024) {
                    ctx.status(400).json(ApiError.badRequest(
                            "defaultChunkSize must be between 1KB and 64MB", ctx.path()));
                    return;
                }
                config.put("defaultChunkSize", size);
                updates.put("defaultChunkSize", size);
            }

            if (request.compressionLevel() != null) {
                int level = request.compressionLevel();
                if (level < 1 || level > 22) {
                    ctx.status(400).json(ApiError.badRequest(
                            "compressionLevel must be between 1 and 22", ctx.path()));
                    return;
                }
                config.put("compressionLevel", level);
                updates.put("compressionLevel", level);
            }

            if (request.compressionEnabled() != null) {
                config.put("compressionEnabled", request.compressionEnabled());
                updates.put("compressionEnabled", request.compressionEnabled());
            }

            if (request.encryptionEnabled() != null) {
                config.put("encryptionEnabled", request.encryptionEnabled());
                updates.put("encryptionEnabled", request.encryptionEnabled());
            }

            if (request.maxConcurrentBackups() != null) {
                config.put("maxConcurrentBackups", request.maxConcurrentBackups());
                updates.put("maxConcurrentBackups", request.maxConcurrentBackups());
            }

            if (request.retentionDays() != null) {
                config.put("retentionDays", request.retentionDays());
                updates.put("retentionDays", request.retentionDays());
            }

            saveConfig();

            LOGGER.info("Configuration updated: {}", updates.keySet());
            ctx.json(Map.of("status", "updated", "config", config));

        } catch (Exception e) {
            LOGGER.error("Failed to update config: {}", e.getMessage(), e);
            ctx.status(500).json(ApiError.internalError(e.getMessage(), ctx.path()));
        }
    }

    /**
     * GET /api/config/backup-sources - List configured backup sources.
     */
    public void getBackupSources(Context ctx) {
        ctx.json(Map.of("sources", backupSources));
    }

    /**
     * POST /api/config/backup-sources - Add a backup source.
     */
    public void addBackupSource(Context ctx) {
        try {
            AddBackupSourceRequest request = ctx.bodyAsClass(AddBackupSourceRequest.class);
            String path = request.path();

            if (path == null || path.isEmpty()) {
                ctx.status(400).json(ApiError.badRequest("path is required", ctx.path()));
                return;
            }

            Path sourcePath;
            try {
                sourcePath = Paths.get(path).toAbsolutePath().normalize();
            } catch (java.nio.file.InvalidPathException e) {
                ctx.status(400).json(ApiError.badRequest("Invalid path format: " + e.getMessage(), ctx.path()));
                return;
            }

            // Check for null bytes (path traversal attack vector)
            if (path.contains("\0")) {
                LOGGER.warn("Path traversal attempt detected: null byte in path");
                ctx.status(400).json(ApiError.badRequest("Invalid path: contains invalid characters", ctx.path()));
                return;
            }

            // Ensure path is absolute (no relative paths allowed)
            if (!sourcePath.isAbsolute()) {
                ctx.status(400).json(ApiError.badRequest("Path must be absolute", ctx.path()));
                return;
            }

            // Ensure path doesn't escape allowed directories (path traversal check)
            String normalizedPath = sourcePath.toString();
            if (!normalizedPath.equals(path) && path.contains("..")) {
                LOGGER.warn("Path traversal attempt detected: {} -> {}", path, normalizedPath);
                ctx.status(400).json(ApiError.badRequest("Path traversal not allowed", ctx.path()));
                return;
            }

            // Validate path exists and is a directory
            if (!Files.exists(sourcePath)) {
                ctx.status(400).json(ApiError.badRequest("Path does not exist: " + normalizedPath, ctx.path()));
                return;
            }

            if (!Files.isDirectory(sourcePath)) {
                ctx.status(400).json(ApiError.badRequest("Path is not a directory: " + normalizedPath, ctx.path()));
                return;
            }

            if (!Files.isReadable(sourcePath)) {
                ctx.status(400).json(ApiError.badRequest("Path is not readable: " + normalizedPath, ctx.path()));
                return;
            }

            // Use normalized path for storage
            String canonicalPath = normalizedPath;

            if (backupSources.contains(canonicalPath)) {
                ctx.status(409).json(ApiError.of(409, "Conflict",
                        "Backup source already exists: " + canonicalPath, ctx.path()));
                return;
            }

            backupSources.add(canonicalPath);
            saveConfig();
            LOGGER.info("Added backup source: {}", canonicalPath);

            ctx.status(201).json(Map.of(
                    "status", "created",
                    "path", canonicalPath,
                    "sources", backupSources));

        } catch (Exception e) {
            LOGGER.error("Failed to add backup source: {}", e.getMessage(), e);
            ctx.status(500).json(ApiError.internalError(e.getMessage(), ctx.path()));
        }
    }

    /**
     * Gets the list of configured backup sources.
     * Use method for internal access.
     *
     * @return list of backup source paths
     */
    public List<String> getBackupSourcesList() {
        return new ArrayList<>(backupSources);
    }

    private void saveConfig() {
        try {
            Files.createDirectories(CONFIG_FILE.getParent());
            Map<String, Object> fullConfig = new HashMap<>(config);
            fullConfig.put("backupSources", backupSources);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(CONFIG_FILE.toFile(), fullConfig);
            LOGGER.info("Configuration saved to {}", CONFIG_FILE);
        } catch (Exception e) {
            LOGGER.error("Failed to save configuration: {}", e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private void loadConfig() {
        if (!Files.exists(CONFIG_FILE)) {
            LOGGER.info("No existing config file found, using defaults.");
            return;
        }
        try {
            Map<String, Object> loaded = objectMapper.readValue(CONFIG_FILE.toFile(),
                    new TypeReference<Map<String, Object>>() {
                    });
            config.putAll(loaded);

            // Extract backup sources if present
            Object sources = config.remove("backupSources");
            if (sources instanceof List) {
                backupSources.clear();
                for (Object src : (List<?>) sources) {
                    if (src instanceof String) {
                        backupSources.add((String) src);
                    }
                }
            }
            LOGGER.info("Configuration loaded from {}", CONFIG_FILE);
        } catch (Exception e) {
            LOGGER.error("Failed to load configuration: {}", e.getMessage(), e);
        }
    }
}
