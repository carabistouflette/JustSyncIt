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

package com.justsyncit.command;

import com.justsyncit.storage.metadata.FileMetadata;
import com.justsyncit.storage.metadata.MetadataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Command to search for files in the backup metadata.
 * Uses the FTS5 full-text search capability.
 */
public class SearchCommand implements Command {

    private static final Logger logger = LoggerFactory.getLogger(SearchCommand.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    @Override
    public boolean execute(String[] args, CommandContext context) {
        if (isHelpRequested(args)) {
            System.out.println(getUsage());
            return true;
        }

        if (!validateMinArgs(args, 1, "Missing search query")) {
            return false;
        }

        MetadataService metadataService = context.getMetadataService();
        if (metadataService == null) {
            System.err.println("Error: Metadata service is not available.");
            return false;
        }

        String query = args[0];

        // Combine multiple args into a single query if needed
        if (args.length > 1) {
            query = String.join(" ", args);
        }

        try {
            System.out.println("Searching for: " + query);
            List<FileMetadata> results = metadataService.searchFiles(query);

            if (results.isEmpty()) {
                System.out.println("No files found matching query: " + query);
            } else {
                System.out.println("Found " + results.size() + " matching files:");
                System.out.println("--------------------------------------------------------------------------------");
                System.out.printf("%-20s %-12s %-50s%n", "Modified", "Size", "Path");
                System.out.println("--------------------------------------------------------------------------------");

                for (FileMetadata file : results) {
                    System.out.printf("%-20s %-12s %-50s%n",
                            DATE_FORMATTER.format(file.getModifiedTime()),
                            humanReadableByteCount(file.getSize()),
                            file.getPath());
                }
                System.out.println("--------------------------------------------------------------------------------");
            }
            return true;

        } catch (Exception e) {
            handleError("Search failed", e, logger);
            return false;
        }
    }

    @Override
    public String getName() {
        return "search";
    }

    @Override
    public String getDescription() {
        return "Search for files in backup metadata using full-text search";
    }

    @Override
    public String getUsage() {
        return "Usage: justsyncit search <query>\n" +
                "  <query>   Search query (supports simple text or FTS syntax)";
    }

    private String humanReadableByteCount(long bytes) {
        if (bytes < 1024)
            return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
}
