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
import com.justsyncit.storage.metadata.FileMetadata;
import com.justsyncit.storage.metadata.MetadataService;
import com.justsyncit.storage.metadata.Snapshot;

import java.io.IOException;
import java.text.DecimalFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Command for showing detailed information about a specific snapshot.
 * Follows Single Responsibility Principle by handling only snapshot information display.
 */
public class SnapshotsInfoCommand implements Command {

    private final MetadataService metadataService;
    private final ServiceFactory serviceFactory;

    /**
     * Creates a snapshots info command with dependency injection.
     *
     * @param metadataService metadata service (may be null for lazy initialization)
     */
    public SnapshotsInfoCommand(MetadataService metadataService) {
        this.metadataService = metadataService;
        this.serviceFactory = new ServiceFactory();
    }

    @Override
    public String getName() {
        return "snapshots";
    }

    @Override
    public String getDescription() {
        return "Show detailed information about a specific snapshot";
    }

    @Override
    public String getUsage() {
        return "snapshots info <snapshot-id> [options]";
    }

    @Override
    public boolean execute(String[] args, CommandContext context) {
        // Handle help option first
        if (args.length == 1 && args[0].equals("--help")) {
            displayHelp();
            return true;
        }

        // Check for subcommand and snapshot ID
        if (args.length < 2 || !args[0].equals("info")) {
            System.err.println("Error: Missing subcommand 'info' or snapshot ID");
            System.err.println(getUsage());
            System.err.println("Use 'help snapshots info' for more information");
            return false;
        }

        String snapshotId = args[1];

        // Parse options
        boolean showFiles = false;
        int fileLimit = 20;
        boolean showStatistics = true;

        for (int i = 2; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--show-files":
                    showFiles = true;
                    break;
                case "--file-limit":
                    if (i + 1 < args.length) {
                        try {
                            fileLimit = Integer.parseInt(args[i + 1]);
                            if (fileLimit <= 0) {
                                System.err.println("Error: File limit must be positive");
                                return false;
                            }
                            i++; // Skip the next argument
                        } catch (NumberFormatException e) {
                            System.err.println("Error: Invalid file limit: " + args[i + 1]);
                            return false;
                        }
                    } else {
                        System.err.println("Error: --file-limit requires a value");
                        return false;
                    }
                    break;
                case "--no-statistics":
                    showStatistics = false;
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
                System.err.println("Error: Failed to initialize metadata service: " + e.getMessage());
                return false;
            }
        }

        try {
            // Get snapshot information
            Optional<Snapshot> snapshotOpt = service.getSnapshot(snapshotId);
            if (snapshotOpt.isEmpty()) {
                System.err.println("Error: Snapshot not found: " + snapshotId);
                return false;
            }

            Snapshot snapshot = snapshotOpt.get();

            // Display snapshot information
            System.out.println("Snapshot Information");
            System.out.println("====================");
            System.out.println();
            System.out.println("ID: " + snapshot.getId());
            System.out.println("Name: " + snapshot.getName());
            if (snapshot.getDescription() != null && !snapshot.getDescription().trim().isEmpty()) {
                System.out.println("Description: " + snapshot.getDescription());
            }
            
            ZonedDateTime zdt = ZonedDateTime.ofInstant(snapshot.getCreatedAt(), ZoneId.systemDefault());
            DateTimeFormatter formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.FULL);
            System.out.println("Created: " + zdt.format(formatter));
            
            System.out.println("Total Files: " + snapshot.getTotalFiles());
            System.out.println("Total Size: " + formatFileSize(snapshot.getTotalSize()));

