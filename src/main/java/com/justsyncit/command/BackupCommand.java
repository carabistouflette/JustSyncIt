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
import com.justsyncit.backup.BackupOptions;
import com.justsyncit.backup.BackupService;
import com.justsyncit.backup.ConsoleBackupProgressTracker;
import com.justsyncit.hash.Blake3Service;
import com.justsyncit.scanner.SymlinkStrategy;
import com.justsyncit.storage.ContentStore;
import com.justsyncit.storage.metadata.MetadataService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;

/**
 * Command for backing up directories.
 * Follows Single Responsibility Principle by handling only backup operations.
 */
public class BackupCommand implements Command {

    private final BackupService backupService;
    private final ServiceFactory serviceFactory;

    /**
     * Creates a backup command with dependency injection.
     *
     * @param backupService backup service (may be null for lazy initialization)
     */
    public BackupCommand(BackupService backupService) {
        this.backupService = backupService;
        this.serviceFactory = new ServiceFactory();
    }

    @Override
    public String getName() {
        return "backup";
    }

    @Override
    public String getDescription() {
        return "Backup a directory to the content store";
    }

    @Override
    public String getUsage() {
        return "backup <source-dir> [options]";
    }

    @Override
    public boolean execute(String[] args, CommandContext context) {

        if (args.length == 0) {
            System.err.println("Error: Source directory is required");
            System.err.println(getUsage());
            System.err.println("Use 'help backup' for more information");
            return false;
        }

        String sourceDir = args[0];
        Path sourcePath = Paths.get(sourceDir);

        if (!Files.exists(sourcePath)) {
            System.err.println("Error: Source directory does not exist: " + sourceDir);
            return false;
        }

        if (!Files.isDirectory(sourcePath)) {
            System.err.println("Error: Source path is not a directory: " + sourceDir);
            return false;
        }

        // Parse options
        BackupOptions.Builder optionsBuilder = new BackupOptions.Builder();

        for (int i = 1; i < args.length; i++) {
            String arg = args[i];

            switch (arg) {
                case "--follow-symlinks":
                    optionsBuilder.symlinkStrategy(SymlinkStrategy.FOLLOW);
                    break;
                case "--skip-symlinks":
                    optionsBuilder.symlinkStrategy(SymlinkStrategy.SKIP);
                    break;
                case "--include-hidden":
                    optionsBuilder.includeHiddenFiles(true);
                    break;
                case "--no-verify":
                    optionsBuilder.verifyIntegrity(false);
                    break;
                case "--chunk-size":
                    if (i + 1 < args.length) {
                        try {
                            int chunkSize = Integer.parseInt(args[i + 1]);
                            optionsBuilder.chunkSize(chunkSize);
                            i++; // Skip the next argument
                        } catch (NumberFormatException e) {
                            System.err.println("Error: Invalid chunk size: " + args[i + 1]);
                            return false;
                        }
                    } else {
                        System.err.println("Error: --chunk-size requires a value");
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

        BackupOptions options = optionsBuilder.build();

        // Create services if not provided
        BackupService service = backupService;
        if (service == null) {
            try {
                Blake3Service blake3Service = serviceFactory.createBlake3Service();
                ContentStore contentStore = serviceFactory.createSqliteContentStore(blake3Service);
                MetadataService metadataService = serviceFactory.createMetadataService();
                service = serviceFactory.createBackupService(contentStore, metadataService, blake3Service);
            } catch (ServiceException e) {
                System.err.println("Error: Failed to initialize backup service: " + e.getMessage());
                return false;
            }
        }

        // Execute backup
        ConsoleBackupProgressTracker progressTracker = new ConsoleBackupProgressTracker();

        try {
            System.out.println("Starting backup of: " + sourceDir);
            System.out.println("Options: " + options);

            CompletableFuture<BackupService.BackupResult> backupFuture = service.backup(sourcePath, options);
            BackupService.BackupResult result = backupFuture.get();

            System.out.println("\nBackup completed successfully!");
            System.out.println("Files processed: " + result.getFilesProcessed());
            System.out.println("Total bytes: " + result.getTotalBytesProcessed());
            System.out.println("Chunks created: " + result.getChunksCreated());
            System.out.println("Integrity verified: " + result.isIntegrityVerified());

        } catch (Exception e) {
            System.err.println("\nBackup failed: " + e.getMessage());
            if (e.getCause() != null) {
                System.err.println("Cause: " + e.getCause().getMessage());
            }
            return false;
        }

        return true;
    }

    /**
     * Displays detailed help information for the backup command.
     */
    private void displayHelp() {
        System.out.println("Backup Command Help");
        System.out.println("==================");
        System.out.println();
        System.out.println("Usage: " + getUsage());
        System.out.println();
        System.out.println("Description:");
        System.out.println("  " + getDescription());
        System.out.println();
        System.out.println("Arguments:");
        System.out.println("  source-dir    Path to the directory to backup");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --follow-symlinks    Follow symbolic links instead of preserving them");
        System.out.println("  --skip-symlinks      Skip symbolic links entirely");
        System.out.println("  --include-hidden     Include hidden files and directories");
        System.out.println("  --no-verify          Skip integrity verification after backup");
        System.out.println("  --chunk-size SIZE    Set chunk size in bytes (default: 64KB)");
        System.out.println("  --help               Show this help message");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  backup /home/user/documents");
        System.out.println("  backup /home/user/documents --include-hidden --chunk-size 1048576");
        System.out.println("  backup /home/user/documents --follow-symlinks --no-verify");
    }
}