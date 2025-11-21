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
import java.util.List;
import java.util.Optional;
import java.util.Scanner;

/**
 * Command for deleting snapshots.
 * Follows Single Responsibility Principle by handling only snapshot deletion operations.
 */
public class SnapshotsDeleteCommand implements Command {

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
        if (args.length == 1 && args[0].equals("--help")) {
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

        // Parse options
        boolean force = false;
        boolean confirm = true;

        for (int i = 2; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--force":
                case "-f":
                    force = true;
                    confirm = false;
                    break;
                case "--no-confirm":
                    confirm = false;
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

            // Check for dependent snapshots or references if needed
            List<Snapshot> allSnapshots = service.listSnapshots();
            System.out.println("Note: This will permanently delete the snapshot and all its file metadata.");
            System.out.println("The actual file chunks will remain in storage if referenced by other snapshots.");
            System.out.println();

            // Confirmation prompt
            if (confirm && !force) {
                Scanner scanner = new Scanner(System.in);
                System.out.print("Are you sure you want to delete this snapshot? (y/N): ");
                String response = scanner.nextLine().trim().toLowerCase();
                
                if (!response.equals("y") && !response.equals("yes")) {
                    System.out.println("Snapshot deletion cancelled.");
                    return true;
                }
            }

            // Delete the snapshot
            System.out.println("Deleting snapshot...");
            service.deleteSnapshot(snapshotId);
            
            System.out.println("Snapshot deleted successfully: " + snapshotId);

            // Show updated statistics
            List<Snapshot> remainingSnapshots = service.listSnapshots();
            System.out.println("Remaining snapshots: " + remainingSnapshots.size());

        } catch (IOException e) {
            System.err.println("Error: Failed to delete snapshot: " + e.getMessage());
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
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return new java.text.DecimalFormat("#,##0.#").format(bytes / 1024.0) + " KB";
        } else if (bytes < 1024 * 1024 * 1024) {
            return new java.text.DecimalFormat("#,##0.#").format(bytes / (1024.0 * 1024)) + " MB";
        } else {
            return new java.text.DecimalFormat("#,##0.#").format(bytes / (1024.0 * 1024 * 1024)) + " GB";
        }
    }
}