            if (showStatistics) {
                System.out.println();
                System.out.println("Statistics");
                System.out.println("----------");
                
                // Get file statistics
                List<FileMetadata> files = service.getFilesInSnapshot(snapshotId);
                
                long totalRegularFiles = 0;
                long totalDirectories = 0;
                long totalSymlinks = 0;
                long totalSize = 0;
                long maxFileSize = 0;
                long minFileSize = Long.MAX_VALUE;
                String largestFile = "";
                String smallestFile = "";
                
                for (FileMetadata file : files) {
                    totalSize += file.getSize();
                    
                    // Since FileMetadata doesn't have isDirectory() or isSymbolicLink() methods,
                    // we'll treat all files as regular files for now
                    // In a real implementation, we might need to add file type information to FileMetadata
                    totalRegularFiles++;
                    if (file.getSize() > maxFileSize) {
                        maxFileSize = file.getSize();
                        largestFile = file.getPath();
                    }
                    if (file.getSize() < minFileSize && file.getSize() > 0) {
                        minFileSize = file.getSize();
                        smallestFile = file.getPath();
                    }
                }
                
                System.out.println("Regular Files: " + totalRegularFiles);
                System.out.println("Directories: " + totalDirectories);
                System.out.println("Symbolic Links: " + totalSymlinks);
                System.out.println("Average File Size: " + 
                    (totalRegularFiles > 0 ? formatFileSize(totalSize / totalRegularFiles) : "0 B"));
                
                if (!largestFile.isEmpty()) {
                    System.out.println("Largest File: " + largestFile + " (" + formatFileSize(maxFileSize) + ")");
                }
                if (minFileSize != Long.MAX_VALUE && !smallestFile.isEmpty()) {
                    System.out.println("Smallest File: " + smallestFile + " (" + formatFileSize(minFileSize) + ")");
                }
            }

            if (showFiles) {
                System.out.println();
                System.out.println("Files (showing first " + Math.min(fileLimit, service.getFilesInSnapshot(snapshotId).size()) + "):");
                System.out.println("------");
                
                List<FileMetadata> files = service.getFilesInSnapshot(snapshotId);
                int displayCount = Math.min(fileLimit, files.size());
                
                System.out.printf("%-50s %-8s %-12s %s%n", "Path", "Type", "Size", "Modified");
                System.out.println("-".repeat(100));
                
                DateTimeFormatter fileFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT);
                
                for (int i = 0; i < displayCount; i++) {
                    FileMetadata file = files.get(i);
                    String type = "FILE"; // Since FileMetadata doesn't have type info
                    String size = formatFileSize(file.getSize());
                    ZonedDateTime fileZdt = ZonedDateTime.ofInstant(file.getModifiedTime(), ZoneId.systemDefault());
                    
                    System.out.printf("%-50s %-8s %-12s %s%n",
                        truncateString(file.getPath(), 50),
                        type,
                        size,
                        fileZdt.format(fileFormatter));
                }
                
                if (files.size() > fileLimit) {
                    System.out.println();
                    System.out.println("... and " + (files.size() - fileLimit) + " more files");
                }
            }

        } catch (IOException e) {
            System.err.println("Error: Failed to get snapshot information: " + e.getMessage());
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
     * Displays detailed help information for the snapshots info command.
     */
    private void displayHelp() {
        System.out.println("Snapshots Info Command Help");
        System.out.println("===========================");
        System.out.println();
        System.out.println("Usage: " + getUsage());
        System.out.println();
        System.out.println("Description:");
        System.out.println("  " + getDescription());
        System.out.println();
        System.out.println("Arguments:");
        System.out.println("  snapshot-id    ID of the snapshot to inspect");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --show-files          Show list of files in the snapshot");
        System.out.println("  --file-limit NUM       Limit number of files to show (default: 20)");
        System.out.println("  --no-statistics        Don't show file statistics");
        System.out.println("  --help                 Show this help message");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  snapshots info abc123-def456");
        System.out.println("  snapshots info abc123-def456 --show-files");
        System.out.println("  snapshots info abc123-def456 --show-files --file-limit 50");
        System.out.println("  snapshots info abc123-def456 --no-statistics");
    }

    /**
     * Formats file size in human-readable format.
     *
     * @param bytes the size in bytes
     * @return formatted size string
     */
    private String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return new DecimalFormat("#,##0.#").format(bytes / 1024.0) + " KB";
        } else if (bytes < 1024 * 1024 * 1024) {
            return new DecimalFormat("#,##0.#").format(bytes / (1024.0 * 1024)) + " MB";
        } else {
            return new DecimalFormat("#,##0.#").format(bytes / (1024.0 * 1024 * 1024)) + " GB";
        }
    }

    /**
     * Truncates a string to the specified length, adding ellipsis if needed.
     *
     * @param str the string to truncate
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