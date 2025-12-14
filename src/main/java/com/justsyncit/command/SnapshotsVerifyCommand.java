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
import com.justsyncit.hash.Blake3Service;
import com.justsyncit.storage.ContentStore;
import com.justsyncit.storage.metadata.FileMetadata;
import com.justsyncit.storage.metadata.MetadataService;
import com.justsyncit.storage.metadata.Snapshot;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Command for verifying the integrity of a snapshot.
 * Follows Single Responsibility Principle by handling only snapshot
 * verification operations.
 */

public class SnapshotsVerifyCommand implements Command {

    private final MetadataService metadataService;
    private final ServiceFactory serviceFactory;
    private final Blake3Service blake3Service;

    /**
     * Creates a snapshots verify command with dependency injection.
     *
     * @param metadataService metadata service (may be null for lazy initialization)
     * @param blake3Service   BLAKE3 service (may be null for lazy initialization)
     */
    public SnapshotsVerifyCommand(MetadataService metadataService, Blake3Service blake3Service) {
        this.metadataService = metadataService;
        this.blake3Service = blake3Service;
        this.serviceFactory = new ServiceFactory();
    }

    /**
     * Creates a snapshots verify command with only metadata service.
     *
     * @param metadataService metadata service (may be null for lazy initialization)
     */
    public SnapshotsVerifyCommand(MetadataService metadataService) {
        this(metadataService, null);
    }

    @Override
    public String getName() {
        return "snapshots";
    }

    @Override
    public String getDescription() {
        return "Verify integrity of a snapshot";
    }

    @Override
    public String getUsage() {
        return "snapshots verify <snapshot-id> [options]";
    }

    @Override
    public boolean execute(String[] args, CommandContext context) {
        if (args.length == 1 && args[0].equals("--help")) {
            displayHelp();
            return true;
        }

        if (args.length < 2 || !args[0].equals("verify")) {
            System.err.println("Error: Missing subcommand 'verify' or snapshot ID");
            System.err.println(getUsage());
            System.err.println("Use 'help snapshots verify' for more information");
            return false;
        }

        String snapshotId = args[1];
        VerifyOptions options = parseOptions(args);
        if (options == null) {
            return false;
        }

        if (options.helpRequested) {
            displayHelp();
            return true;
        }

        try (ServiceContext services = initializeServices()) {
            if (services == null) {
                return false;
            }

            // Get snapshot information
            Optional<Snapshot> snapshotOpt = services.metadataService.getSnapshot(snapshotId);
            if (snapshotOpt.isEmpty()) {
                System.err.println("Error: Snapshot not found: " + snapshotId);
                return false;
            }

            Snapshot snapshot = snapshotOpt.get();

            if (!options.quiet) {
                printSnapshotInfo(snapshot);
            }

            // Get files in snapshot
            List<FileMetadata> files = services.metadataService.getFilesInSnapshot(snapshotId);
            if (files == null) {
                System.err.println("Error: Failed to retrieve files for snapshot: " + snapshotId);
                return false;
            }

            if (files.isEmpty()) {
                if (!options.quiet) {
                    System.out.println("Warning: Snapshot contains no files to verify");
                }
                return true;
            }

            return verifyFiles(files, options, services);

        } catch (IOException e) {
            System.err.println("Error: Failed to verify snapshot: " + e.getMessage());
            return false;
        } catch (Exception e) {
            System.err.println("Error: Unexpected error during snapshot verification: " + e.getMessage());
            return false;
        }
    }

    private void printSnapshotInfo(Snapshot snapshot) {
        System.out.println("Verifying Snapshot: " + snapshot.getId());
        System.out.println("=============================");
        System.out.println("Name: " + snapshot.getName());
        System.out.println("Created: " + snapshot.getCreatedAt());
        System.out.println("Total Files: " + snapshot.getTotalFiles());
        System.out.println("Total Size: " + formatFileSize(snapshot.getTotalSize()));
        System.out.println();
    }

    private static class VerifyOptions {
        boolean verifyChunks = true;
        boolean verifyFileHashes = true;
        boolean quiet = false;
        boolean showProgress = true;
        boolean helpRequested = false;
    }

