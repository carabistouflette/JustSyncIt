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

import com.justsyncit.web.dto.ApiError;
import com.justsyncit.web.dto.FileEntry;

import io.javalin.http.Context;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.stream.Stream;

/**
 * REST controller for file system browsing.
 */
public final class FileBrowserController {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileBrowserController.class);
    private static final int MAX_RESULTS = 1000;

    private static final java.util.Set<String> FORBIDDEN_NAMES = java.util.Set.of(
            ".ssh", ".aws", ".gnupg", ".kube", ".netrc", "credentials");

    public FileBrowserController() {
        // No dependencies needed
    }

    /**
     * GET /api/files - Browse directory contents.
     */
    public void browse(Context ctx) {
        try {
            String pathParam = ctx.queryParam("path");
            boolean showHidden = ctx.queryParamAsClass("showHidden", Boolean.class).getOrDefault(false);

            if (pathParam == null || pathParam.isEmpty()) {
                pathParam = System.getProperty("user.home");
            }

            Path path = Paths.get(pathParam).toAbsolutePath().normalize();

            // Security check - don't allow browsing sensitive directories
            if (!isPathAllowed(path)) {
                ctx.status(403).json(ApiError.of(403, "Forbidden",
                        "Access to this path is not allowed", ctx.path()));
                return;
            }

            if (!Files.exists(path)) {
                ctx.status(404).json(ApiError.notFound("Path not found: " + path, ctx.path()));
                return;
            }

            if (!Files.isDirectory(path)) {
                ctx.status(400).json(ApiError.badRequest("Path is not a directory: " + path, ctx.path()));
                return;
            }

            List<FileEntry> entries = new ArrayList<>();

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
                for (Path entry : stream) {
                    try {
                        BasicFileAttributes attrs = Files.readAttributes(entry, BasicFileAttributes.class);

                        // Skip hidden files if not requested
                        boolean hidden = Files.isHidden(entry);
                        if (hidden && !showHidden) {
                            continue;
                        }

                        FileEntry fileEntry = new FileEntry();
                        fileEntry.setName(entry.getFileName().toString());
                        fileEntry.setPath(entry.toString());
                        fileEntry.setDirectory(attrs.isDirectory());
                        fileEntry.setSize(attrs.isRegularFile() ? attrs.size() : 0);
                        fileEntry.setModifiedAt(Instant.ofEpochMilli(attrs.lastModifiedTime().toMillis()));
                        fileEntry.setHidden(hidden);
                        fileEntry.setSymlink(Files.isSymbolicLink(entry));

                        entries.add(fileEntry);
                    } catch (IOException e) {
                        // Skip files we can't read
                        LOGGER.debug("Cannot read file attributes: {}", entry);
                    }
                }
            }

            // Sort: directories first, then alphabetically
            entries.sort(Comparator
                    .comparing(FileEntry::isDirectory).reversed()
                    .thenComparing(FileEntry::getName, String.CASE_INSENSITIVE_ORDER));

            // Build response - use HashMap because Map.of doesn't allow null values
            java.util.Map<String, Object> response = new java.util.HashMap<>();
            response.put("path", path.toString());
            response.put("parent", path.getParent() != null ? path.getParent().toString() : null);
            response.put("entries", entries);
            response.put("count", entries.size());
            ctx.json(response);

        } catch (Exception e) {
            LOGGER.error("Failed to browse directory", e);
            ctx.status(500).json(ApiError.internalError(e.getMessage(), ctx.path()));
        }
    }

    /**
     * GET /api/files/search - Search files by name.
     */
    public void search(Context ctx) {
        try {
            String basePath = ctx.queryParam("path");
            String pattern = ctx.queryParam("pattern");
            int maxDepth = ctx.queryParamAsClass("maxDepth", Integer.class).getOrDefault(10);
            int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(100);

            if (basePath == null || basePath.isEmpty()) {
                basePath = System.getProperty("user.home");
            }

            if (pattern == null || pattern.isEmpty()) {
                ctx.status(400).json(ApiError.badRequest("Search pattern is required", ctx.path()));
                return;
            }

            Path searchPath = Paths.get(basePath).toAbsolutePath().normalize();

            if (!isPathAllowed(searchPath)) {
                ctx.status(403).json(ApiError.of(403, "Forbidden",
                        "Access to this path is not allowed", ctx.path()));
                return;
            }

            if (!Files.exists(searchPath) || !Files.isDirectory(searchPath)) {
                ctx.status(404).json(ApiError.notFound("Directory not found: " + searchPath, ctx.path()));
                return;
            }

            String searchPattern = pattern.toLowerCase();
            List<FileEntry> results = new ArrayList<>();

            try (Stream<Path> walk = Files.walk(searchPath, maxDepth)) {
                walk.filter(p -> p.getFileName().toString().toLowerCase().contains(searchPattern))
                        .limit(Math.min(limit, MAX_RESULTS))
                        .forEach(p -> {
                            try {
                                BasicFileAttributes attrs = Files.readAttributes(p, BasicFileAttributes.class);
                                FileEntry entry = new FileEntry();
                                entry.setName(p.getFileName().toString());
                                entry.setPath(p.toString());
                                entry.setDirectory(attrs.isDirectory());
                                entry.setSize(attrs.isRegularFile() ? attrs.size() : 0);
                                entry.setModifiedAt(Instant.ofEpochMilli(attrs.lastModifiedTime().toMillis()));
                                results.add(entry);
                            } catch (IOException e) {
                                // Skip files we can't read
                            }
                        });
            }

            ctx.json(Map.of(
                    "basePath", searchPath.toString(),
                    "pattern", pattern,
                    "results", results,
                    "count", results.size()));

        } catch (Exception e) {
            LOGGER.error("Failed to search files", e);
            ctx.status(500).json(ApiError.internalError(e.getMessage(), ctx.path()));
        }
    }

    private boolean isPathAllowed(Path path) {
        String pathStr = path.toString();

        // Block only truly sensitive virtual filesystems
        if (pathStr.startsWith("/proc") || pathStr.startsWith("/sys") || pathStr.startsWith("/dev")
                || pathStr.startsWith("/run")) {
            return false;
        }

        // Block sensitive directories
        for (Path part : path) {
            if (FORBIDDEN_NAMES.contains(part.toString())) {
                LOGGER.warn("Access denied to sensitive path: {}", path);
                return false;
            }
        }

        // Allow root and all other paths for browsing
        return true;
    }
}
