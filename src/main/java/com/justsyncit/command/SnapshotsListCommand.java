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

import com.justsyncit.ServiceException;
import com.justsyncit.ServiceFactory;
import com.justsyncit.storage.metadata.MetadataService;
import com.justsyncit.storage.metadata.Snapshot;

import java.text.DecimalFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.ZonedDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Command for listing all available snapshots.
 * Follows Single Responsibility Principle by handling only snapshot listing
 * operations.
 */

public class SnapshotsListCommand implements Command {

    private static final Logger logger = LoggerFactory.getLogger(SnapshotsListCommand.class);

    private final MetadataService metadataService;
    private final ServiceFactory serviceFactory;

    /**
     * Creates a snapshots list command with dependency injection.
     *
     * @param metadataService metadata service (may be null for lazy initialization)
     */
    public SnapshotsListCommand(MetadataService metadataService) {
        this.metadataService = metadataService;
        this.serviceFactory = new ServiceFactory();
    }

    @Override
    public String getName() {
        return "snapshots";
    }

    @Override
    public String getDescription() {
        return "List all available snapshots";
    }

    @Override
    public String getUsage() {
        return "snapshots list [options]";
    }

    @Override
    public boolean execute(String[] args, CommandContext context) {
        // Handle help option first
        if (args.length == 1 && args[0].equals("--help")) {
            displayHelp();
            return true;
        }

        // Check for subcommand - when called from SnapshotsCommandGroup, 'list' is
        // already parsed
        // so we don't need to check args[0] again. Just proceed with option parsing.

        // Parse options
        boolean verbose = false;
        boolean sortBySize = false;
        boolean sortByDate = true; // Default sort by date

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--verbose":
                case "-v":
                    verbose = true;
                    break;
                case "--sort-by-size":
                    sortBySize = true;
                    sortByDate = false;
                    break;
                case "--sort-by-date":
                    sortByDate = true;
                    sortBySize = false;
                    break;
                case "--help":
                    displayHelp();
                    return true;
                default:
                    if (arg.startsWith("--")) {
                        System.err.println("Error: Unknown option: " + arg);
                        return false;
                    }
                    break;
            }
        }

        // Create service if not provided
        MetadataService service = metadataService;
        if (service == null) {
            try {
                service = serviceFactory.createMetadataService();
            } catch (ServiceException e) {
                handleError("Failed to initialize metadata service", e, logger);
                return false;
            }
        }

        try {
            List<Snapshot> snapshots = service.listSnapshots();

            if (snapshots.isEmpty()) {
                System.out.println("No snapshots found.");
                return true;
            }

            // Sort snapshots if requested
            if (sortBySize) {
                snapshots.sort((s1, s2) -> Long.compare(s2.getTotalSize(), s1.getTotalSize()));
            } else if (sortByDate) {
                snapshots.sort((s1, s2) -> s2.getCreatedAt().compareTo(s1.getCreatedAt()));
            }

            // Display snapshots
            System.out.println("Available Snapshots:");
            System.out.println("===================");
            System.out.println();

            if (verbose) {
                // Verbose output with all details
                System.out.printf("%-20s %-20s %-15s %-12s %-12s %s%n",
                        "ID", "Name", "Created", "Files", "Size", "Description");
                System.out.println("-".repeat(100));

                DateTimeFormatter formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT);
                DecimalFormat sizeFormat = new DecimalFormat("#,##0.#");

                for (Snapshot snapshot : snapshots) {
                    String sizeStr = formatFileSize(snapshot.getTotalSize(), sizeFormat);
                    ZonedDateTime zdt = ZonedDateTime.ofInstant(snapshot.getCreatedAt(), ZoneId.systemDefault());
                    System.out.printf("%-20s %-20s %-15s %-12d %-12s %s%n",
                            truncateString(snapshot.getId(), 20),
                            truncateString(snapshot.getName(), 20),
                            zdt.format(formatter),
                            snapshot.getTotalFiles(),
                            sizeStr,
                            snapshot.getDescription() != null ? snapshot.getDescription() : "");
                }
            } else {
                // Compact output
                System.out.printf("%-12s %-20s %-15s %-10s %s%n",
                        "ID", "Name", "Created", "Files", "Size");
                System.out.println("-".repeat(80));

                DateTimeFormatter formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT);

                for (Snapshot snapshot : snapshots) {
                    String sizeStr = formatFileSize(snapshot.getTotalSize());
                    ZonedDateTime zdt = ZonedDateTime.ofInstant(snapshot.getCreatedAt(), ZoneId.systemDefault());
                    System.out.printf("%-12s %-20s %-15s %-10d %s%n",
                            truncateString(snapshot.getId(), 12),
                            truncateString(snapshot.getName(), 20),
                            zdt.format(formatter),
                            snapshot.getTotalFiles(),
                            sizeStr);
                }
            }

            System.out.println();
            System.out.println("Total: " + snapshots.size() + " snapshot(s)");

            // Calculate total statistics
            long totalFiles = snapshots.stream().mapToLong(Snapshot::getTotalFiles).sum();
            long totalSize = snapshots.stream().mapToLong(Snapshot::getTotalSize).sum();

            System.out.println("Total files across all snapshots: " + totalFiles);
            System.out.println("Total size across all snapshots: " + formatFileSize(totalSize));

        } catch (Exception e) {
            handleError("Failed to list snapshots", e, logger);
            return false;
        } finally {
            // Clean up resources if we created them
            if (metadataService == null && service != null) {
                try {
                    service.close();
                } catch (Exception e) {
                    System.err.println("Warning: Failed to close metadata service: " + e.getMessage());
                }
            }
        }

        return true;
    }

    /**
     * Displays detailed help information for the snapshots list command.
     */
    private void displayHelp() {
        System.out.println("Snapshots List Command Help");
        System.out.println("===========================");
        System.out.println();
        System.out.println("Usage: " + getUsage());
        System.out.println();
        System.out.println("Description:");
        System.out.println("  " + getDescription());
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --verbose, -v         Show detailed information including descriptions");
        System.out.println("  --sort-by-size        Sort snapshots by size (largest first)");
        System.out.println("  --sort-by-date        Sort snapshots by creation date (newest first, default)");
        System.out.println("  --help                Show this help message");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  snapshots list");
        System.out.println("  snapshots list --verbose");
        System.out.println("  snapshots list --sort-by-size");
        System.out.println("  snapshots list --verbose --sort-by-size");
    }

    /**
     * Formats file size in human-readable format.
     *
     * @param bytes the size in bytes
     * @return formatted size string
     */
    private String formatFileSize(long bytes) {
        return formatFileSize(bytes, new DecimalFormat("#,##0.#"));
    }

    /**
     * Formats file size in human-readable format using the specified formatter.
     *
     * @param bytes     the size in bytes
     * @param formatter the decimal formatter to use
     * @return formatted size string
     */
    private String formatFileSize(long bytes, DecimalFormat formatter) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return formatter.format(bytes / 1024.0) + " KB";
        } else if (bytes < 1024 * 1024 * 1024) {
            return formatter.format(bytes / (1024.0 * 1024)) + " MB";
        } else {
            return formatter.format(bytes / (1024.0 * 1024 * 1024)) + " GB";
        }
    }

    /**
     * Truncates a string to the specified length, adding ellipsis if needed.
     *
     * @param str       the string to truncate
     * @param maxLength the maximum length
     * @return truncated string
     */
    private String truncateString(String str, int maxLength) {
        if (str == null) {
            return "";
        }
        if (str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength - 3) + "...";
    }
}