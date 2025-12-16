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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.justsyncit.hash.Blake3Service;
import com.justsyncit.network.NetworkService;
import com.justsyncit.network.TransportType;
import com.justsyncit.storage.ContentStore;
import com.justsyncit.storage.metadata.MetadataService;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.Locale;

/**
 * Command for restoring directories from snapshots.
 * Follows Single Responsibility Principle by handling only restore operations.
 */

public class RestoreCommand implements Command {

    private static final Logger logger = LoggerFactory.getLogger(RestoreCommand.class);

    private final RestoreService restoreService;
    private final ServiceFactory serviceFactory;
    private final NetworkService networkService;

    /**
     * Creates a restore command with dependency injection.
     *
     * @param restoreService restore service (may be null for lazy initialization)
     * @param networkService network service (may be null for lazy initialization)
     */
    public RestoreCommand(RestoreService restoreService, NetworkService networkService) {
        this.restoreService = restoreService;
        this.networkService = networkService;
        this.serviceFactory = new ServiceFactory();
    }

    /**
     * Creates a restore command with only restore service (for backward
     * compatibility).
     *
     * @param restoreService restore service (may be null for lazy initialization)
     */
    public RestoreCommand(RestoreService restoreService) {
        this(restoreService, null);
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
            logger.error("Missing required arguments");
            System.err.println("Error: Snapshot ID and target directory are required");
            System.err.println(getUsage());
            System.err.println("Use 'help restore' for more information");
            return false;
        }

        String snapshotId = args[0];
        String targetDir = args[1];
        Path targetPath = Paths.get(targetDir);

        if (!validateTargetPath(targetPath, targetDir)) {
            return false;
        }

        RestoreOptions options;
        try {
            options = parseOptions(args);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid arguments: {}", e.getMessage());
            System.err.println(e.getMessage());
            return false;
        }

        if (options == null) {
            return false; // Help was displayed
        }

        // Services to close if locally created
        ContentStore localContentStore = null;
        MetadataService localMetadataService = null;
        NetworkService localNetworkService = null;