    private VerifyOptions parseOptions(String[] args) {
        VerifyOptions options = new VerifyOptions();
        for (int i = 2; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--no-chunk-verify":
                    options.verifyChunks = false;
                    break;
                case "--no-file-hash-verify":
                    options.verifyFileHashes = false;
                    break;
                case "--quiet":
                case "-q":
                    options.quiet = true;
                    options.showProgress = false;
                    break;
                case "--no-progress":
                    options.showProgress = false;
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
        final MetadataService metadataService;
        final Blake3Service blake3Service;
        final ContentStore contentStore;
        final boolean createdMetadataService;
        final boolean createdBlake3Service;

        ServiceContext(MetadataService metadataService, Blake3Service blake3Service, ContentStore contentStore,
                boolean createdMetadataService, boolean createdBlake3Service) {
            this.metadataService = metadataService;
            this.blake3Service = blake3Service;
            this.contentStore = contentStore;
            this.createdMetadataService = createdMetadataService;
            this.createdBlake3Service = createdBlake3Service;
        }

        @Override
        public void close() {
            if (createdMetadataService && metadataService != null) {
                try {
                    metadataService.close();
                } catch (Exception e) {
                    System.err.println("Warning: Failed to close metadata service: " + e.getMessage());
                }
            }
            if (contentStore != null) {
                try {
                    contentStore.close();
                } catch (Exception e) {
                    System.err.println("Warning: Failed to close content store: " + e.getMessage());
                }
            }
            if (createdBlake3Service && blake3Service != null) {
                try {
                    if (blake3Service instanceof AutoCloseable) {
                        ((AutoCloseable) blake3Service).close();
                    }
                } catch (Exception e) {
                    System.err.println("Warning: Failed to close BLAKE3 service: " + e.getMessage());
                }
            }
        }
    }

    private ServiceContext initializeServices() {
        MetadataService ms = this.metadataService;
        Blake3Service bs = this.blake3Service;
        ContentStore cs = null;
        boolean createdMs = false;
        boolean createdBs = false;

        if (ms == null) {
            try {
                ms = serviceFactory.createMetadataService();
                createdMs = true;
            } catch (ServiceException e) {
                System.err.println("Error: Failed to initialize metadata service: " + e.getMessage());
                return null;
            }
        }

        if (bs == null) {
            try {
                bs = serviceFactory.createBlake3Service();
                createdBs = true;
            } catch (ServiceException e) {
                System.err.println("Error: Failed to initialize BLAKE3 service: " + e.getMessage());
                // If we created metadata service, we should optimize and close it,
                // but for simplicity and because we return null, we rely on the caller or just
                // leak slightly in this error case
                // which is acceptable for a CLI command that will exit.
                // Better:
                if (createdMs) {
                    try {
                        ms.close();
                    } catch (Exception ignored) {
                    }
                }
                return null;
            }
        }

        try {
            cs = serviceFactory.createSqliteContentStore(bs);
        } catch (Exception e) {
            System.err.println("Error: Failed to initialize content store: " + e.getMessage());
            if (createdMs) {
                try {
                    ms.close();
                } catch (Exception ignored) {
                }
            }
            if (createdBs && bs instanceof AutoCloseable) {
                try {
                    ((AutoCloseable) bs).close();
                } catch (Exception ignored) {
                }
            }
            return null;
        }

        return new ServiceContext(ms, bs, cs, createdMs, createdBs);
    }

    private static class VerifyStats {
        final AtomicInteger filesVerified = new AtomicInteger(0);
        final AtomicInteger filesWithErrors = new AtomicInteger(0);
        final AtomicInteger chunksVerified = new AtomicInteger(0);
        final AtomicInteger chunksWithErrors = new AtomicInteger(0);
        final AtomicLong bytesVerified = new AtomicLong(0);
        final AtomicLong errors = new AtomicLong(0);
    }

    private boolean verifyFiles(List<FileMetadata> files, VerifyOptions options, ServiceContext services) {
        VerifyStats stats = new VerifyStats();

        System.out.println("Starting verification...");
        System.out.println();

        for (int i = 0; i < files.size(); i++) {
            FileMetadata file = files.get(i);
            verifyFile(file, i, files.size(), options, services, stats);
        }

        if (options.showProgress && !options.quiet) {
            System.out.println();
        }

        printResults(files.size(), stats.filesVerified.get(), stats.filesWithErrors.get(),
                stats.chunksVerified.get(), stats.chunksWithErrors.get(),
                stats.bytesVerified.get(), stats.errors.get());

        return stats.filesWithErrors.get() == 0 && stats.chunksWithErrors.get() == 0;
    }

    private void verifyFile(FileMetadata file, int index, int totalFiles, VerifyOptions options,
            ServiceContext services, VerifyStats stats) {
        // Validate file metadata
        if (file == null) {
            if (!options.quiet) {
                System.out.println("\nWarning: Encountered null file metadata at index " + index);
            }
            stats.filesWithErrors.incrementAndGet();
            stats.errors.incrementAndGet();
            return;
        }

        // Report progress
        if (options.showProgress && !options.quiet && (index % 10 == 0 || index == totalFiles - 1)) {
            double progress = (index + 1) * 100.0 / totalFiles;
            System.out.printf("\rProgress: %d/%d files (%.1f%%)", index + 1, totalFiles, progress);
            System.out.flush();
        }

        try {
            boolean fileIntegrityOk = true;

            if (options.verifyFileHashes) {
                String expectedHash = file.getFileHash();
                if (expectedHash == null || expectedHash.isEmpty()) {
                    if (!options.quiet) {
                        System.out.println("\nWarning: File has no hash: " + file.getPath());
                    }
                    fileIntegrityOk = false;
                } else {
                    fileIntegrityOk = verifyFileHash(file, services.contentStore, services.blake3Service);
                }
            }

            if (options.verifyChunks && fileIntegrityOk) {
                fileIntegrityOk = verifyChunks(file, options, services, stats, fileIntegrityOk);
            }

            if (fileIntegrityOk) {
                stats.filesVerified.incrementAndGet();
                stats.bytesVerified.addAndGet(file.getSize());
            } else {
                stats.filesWithErrors.incrementAndGet();
                if (!options.quiet) {
                    System.out.println("\nFile integrity error: " + file.getPath());
                }
            }

        } catch (Exception e) {
            stats.filesWithErrors.incrementAndGet();
            stats.errors.incrementAndGet();
            if (!options.quiet) {
                System.out.println("\nError verifying file " + file.getPath() + ": " + e.getMessage());
            }
        }
    }

    private boolean verifyChunks(FileMetadata file, VerifyOptions options, ServiceContext services,
            VerifyStats stats, boolean fileIntegrityOk) {
        List<String> chunkHashes = file.getChunkHashes();
        if (chunkHashes == null) {
            if (!options.quiet) {
                System.out.println("\nWarning: File has no chunk hashes: " + file.getPath());
            }
            stats.errors.incrementAndGet();
            return false;
        }

        for (String chunkHash : chunkHashes) {
            if (chunkHash == null || chunkHash.isEmpty()) {
                if (!options.quiet) {
                    System.out.println("\nWarning: Encountered null or empty chunk hash in file: " + file.getPath());
                }
                stats.chunksWithErrors.incrementAndGet();
                stats.errors.incrementAndGet();
                fileIntegrityOk = false;
                continue;
            }

            stats.chunksVerified.incrementAndGet();

            try {
                if (services.contentStore.existsChunk(chunkHash)) {
                    services.contentStore.retrieveChunk(chunkHash);
                } else {
                    stats.chunksWithErrors.incrementAndGet();
                    stats.errors.incrementAndGet();
                    fileIntegrityOk = false;
                    if (!options.quiet) {
                        System.out.println("\nChunk not found: " + chunkHash + " (file: " + file.getPath() + ")");
                    }
                }
            } catch (IOException e) {
                stats.chunksWithErrors.incrementAndGet();
                stats.errors.incrementAndGet();
                fileIntegrityOk = false;
                if (!options.quiet) {
                    System.out.println("\nChunk integrity error: " + chunkHash + " (file: " + file.getPath() + ") - "
                            + e.getMessage());
                }
            } catch (Exception e) {
                stats.chunksWithErrors.incrementAndGet();
                stats.errors.incrementAndGet();
                fileIntegrityOk = false;
                if (!options.quiet) {
                    System.out.println("\nUnexpected chunk error: " + chunkHash + " (file: " + file.getPath() + ") - "
                            + e.getMessage());
                }
            }
        }
        return fileIntegrityOk;
    }

    private void printResults(int totalFiles, int filesVerified, int filesWithErrors,
            int chunksVerified, int chunksWithErrors, long bytesVerified, long errors) {
        System.out.println();
        System.out.println("Verification Results:");
        System.out.println("====================");
        System.out.println("Files verified: " + filesVerified + "/" + totalFiles);
        System.out.println("Files with errors: " + filesWithErrors);
        System.out.println("Chunks verified: " + chunksVerified);
        System.out.println("Chunks with errors: " + chunksWithErrors);
        System.out.println("Bytes verified: " + formatFileSize(bytesVerified));
        System.out.println();

        if (filesWithErrors == 0 && chunksWithErrors == 0) {
            System.out.println("✓ Snapshot verification PASSED - No integrity issues found");
        } else {
            System.out.println("✗ Snapshot verification FAILED - " + errors + " error(s) found");
        }
    }

    /**
     * Displays detailed help information for the snapshots verify command.
     */
    private void displayHelp() {
        System.out.println("Snapshots Verify Command Help");
        System.out.println("===============================");
        System.out.println();
        System.out.println("Usage: " + getUsage());
        System.out.println();
        System.out.println("Description:");
        System.out.println("  " + getDescription());
        System.out.println();
        System.out.println("Arguments:");
        System.out.println("  snapshot-id    ID of the snapshot to verify");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --no-chunk-verify        Skip chunk integrity verification");
        System.out.println("  --no-file-hash-verify    Skip file hash verification");
        System.out.println("  --quiet, -q              Quiet mode with minimal output");
        System.out.println("  --no-progress             Don't show progress indicator");
        System.out.println("  --help                    Show this help message");
        System.out.println();
        System.out.println("Notes:");
        System.out.println("  - Verification checks both chunk and file integrity");
        System.out.println("  - This can be time-consuming for large snapshots");
        System.out.println("  - Use --no-chunk-verify for faster verification");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  snapshots verify abc123-def456");
        System.out.println("  snapshots verify abc123-def456 --quiet");
        System.out.println("  snapshots verify abc123-def456 --no-chunk-verify");
        System.out.println("  snapshots verify abc123-def456 --no-progress");
    }

    /**
     * Verifies the file hash by reconstructing the file from chunks and computing
     * its hash.
     *
     * @param file          the file metadata to verify
     * @param contentStore  the content store to retrieve chunks from
     * @param blake3Service the BLAKE3 service for hashing
     * @return true if the file hash is valid, false otherwise
     */
    private boolean verifyFileHash(FileMetadata file, ContentStore contentStore, Blake3Service blake3Service) {
        try {
            List<String> chunkHashes = file.getChunkHashes();
            if (chunkHashes == null || chunkHashes.isEmpty()) {
                return false;
            }

            // Create incremental hasher to reconstruct file from chunks
            Blake3Service.Blake3IncrementalHasher hasher = blake3Service.createIncrementalHasher();

            // Retrieve and hash each chunk in order
            for (String chunkHash : chunkHashes) {
                if (chunkHash == null || chunkHash.isEmpty()) {
                    return false;
                }

                try {
                    byte[] chunkData = contentStore.retrieveChunk(chunkHash);
                    if (chunkData == null) {
                        return false;
                    }
                    hasher.update(chunkData);
                } catch (IOException e) {
                    return false;
                }
            }

            // Compute the reconstructed file hash
            String computedHash = hasher.digest();
            String expectedHash = file.getFileHash();

            return computedHash != null && computedHash.equals(expectedHash);

        } catch (Exception e) {
            return false;
        }
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