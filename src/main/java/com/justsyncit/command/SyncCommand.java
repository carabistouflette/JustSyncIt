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

import com.justsyncit.ServiceFactory;
import com.justsyncit.hash.Blake3Service;
import com.justsyncit.network.NetworkService;
import com.justsyncit.network.TransportType;
import com.justsyncit.storage.ContentStore;
import com.justsyncit.storage.metadata.MetadataService;
import com.justsyncit.storage.metadata.Snapshot;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Command for synchronizing local directory with remote server.
 * Follows Single Responsibility Principle by handling only synchronization
 * operations.
 */

public class SyncCommand implements Command {

    private static final Logger logger = LoggerFactory.getLogger(SyncCommand.class);

    private final NetworkService networkService;
    private final ServiceFactory serviceFactory;

    /**
     * Creates a sync command with dependency injection.
     *
     * @param networkService network service (may be null for lazy initialization)
     */
    public SyncCommand(NetworkService networkService) {
        this.networkService = networkService;
        this.serviceFactory = new ServiceFactory();
    }

    @Override
    public String getName() {
        return "sync";
    }

    @Override
    public String getDescription() {
        return "Synchronize local directory with remote server";
    }

    @Override
    public String getUsage() {
        return "sync <local-path> <remote-server> [options]";
    }