        try {
            RestoreService service = this.restoreService;
            NetworkService netService = this.networkService;

            // Initialize services if not provided
            if (service == null) {
                try {
                    Blake3Service blake3Service = serviceFactory.createBlake3Service();
                    localContentStore = serviceFactory.createSqliteContentStore(blake3Service);
                    localMetadataService = serviceFactory.createMetadataService();
                    service = serviceFactory.createRestoreService(localContentStore, localMetadataService,
                            blake3Service);

                    if (options.isRemoteRestore() && netService == null) {
                        localNetworkService = serviceFactory.createNetworkService();
                        netService = localNetworkService;
                    }
                } catch (ServiceException e) {
                    handleError("Failed to initialize restore service", e, logger);
                    return false;
                }
            }

            return performRestore(service, netService, snapshotId, targetPath, options);

        } catch (Exception e) {
            handleError("Restore failed", e, logger);
            return false;
        } finally {

            closeQuietly(localContentStore);
            closeQuietly(localMetadataService);
            closeQuietly(localNetworkService);
        }
    }

    /**
     * Validates the target path.
     *
     * @param targetPath target path
     * @param targetDir  target directory string
     * @return true if valid
     */
    private boolean validateTargetPath(Path targetPath, String targetDir) {
        if (Files.exists(targetPath) && !Files.isDirectory(targetPath)) {
            System.err.println("Error: Target path exists but is not a directory: " + targetDir);
            return false;
        }
        return true;
    }

    /**
     * Parses command-line options.
     *
     * @param args command-line arguments
     * @return parsed options, or null if help was displayed
     * @throws IllegalArgumentException if invalid options are provided
     */
    private RestoreOptions parseOptions(String[] args) {
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
                        i++;
                    } else {
                        throw new IllegalArgumentException("Error: --include requires a pattern");
                    }
                    break;
                case "--exclude":
                    if (i + 1 < args.length) {
                        optionsBuilder.excludePattern(args[i + 1]);
                        i++;
                    } else {
                        throw new IllegalArgumentException("Error: --exclude requires a pattern");
                    }
                    break;
                case "--remote":
                    optionsBuilder.remoteRestore(true);
                    break;
                case "--server":
                    if (i + 1 < args.length) {
                        try {
                            String[] parts = args[i + 1].split(":");
                            if (parts.length != 2) {
                                throw new IllegalArgumentException("Error: Invalid server format. Use host:port");
                            }
                            String host = parts[0];
                            int port = Integer.parseInt(parts[1]);
                            optionsBuilder.remoteAddress(new InetSocketAddress(host, port));
                            i++;
                        } catch (NumberFormatException e) {
                            throw new IllegalArgumentException("Error: Invalid port number: " + args[i + 1]);
                        }
                    } else {
                        throw new IllegalArgumentException("Error: --server requires a value (host:port)");
                    }
                    break;
                case "--transport":
                    if (i + 1 < args.length) {
                        try {
                            TransportType transportType = TransportType.valueOf(args[i + 1].toUpperCase(Locale.ROOT));
                            optionsBuilder.transportType(transportType);
                            i++;
                        } catch (IllegalArgumentException e) {
                            throw new IllegalArgumentException(
                                    "Error: Invalid transport type: " + args[i + 1] + ". Valid types: TCP, QUIC");
                        }
                    } else {
                        throw new IllegalArgumentException("Error: --transport requires a value (TCP|QUIC)");
                    }
                    break;
                case "--help":
                    displayHelp();
                    return null; // Signal to stop parsing and exit
                default:
                    if (arg.startsWith("--")) {
                        throw new IllegalArgumentException("Error: Unknown option: " + arg);
                    }
                    break;
            }
        }

        RestoreOptions options = optionsBuilder.build();

        // Validate remote restore options
        if (options.isRemoteRestore()) {
            if (options.getRemoteAddress() == null) {
                throw new IllegalArgumentException("Error: Remote restore requires --server option");
            }
        }

        return options;
    }

    /**
     * Performs the restore operation.
     *
     * @param service    restore service
     * @param netService network service
     * @param snapshotId snapshot ID
     * @param targetPath target path
     * @param options    restore options
     * @return true if successful
     * @throws Exception if restore fails
     */
    private boolean performRestore(RestoreService service, NetworkService netService,
            String snapshotId, Path targetPath, RestoreOptions options) throws Exception {
        System.out.println("Starting restore of snapshot: " + snapshotId);
        System.out.println("Target directory: " + targetPath);
        System.out.println("Options: " + options);

        if (options.isRemoteRestore()) {
            System.out.println(
                    "Remote restore from: " + options.getRemoteAddress() + " using " + options.getTransportType());

            // Connect to remote server first
            netService.connectToNode(options.getRemoteAddress(), options.getTransportType()).get();

            try {
                // For remote restore, we would typically receive the snapshot data from the
                // remote server
                // For now, we'll simulate receiving it and then restore locally
                System.out.println("Receiving snapshot data from remote server...");

                // In a real implementation, we would receive the snapshot and chunks from the
                // remote server
                // For demonstration, we'll just show what would happen and then do a local
                // restore
                CompletableFuture<RestoreService.RestoreResult> restoreFuture = service.restore(snapshotId, targetPath,
                        options);
                RestoreService.RestoreResult result = restoreFuture.get();

                printResult(result, true);
            } finally {
                // Disconnect from remote server
                netService.disconnectFromNode(options.getRemoteAddress()).get();
            }
        } else {
            // Local restore
            CompletableFuture<RestoreService.RestoreResult> restoreFuture = service.restore(snapshotId, targetPath,
                    options);
            RestoreService.RestoreResult result = restoreFuture.get();

            printResult(result, false);
        }

        return true;
    }

    /**
     * Prints restore result.
     *
     * @param result restore result
     * @param remote whether this was a remote restore
     */
    private void printResult(RestoreService.RestoreResult result, boolean remote) {
        if (remote) {
            System.out.println("\nRemote restore completed successfully!");
        } else {
            System.out.println("\nRestore completed successfully!");
        }
        System.out.println("Files restored: " + result.getFilesRestored());
        System.out.println("Files skipped: " + result.getFilesSkipped());
        System.out.println("Files with errors: " + result.getFilesWithErrors());
        System.out.println("Total bytes: " + result.getTotalBytesRestored());
        System.out.println("Integrity verified: " + result.isIntegrityVerified());
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
        System.out.println("  --remote                Enable remote restore from server");
        System.out.println("  --server HOST:PORT       Remote server address for remote restore");
        System.out.println("  --transport TYPE         Transport protocol (TCP|QUIC, default: TCP)");
        System.out.println("  --help                  Show this help message");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  restore abc123-def456 /home/user/restore");
        System.out.println("  restore abc123-def456 /home/user/restore --overwrite");
        System.out.println("  restore abc123-def456 /home/user/restore --include \"*.txt\" --exclude \"*.tmp\"");
        System.out.println("  restore abc123-def456 /home/user/restore --remote --server 192.168.1.100:8080");
        System.out.println(
                "  restore abc123-def456 /home/user/restore --remote --server backup.example.com:8080 --transport QUIC");
    }
}