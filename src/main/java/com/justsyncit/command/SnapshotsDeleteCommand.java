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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;

/**
 * Command for deleting snapshots.
 * Follows Single Responsibility Principle by handling only snapshot deletion
 * operations.
 */

public class SnapshotsDeleteCommand implements Command {

    private static final long KB = 1024;
    private static final long MB = KB * 1024;
    private static final long GB = MB * 1024;

    private final MetadataService metadataService;
    private final ServiceFactory serviceFactory;

    /**
     * Creates a snapshots delete command with dependency injection.
     *
     * @param metadataService metadata service (may be null for lazy initialization)
     */
    public SnapshotsDeleteCommand(MetadataService metadataService) {
        this.metadataService = metadataService;
        this.serviceFactory = new ServiceFactory();
    }

    @Override
    public String getName() {
        return "snapshots";
    }

    @Override
    public String getDescription() {
        return "Delete a specific snapshot";
    }

    @Override
    public String getUsage() {
        return "snapshots delete <snapshot-id> [options]";
    }

    @Override
    public boolean execute(String[] args, CommandContext context) {
        // Handle help option first
        if (isHelpRequested(args)) {
            displayHelp();
            return true;
        }

        // Check for subcommand and snapshot ID
        if (args.length < 2 || !args[0].equals("delete")) {
            System.err.println("Error: Missing subcommand 'delete' or snapshot ID");
            System.err.println(getUsage());
            System.err.println("Use 'help snapshots delete' for more information");
            return false;
        }

        String snapshotId = args[1];

        DeleteOptions options;
        try {
            options = parseOptions(args);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            return false;
        }

        if (options == null) {
            return false; // Help was displayed
        }

        MetadataService localService = null;

        try {
            MetadataService service = this.metadataService;
            if (service == null) {
                try {
                    localService = serviceFactory.createMetadataService();
                    service = localService;
                } catch (ServiceException e) {
                    System.err.println("Error: Failed to initialize metadata service: " + e.getMessage());
                    return false;
                }
            }

            return deleteSnapshot(service, snapshotId, options);

        } catch (IOException e) {
            System.err.println("Error: Failed to delete snapshot: " + e.getMessage());
            return false;
        } finally {
            closeQuietly(localService);
        }
    }

    /**
     * Parses command-line options.
     *
     * @param args command-line arguments
     * @return parsed options, or null if help was displayed
     * @throws IllegalArgumentException if invalid options are provided
     */
    private DeleteOptions parseOptions(String[] args) {
        boolean confirm = true;

        for (int i = 2; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--force":
                case "-f":
                case "--no-confirm":
                    confirm = false;
                    break;
                case "--help":
                    displayHelp();
                    return null;
                default:
                    if (arg.startsWith("--")) {
                        throw new IllegalArgumentException("Error: Unknown option: " + arg);
                    }
                    break;
            }
        }

