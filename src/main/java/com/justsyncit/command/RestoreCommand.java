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
import com.justsyncit.restore.RestoreOptions;
import com.justsyncit.restore.RestoreService;
import com.justsyncit.restore.ConsoleRestoreProgressTracker;
import com.justsyncit.hash.Blake3Service;
import com.justsyncit.storage.ContentStore;
import com.justsyncit.storage.metadata.MetadataService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;

/**
 * Command for restoring directories from snapshots.
 * Follows Single Responsibility Principle by handling only restore operations.
 */
public class RestoreCommand implements Command {

    private final RestoreService restoreService;
    private final ServiceFactory serviceFactory;

    /**
     * Creates a restore command with dependency injection.
     *
     * @param restoreService restore service (may be null for lazy initialization)
     */
    public RestoreCommand(RestoreService restoreService) {
        this.restoreService = restoreService;
        this.serviceFactory = new ServiceFactory();
    }

    @Override
    public String getName() {
        return "restore";
    }

    @Override
    public String getDescription() {
        return "Restore a directory from a snapshot";
    }

    @Override
    public String getUsage() {
        return "restore <snapshot-id> <target-dir> [options]";
    }

    @Override
    public boolean execute(String[] args, CommandContext context) {

        // Handle help option first
        if (args.length == 1 && args[0].equals("--help")) {
            displayHelp();
            return true;
        }

        // Handle case where --help is the second argument
        if (args.length >= 2 && args[1].equals("--help")) {
            displayHelp();
            return true;
        }

        if (args.length < 2) {
            System.err.println("Error: Snapshot ID and target directory are required");
            System.err.println(getUsage());
            System.err.println("Use 'help restore' for more information");
            return false;
        }

        String snapshotId = args[0];
        String targetDir = args[1];
        Path targetPath = Paths.get(targetDir);

        // Validate target directory
        if (Files.exists(targetPath) && !Files.isDirectory(targetPath)) {
            System.err.println("Error: Target path exists but is not a directory: " + targetDir);
            return false;
        }

        // Parse options
        RestoreOptions.Builder optionsBuilder = new RestoreOptions.Builder();

        for (int i = 2; i < args.length; i++) {
            String arg = args[i];

            switch (arg) {
                case "--overwrite":
                    optionsBuilder.overwriteExisting(true);
                    break;
                case "--backup-existing":
                    optionsBuilder.backupExisting(true);
                    break;
                case "--no-verify":
                    optionsBuilder.verifyIntegrity(false);
                    break;
                case "--no-preserve-attributes":
                    optionsBuilder.preserveAttributes(false);
                    break;
                case "--include":
                    if (i + 1 < args.length) {
                        optionsBuilder.includePattern(args[i + 1]);
                        i++; // Skip the next argument
                    } else {
                        System.err.println("Error: --include requires a pattern");
                        return false;
                    }
                    break;
                case "--exclude":
                    if (i + 1 < args.length) {
                        optionsBuilder.excludePattern(args[i + 1]);
                        i++; // Skip the next argument
                    } else {
                        System.err.println("Error: --exclude requires a pattern");
                        return false;
                    }
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

        RestoreOptions options = optionsBuilder.build();

        // Create services if not provided
        RestoreService service = restoreService;
        ContentStore contentStore = null;
        MetadataService metadataService = null;

        if (service == null) {
            try {
                Blake3Service blake3Service = serviceFactory.createBlake3Service();
                contentStore = serviceFactory.createSqliteContentStore(blake3Service);
                metadataService = serviceFactory.createMetadataService();
                service = serviceFactory.createRestoreService(contentStore, metadataService, blake3Service);
            } catch (ServiceException e) {
                System.err.println("Error: Failed to initialize restore service: " + e.getMessage());
                return false;
            }
        }

        // Execute restore
        ConsoleRestoreProgressTracker progressTracker = new ConsoleRestoreProgressTracker();

        try {
            System.out.println("Starting restore of snapshot: " + snapshotId);
            System.out.println("Target directory: " + targetDir);
            System.out.println("Options: " + options);

            CompletableFuture<RestoreService.RestoreResult> restoreFuture = service.restore(snapshotId, targetPath, options);
            RestoreService.RestoreResult result = restoreFuture.get();

            System.out.println("\nRestore completed successfully!");
            System.out.println("Files restored: " + result.getFilesRestored());
            System.out.println("Files skipped: " + result.getFilesSkipped());
            System.out.println("Files with errors: " + result.getFilesWithErrors());
            System.out.println("Total bytes: " + result.getTotalBytesRestored());
            System.out.println("Integrity verified: " + result.isIntegrityVerified());

        } catch (Exception e) {
            System.err.println("\nRestore failed: " + e.getMessage());
            if (e.getCause() != null) {
                System.err.println("Cause: " + e.getCause().getMessage());
            }
            return false;
        } finally {
            // Clean up resources if we created them
            if (contentStore != null) {
                try {
                    contentStore.close();
                } catch (Exception e) {
                    System.err.println("Warning: Failed to close content store: " + e.getMessage());
                }
            }
            if (metadataService != null) {
                try {
                    metadataService.close();
                } catch (Exception e) {
                    System.err.println("Warning: Failed to close metadata service: " + e.getMessage());
                }
            }
        }

        // Force JVM exit to prevent hanging due to background threads
        System.exit(0);
        return true; // This line won't be reached but keeps compiler happy
    }

    /**
     * Displays detailed help information for the restore command.
     */
    private void displayHelp() {
        System.out.println("Restore Command Help");
        System.out.println("===================");
        System.out.println();
        System.out.println("Usage: " + getUsage());
        System.out.println();
        System.out.println("Description:");
        System.out.println("  " + getDescription());
        System.out.println();
        System.out.println("Arguments:");
        System.out.println("  snapshot-id    ID of the snapshot to restore");
        System.out.println("  target-dir     Directory to restore files to");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --overwrite             Overwrite existing files");
        System.out.println("  --backup-existing       Backup existing files before overwriting");
        System.out.println("  --no-verify             Skip integrity verification after restore");
        System.out.println("  --no-preserve-attributes Don't preserve file attributes");
        System.out.println("  --include PATTERN       Only restore files matching pattern");
        System.out.println("  --exclude PATTERN       Skip files matching pattern");
        System.out.println("  --help                  Show this help message");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  restore abc123-def456 /home/user/restore");
        System.out.println("  restore abc123-def456 /home/user/restore --overwrite");
        System.out.println("  restore abc123-def456 /home/user/restore --include \"*.txt\" --exclude \"*.tmp\"");
    }
}