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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * REST controller for configuration management.
 */
public final class ConfigController {

    private static final Logger LOGGER = Logger.getLogger(ConfigController.class.getName());

    private final WebServerContext context;

    // In-memory config storage (would typically persist to file)
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
    @SuppressWarnings("unchecked")
    public void updateConfig(Context ctx) {
        try {
            Map<String, Object> updates = ctx.bodyAsClass(Map.class);

            // Validate and apply updates
            for (Map.Entry<String, Object> entry : updates.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();

                // Validate specific config keys
                switch (key) {
                    case "webPort":
                        if (value instanceof Number) {
                            int port = ((Number) value).intValue();
                            if (port < 1 || port > 65535) {
                                ctx.status(400).json(ApiError.badRequest(
                                        "webPort must be between 1 and 65535", ctx.path()));
                                return;
                            }
                        }
                        break;
                    case "defaultChunkSize":
                        if (value instanceof Number) {
                            long size = ((Number) value).longValue();
                            if (size < 1024 || size > 64 * 1024 * 1024) {
                                ctx.status(400).json(ApiError.badRequest(
                                        "defaultChunkSize must be between 1KB and 64MB", ctx.path()));
                                return;
                            }
                        }
                        break;
                    case "compressionLevel":
                        if (value instanceof Number) {
                            int level = ((Number) value).intValue();
                            if (level < 1 || level > 22) {
                                ctx.status(400).json(ApiError.badRequest(
                                        "compressionLevel must be between 1 and 22", ctx.path()));
                                return;
                            }
                        }
                        break;
                    default:
                        // Allow other keys
                        break;
                }

                config.put(key, value);
            }

            LOGGER.info("Configuration updated: " + updates.keySet());
            ctx.json(Map.of("status", "updated", "config", config));

        } catch (Exception e) {
            LOGGER.severe("Failed to update config: " + e.getMessage());
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
    @SuppressWarnings("unchecked")
    public void addBackupSource(Context ctx) {
        try {
            Map<String, String> body = ctx.bodyAsClass(Map.class);
            String path = body.get("path");

            if (path == null || path.isEmpty()) {
                ctx.status(400).json(ApiError.badRequest("path is required", ctx.path()));
                return;
            }

            if (backupSources.contains(path)) {
                ctx.status(409).json(ApiError.of(409, "Conflict",
                        "Backup source already exists: " + path, ctx.path()));
                return;
            }

            backupSources.add(path);
            LOGGER.info("Added backup source: " + path);

            ctx.status(201).json(Map.of(
                    "status", "created",
                    "path", path,
                    "sources", backupSources));

        } catch (Exception e) {
            LOGGER.severe("Failed to add backup source: " + e.getMessage());
            ctx.status(500).json(ApiError.internalError(e.getMessage(), ctx.path()));
        }
    }
}