    @Override
    public boolean execute(String[] args, CommandContext context) {
        // Handle help option first
        if (args.length == 1 && args[0].equals("--help")) {
            displayHelp();
            return true;
        }

        // Parse required arguments and options
        SyncOptions options = parseOptions(args);
        if (options == null) {
            return false;
        }

        if (options.helpRequested) {
            displayHelp();
            return true;
        }

        // Initialize services
        try (ServiceContext services = initializeServices()) {
            if (services == null) {
                return false;
            }

            if (options.verbose) {
                printSyncSettings(options);
            }

            if (options.dryRun) {
                System.out.println("DRY RUN MODE - No actual changes will be made");
                System.out.println();
            }

            // Connect to remote server
            if (options.verbose) {
                System.out.println("Connecting to remote server...");
            }

            CompletableFuture<Void> connectFuture = services.netService.connectToNode(options.serverAddress,
                    options.transportType);
            try {
                connectFuture.get(30, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                logger.error("Connection timeout after 30 seconds", e);
                System.err.println("Error: Connection timeout after 30 seconds");
                return false;
            }

            if (options.verbose) {
                System.out.println("Connected to server successfully.");
                System.out.println("Starting synchronization...");
            }

            boolean success = performSynchronization(services, options);

            // Disconnect from remote server
            try {
                services.netService.disconnectFromNode(options.serverAddress).get(10, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                logger.warn("Disconnect timeout after 10 seconds", e);
                System.err.println("Warning: Disconnect timeout after 10 seconds");
            }

            return success;

        } catch (Exception e) {
            logger.error("Synchronization failed", e);
            System.err.println("Error: Synchronization failed: " + e.getMessage());
            if (e.getCause() != null) {
                System.err.println("Cause: " + e.getCause().getMessage());
            }
            return false;
        }
    }

    private boolean performSynchronization(ServiceContext services, SyncOptions options) {
        try {
            // Get local and remote snapshots
            List<Snapshot> localSnapshots;
            List<Snapshot> remoteSnapshots;

            try {
                localSnapshots = services.metadataService.listSnapshots();
                if (localSnapshots == null) {
                    localSnapshots = List.of();
                }
            } catch (Exception e) {
                logger.error("Failed to retrieve local snapshots", e);
                System.err.println("Error: Failed to retrieve local snapshots: " + e.getMessage());
                return false;
            }

            // In a real implementation, we would query the remote server for its snapshots
            remoteSnapshots = simulateRemoteSnapshots();

            if (options.verbose) {
                System.out.println("Local snapshots: " + localSnapshots.size());
                System.out.println("Remote snapshots: " + remoteSnapshots.size());
                System.out.println();
            }

            // Perform synchronization logic
            SyncResult result = new SyncResult();

            // Create sets for efficient lookup
            Set<String> localSnapshotIds = new HashSet<>();
            Set<String> remoteSnapshotIds = new HashSet<>();

            for (Snapshot snapshot : localSnapshots) {
                if (snapshot != null && snapshot.getId() != null) {
                    localSnapshotIds.add(snapshot.getId());
                }
            }

            for (Snapshot snapshot : remoteSnapshots) {
                if (snapshot != null && snapshot.getId() != null) {
                    remoteSnapshotIds.add(snapshot.getId());
                }
            }

            if (options.bidirectional) {
                syncLocalToRemote(localSnapshots, remoteSnapshotIds, result, options);
                syncRemoteToLocal(remoteSnapshots, localSnapshotIds, result, options);
            } else {
                syncLocalToRemote(localSnapshots, remoteSnapshotIds, result, options);
            }

            if (options.verbose) {
                printSyncSummary(result, options);
            } else {
                System.out.println("Sync completed: " + result.syncedToRemote + " to remote, " + result.syncedToLocal
                        + " to local");
            }

            return true;

        } catch (Exception e) {
            logger.error("Synchronization logic failed", e);
            System.err.println("Error: Synchronization logic failed: " + e.getMessage());
            return false;
        }
    }

    private void syncLocalToRemote(List<Snapshot> localSnapshots, Set<String> remoteSnapshotIds, SyncResult result,
            SyncOptions options) throws InterruptedException {
        for (Snapshot localSnapshot : localSnapshots) {
            if (localSnapshot == null || localSnapshot.getId() == null) {
                continue;
            }

            if (!remoteSnapshotIds.contains(localSnapshot.getId())) {
                if (options.verbose) {
                    System.out.println(
                            "Syncing to remote: " + localSnapshot.getName() + " (" + localSnapshot.getId() + ")");
                }

                if (!options.dryRun) {
                    result.totalBytesTransferred += localSnapshot.getTotalSize();
                    simulateTransfer(localSnapshot.getTotalSize());
                }
                result.syncedToRemote++;
            }
        }
    }

    private void syncRemoteToLocal(List<Snapshot> remoteSnapshots, Set<String> localSnapshotIds, SyncResult result,
            SyncOptions options) throws InterruptedException {
        for (Snapshot remoteSnapshot : remoteSnapshots) {
            if (remoteSnapshot == null || remoteSnapshot.getId() == null) {
                continue;
            }

            if (!localSnapshotIds.contains(remoteSnapshot.getId())) {
                if (options.verbose) {
                    System.out.println(
                            "Syncing to local: " + remoteSnapshot.getName() + " (" + remoteSnapshot.getId() + ")");
                }

                if (!options.dryRun) {
                    result.totalBytesTransferred += remoteSnapshot.getTotalSize();
                    simulateTransfer(remoteSnapshot.getTotalSize());
                }
                result.syncedToLocal++;
            }
        }
    }

    private static class SyncResult {
        int syncedToRemote = 0;
        int syncedToLocal = 0;
        int conflicts = 0;
        long totalBytesTransferred = 0;
    }

    private void printSyncSettings(SyncOptions options) {
        System.out.println("Synchronization Settings:");
        System.out.println("=======================");
        System.out.println("Local Path: " + options.localDir.toAbsolutePath());
        System.out.println("Remote Server: " + options.serverAddress);
        System.out.println("Transport: " + options.transportType);
        System.out.println("Bidirectional: " + (options.bidirectional ? "enabled" : "disabled"));
        System.out.println("Delete Extra Files: " + (options.deleteExtra ? "enabled" : "disabled"));
        System.out.println("Verify Integrity: " + options.verifyIntegrity);
        System.out.println("Dry Run: " + (options.dryRun ? "enabled" : "disabled"));
        System.out.println();
    }

    private void printSyncSummary(SyncResult result, SyncOptions options) {
        System.out.println();
        System.out.println("Synchronization completed!");
        System.out.println("========================");
        System.out.println("Synced to remote: " + result.syncedToRemote + " snapshots");
        System.out.println("Synced to local: " + result.syncedToLocal + " snapshots");
        System.out.println("Conflicts: " + result.conflicts);
        System.out.println("Total bytes transferred: " + formatFileSize(result.totalBytesTransferred));

        if (options.dryRun) {
            System.out.println();
            System.out.println("This was a dry run. No actual changes were made.");
        }
    }

    private static class SyncOptions {
        Path localDir;
        InetSocketAddress serverAddress;
        TransportType transportType = TransportType.TCP;
        boolean bidirectional = true;
        boolean deleteExtra = false;
        boolean verifyIntegrity = true;
        boolean dryRun = false;
        boolean verbose = true;
        boolean helpRequested = false;
    }

    private SyncOptions parseOptions(String[] args) {
        if (args.length < 2) {
            // Handle special case where user asks for help without other args, but the
            // initial check should cover it.
            // If we are here, args are insufficient for real work.
            if (args.length == 1 && args[0].equals("--help")) {
                SyncOptions opts = new SyncOptions();
                opts.helpRequested = true;
                return opts;
            }
            System.err.println("Error: Missing required arguments");
            System.err.println("Usage: " + getUsage());
            System.err.println("Use 'help sync' for more information");
            return null;
        }

        SyncOptions options = new SyncOptions();
        String localPath = args[0];
        String remoteServer = args[1];
        options.localDir = Paths.get(localPath);

        // Validate local directory
        if (!Files.exists(options.localDir)) {
            System.err.println("Error: Local directory does not exist: " + localPath);
            return null;
        }

        if (!Files.isDirectory(options.localDir)) {
            System.err.println("Error: Local path is not a directory: " + localPath);
            return null;
        }

        // Parse remote server address
        try {
            String[] parts = remoteServer.split(":");
            if (parts.length != 2) {
                System.err.println("Error: Invalid server format. Use host:port");
                return null;
            }
            String host = parts[0];
            if (host == null || host.trim().isEmpty()) {
                System.err.println("Error: Hostname cannot be empty");
                return null;
            }
            int port = Integer.parseInt(parts[1]);
            if (port < 1 || port > 65535) {
                System.err.println("Error: Port must be between 1 and 65535");
                return null;
            }
            options.serverAddress = new InetSocketAddress(host, port);
        } catch (NumberFormatException e) {
            System.err.println("Error: Invalid port number in server address: " + remoteServer);
            return null;
        }

        for (int i = 2; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--transport":
                    if (i + 1 < args.length) {
                        try {
                            options.transportType = TransportType.valueOf(args[i + 1].toUpperCase(Locale.ROOT));
                            i++;
                        } catch (IllegalArgumentException e) {
                            System.err.println(
                                    "Error: Invalid transport type: " + args[i + 1] + ". Valid types: TCP, QUIC");
                            return null;
                        }
                    } else {
                        System.err.println("Error: --transport requires a value (TCP|QUIC)");
                        return null;
                    }
                    break;
                case "--one-way":
                    options.bidirectional = false;
                    break;
                case "--delete-extra":
                    options.deleteExtra = true;
                    break;
                case "--no-verify":
                    options.verifyIntegrity = false;
                    break;
                case "--dry-run":
                    options.dryRun = true;
                    break;
                case "--quiet":
                case "-q":
                    options.verbose = false;
                    break;
                case "--help":
                    options.helpRequested = true;
                    return options;
                default:
                    if (arg.startsWith("--")) {
                        System.err.println("Error: Unknown option: " + arg);
                        return null;
                    }
                    break;
            }
        }
        return options;
    }

    private static class ServiceContext implements AutoCloseable {
        final NetworkService netService;
        final MetadataService metadataService;
        final ContentStore contentStore;

        ServiceContext(NetworkService netService, MetadataService metadataService, ContentStore contentStore) {
            this.netService = netService;
            this.metadataService = metadataService;
            this.contentStore = contentStore;
        }

        @Override
        public void close() {
            if (netService != null) {
                try {
                    netService.close();
                } catch (Exception e) {
                    logger.warn("Failed to close network service", e);
                    System.err.println("Warning: Failed to close network service: " + e.getMessage());
                }
            }
            if (metadataService != null) {
                try {
                    metadataService.close();
                } catch (Exception e) {
                    logger.warn("Failed to close metadata service", e);
                    System.err.println("Warning: Failed to close metadata service: " + e.getMessage());
                }
            }
            if (contentStore != null) {
                try {
                    contentStore.close();
                } catch (Exception e) {
                    logger.warn("Failed to close content store", e);
                    System.err.println("Warning: Failed to close content store: " + e.getMessage());
                }
            }
        }
    }

    private ServiceContext initializeServices() {
        NetworkService ns = null;
        MetadataService ms = null;
        Blake3Service bs = null;
        ContentStore cs = null;

        try {
            ns = networkService != null ? networkService : serviceFactory.createNetworkService();
            ms = serviceFactory.createMetadataService();
            bs = serviceFactory.createBlake3Service();
            cs = serviceFactory.createSqliteContentStore(bs);
        } catch (Exception e) {
            logger.error("Failed to initialize services", e);
            System.err.println("Error: Failed to initialize services: " + e.getMessage());
            // Cleanup partilly initialized services
            if (ns != null && networkService == null) {
                try {
                    ns.close();
                } catch (Exception ignored) {
                }
            }
            if (ms != null) {
                try {
                    ms.close();
                } catch (Exception ignored) {
                }
            }
            return null;
        }
        return new ServiceContext(ns, ms, cs);
    }

    /**
     * Simulates remote snapshots (for demonstration purposes).
     * In a real implementation, this would query the remote server.
     *
     * @return list of simulated remote snapshots
     */
    private List<Snapshot> simulateRemoteSnapshots() {
        // Return empty list for simulation
        // In a real implementation, this would be an API call to the remote server
        return List.of();
    }

    /**
     * Simulates a transfer operation with progress.
     *
     * @param bytes size to transfer
     */
    private void simulateTransfer(long bytes) throws InterruptedException {
        if (bytes <= 0) {
            return;
        }

        long chunkSize = 64 * 1024; // 64KB chunks
        long transferred = 0;

        while (transferred < bytes) {
            long currentChunk = Math.min(chunkSize, bytes - transferred);
            transferred += currentChunk;
        }
    }

    /**
     * Displays detailed help information for the sync command.
     */
    private void displayHelp() {
        System.out.println("Sync Command Help");
        System.out.println("================");
        System.out.println();
        System.out.println("Usage: " + getUsage());
        System.out.println();
        System.out.println("Description:");
        System.out.println("  " + getDescription());
        System.out.println();
        System.out.println("Arguments:");
        System.out.println("  local-path      Local directory to synchronize");
        System.out.println("  remote-server   Remote server address (host:port)");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --transport TYPE       Transport protocol (TCP|QUIC, default: TCP)");
        System.out.println("  --one-way             One-way sync (local to remote only)");
        System.out.println("  --delete-extra        Delete files that don't exist on source");
        System.out.println("  --no-verify           Skip integrity verification");
        System.out.println("  --dry-run             Show what would be synchronized without making changes");
        System.out.println("  --quiet, -q            Quiet mode with minimal output");
        System.out.println("  --help                 Show this help message");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  sync /home/user/documents 192.168.1.100:8080");
        System.out.println("  sync /home/user/documents backup.example.com:8080 --transport QUIC");
        System.out.println("  sync /home/user/documents 192.168.1.100:8080 --one-way");
        System.out.println("  sync /home/user/documents backup.example.com:8080 --delete-extra");
        System.out.println("  sync /home/user/documents 192.168.1.100:8080 --dry-run");
    }

    /**
     * Formats file size in human-readable format.
     *
     * @param bytes size in bytes
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