        return new DeleteOptions(confirm);
    }

    /**
     * Deletes a snapshot with the given options.
     *
     * @param service    metadata service
     * @param snapshotId snapshot ID to delete
     * @param options    delete options
     * @return true if successful
     * @throws IOException if deletion fails
     */
    private boolean deleteSnapshot(MetadataService service, String snapshotId, DeleteOptions options)
            throws IOException {
        // Get snapshot information
        Optional<Snapshot> snapshotOpt = service.getSnapshot(snapshotId);
        if (snapshotOpt.isEmpty()) {
            System.err.println("Error: Snapshot not found: " + snapshotId);
            return false;
        }

        Snapshot snapshot = snapshotOpt.get();

        // Display snapshot information
        displaySnapshotInfo(snapshot);

        // Show deletion warning
        displayDeletionWarning();

        // Confirmation prompt
        if (options.confirm && !confirmDeletion()) {
            System.out.println("Snapshot deletion cancelled.");
            return true;
        }

        // Delete the snapshot
        System.out.println("Deleting snapshot...");
        service.deleteSnapshot(snapshotId);

        System.out.println("Snapshot deleted successfully: " + snapshotId);

        // Show updated statistics
        List<Snapshot> remainingSnapshots = service.listSnapshots();
        System.out.println("Remaining snapshots: " + remainingSnapshots.size());

        return true;
    }

    /**
     * Displays snapshot information.
     *
     * @param snapshot the snapshot to display
     */
    private void displaySnapshotInfo(Snapshot snapshot) {
        System.out.println("Snapshot to Delete:");
        System.out.println("====================");
        System.out.println("ID: " + snapshot.getId());
        System.out.println("Name: " + snapshot.getName());
        if (snapshot.getDescription() != null && !snapshot.getDescription().trim().isEmpty()) {
            System.out.println("Description: " + snapshot.getDescription());
        }
        System.out.println("Created: " + snapshot.getCreatedAt());
        System.out.println("Total Files: " + snapshot.getTotalFiles());
        System.out.println("Total Size: " + formatFileSize(snapshot.getTotalSize()));
        System.out.println();
    }

    /**
     * Displays deletion warning.
     */
    private void displayDeletionWarning() {
        System.out.println("Note: This will permanently delete the snapshot and all its file metadata.");
        System.out.println("The actual file chunks will remain in storage if referenced by other snapshots.");
        System.out.println();
    }

    /**
     * Prompts for deletion confirmation.
     *
     * @return true if user confirms
     */
    private boolean confirmDeletion() {
        try (Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8.name())) {
            System.out.print("Are you sure you want to delete this snapshot? (y/N): ");
            String response = scanner.nextLine().trim().toLowerCase(java.util.Locale.ROOT);
            return response.equals("y") || response.equals("yes");
        }
    }

    /**
     * Safely closes a resource.
     *
     * @param resource resource to close
     */
    private void closeQuietly(AutoCloseable resource) {
        if (resource != null) {
            try {
                resource.close();
            } catch (Exception e) {
                System.err.println("Warning: Failed to close resource: " + e.getMessage());
            }
        }
    }

    /**
     * Displays detailed help information for the snapshots delete command.
     */
    private void displayHelp() {
        System.out.println("Snapshots Delete Command Help");
        System.out.println("===============================");
        System.out.println();
        System.out.println("Usage: " + getUsage());
        System.out.println();
        System.out.println("Description:");
        System.out.println("  " + getDescription());
        System.out.println();
        System.out.println("Arguments:");
        System.out.println("  snapshot-id    ID of the snapshot to delete");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --force, -f           Delete without confirmation");
        System.out.println("  --no-confirm          Skip confirmation prompt");
        System.out.println("  --help                Show this help message");
        System.out.println();
        System.out.println("Notes:");
        System.out.println("  - This operation is irreversible");
        System.out.println("  - File chunks are only deleted if not referenced by other snapshots");
        System.out.println("  - Use with caution as deleted snapshots cannot be recovered");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  snapshots delete abc123-def456");
        System.out.println("  snapshots delete abc123-def456 --force");
        System.out.println("  snapshots delete abc123-def456 --no-confirm");
    }

    /**
     * Formats file size in human-readable format.
     *
     * @param bytes the size in bytes
     * @return formatted size string
     */
    private String formatFileSize(long bytes) {
        if (bytes < KB) {
            return bytes + " B";
        } else if (bytes < MB) {
            return formatWithDecimal(bytes / (double) KB) + " KB";
        } else if (bytes < GB) {
            return formatWithDecimal(bytes / (double) MB) + " MB";
        } else {
            return formatWithDecimal(bytes / (double) GB) + " GB";
        }
    }

    /**
     * Formats a number with one decimal place.
     *
     * @param value the value to format
     * @return formatted string
     */
    private String formatWithDecimal(double value) {
        return new java.text.DecimalFormat("#,##0.#").format(value);
    }

    /**
     * Simple data class to hold delete options.
     */
    private static class DeleteOptions {
        final boolean confirm;

        DeleteOptions(boolean confirm) {
            this.confirm = confirm;
        }
    }
}