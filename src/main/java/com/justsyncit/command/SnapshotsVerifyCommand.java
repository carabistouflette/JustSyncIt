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
 * Follows Single Responsibility Principle by handling only snapshot verification operations.
 */
public class SnapshotsVerifyCommand implements Command {

    private final MetadataService metadataService;
    private final ServiceFactory serviceFactory;
    private final Blake3Service blake3Service;

    /**
     * Creates a snapshots verify command with dependency injection.
     *
     * @param metadataService metadata service (may be null for lazy initialization)
     * @param blake3Service BLAKE3 service (may be null for lazy initialization)
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
        // Handle help option first
        if (args.length == 1 && args[0].equals("--help")) {
            displayHelp();
            return true;
        }

        // Check for subcommand and snapshot ID
        if (args.length < 2 || !args[0].equals("verify")) {
            System.err.println("Error: Missing subcommand 'verify' or snapshot ID");
            System.err.println(getUsage());
            System.err.println("Use 'help snapshots verify' for more information");
            return false;
        }

        String snapshotId = args[1];

        // Parse options
        boolean verifyChunks = true;
        boolean verifyFileHashes = true;
        boolean quiet = false;
        boolean showProgress = true;

        for (int i = 2; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--no-chunk-verify":
                    verifyChunks = false;
                    break;
                case "--no-file-hash-verify":
                    verifyFileHashes = false;
                    break;
                case "--quiet":
                case "-q":
                    quiet = true;
                    showProgress = false;
                    break;
                case "--no-progress":
                    showProgress = false;
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

        // Create services if not provided
        MetadataService metadataService = this.metadataService;
        Blake3Service blake3Service = this.blake3Service;
        ContentStore contentStore = null;
        boolean createdMetadataService = false;
        boolean createdBlake3Service = false;

        if (metadataService == null) {
            try {
                metadataService = serviceFactory.createMetadataService();
                createdMetadataService = true;
            } catch (ServiceException e) {
                System.err.println("Error: Failed to initialize metadata service: " + e.getMessage());
                return false;
            }
        }

        if (blake3Service == null) {
            try {
                blake3Service = serviceFactory.createBlake3Service();
                createdBlake3Service = true;
            } catch (ServiceException e) {
                System.err.println("Error: Failed to initialize BLAKE3 service: " + e.getMessage());
                return false;
            }
        }

        try {
            contentStore = serviceFactory.createSqliteContentStore(blake3Service);
        } catch (ServiceException e) {
            System.err.println("Error: Failed to initialize content store: " + e.getMessage());
            return false;
        } catch (Exception e) {
            System.err.println("Error: Unexpected error initializing content store: " + e.getMessage());
            return false;
        }

        try {
            // Get snapshot information
            Optional<Snapshot> snapshotOpt = metadataService.getSnapshot(snapshotId);
            if (snapshotOpt.isEmpty()) {
                System.err.println("Error: Snapshot not found: " + snapshotId);
                return false;
            }

            Snapshot snapshot = snapshotOpt.get();

            if (!quiet) {
                System.out.println("Verifying Snapshot: " + snapshotId);
                System.out.println("=============================");
                System.out.println("Name: " + snapshot.getName());
                System.out.println("Created: " + snapshot.getCreatedAt());
                System.out.println("Total Files: " + snapshot.getTotalFiles());
                System.out.println("Total Size: " + formatFileSize(snapshot.getTotalSize()));
                System.out.println();
            }

            // Get files in snapshot
            List<FileMetadata> files = metadataService.getFilesInSnapshot(snapshotId);

            // Validate files list
            if (files == null) {
                System.err.println("Error: Failed to retrieve files for snapshot: " + snapshotId);
                return false;
            }

            if (files.isEmpty()) {
                if (!quiet) {
                    System.out.println("Warning: Snapshot contains no files to verify");
                }
                return true;
            }

            // Verification statistics
            AtomicInteger filesVerified = new AtomicInteger(0);
            AtomicInteger filesWithErrors = new AtomicInteger(0);
            AtomicInteger chunksVerified = new AtomicInteger(0);
            AtomicInteger chunksWithErrors = new AtomicInteger(0);
            AtomicLong bytesVerified = new AtomicLong(0);
            AtomicLong errors = new AtomicLong(0);

            System.out.println("Starting verification...");
            System.out.println();

            // Verify each file
            for (int i = 0; i < files.size(); i++) {
                FileMetadata file = files.get(i);

                // Validate file metadata
                if (file == null) {
                    if (!quiet) {
                        System.out.println("\nWarning: Encountered null file metadata at index " + i);
                    }
                    filesWithErrors.incrementAndGet();
                    errors.incrementAndGet();
                    continue;
                }

                // Report progress less frequently for better performance
                if (showProgress && !quiet && (i % 10 == 0 || i == files.size() - 1)) {
                    double progress = (i + 1) * 100.0 / files.size();
                    System.out.printf("\rProgress: %d/%d files (%.1f%%)",
                        i + 1, files.size(), progress);
                    System.out.flush();
                }

                try {
                    // Verify file integrity
                    boolean fileIntegrityOk = true;

                    if (verifyFileHashes) {
                        // Verify file hash by reconstructing from chunks
                        String expectedHash = file.getFileHash();
                        if (expectedHash == null || expectedHash.isEmpty()) {
                            if (!quiet) {
                                System.out.println("\nWarning: File has no hash: " + file.getPath());
                            }
                            fileIntegrityOk = false;
                        } else {
                            // Implement actual file hash verification
                            fileIntegrityOk = verifyFileHash(file, contentStore, blake3Service);
                        }
                    }

                    if (verifyChunks && fileIntegrityOk) {
                        List<String> chunkHashes = file.getChunkHashes();
                        if (chunkHashes == null) {
                            if (!quiet) {
                                System.out.println("\nWarning: File has no chunk hashes: " + file.getPath());
                            }
                            fileIntegrityOk = false;
                            filesWithErrors.incrementAndGet();
                            errors.incrementAndGet();
                        } else {
                            // Verify each chunk exists and has correct hash
                            for (String chunkHash : chunkHashes) {
                                // Validate chunk hash
                                if (chunkHash == null || chunkHash.isEmpty()) {
                                    if (!quiet) {
                                        System.out.println("\nWarning: Encountered null or empty chunk hash in file: " + file.getPath());
                                    }
                                    chunksWithErrors.incrementAndGet();
                                    errors.incrementAndGet();
                                    fileIntegrityOk = false;
                                    continue;
                                }

                                chunksVerified.incrementAndGet();

                                // Verify chunk integrity by checking if chunk exists and retrieving it
                                // The retrieveChunk method already verifies integrity
                                try {
                                    if (contentStore.existsChunk(chunkHash)) {
                                        // Verify chunk integrity by retrieving it - the retrieveChunk method verifies the hash
                                        // We don't need to store the data since retrieval itself verifies integrity
                                        contentStore.retrieveChunk(chunkHash);
                                        // If we get here without exception, chunk integrity is verified
                                    } else {
                                        chunksWithErrors.incrementAndGet();
                                        errors.incrementAndGet();
                                        fileIntegrityOk = false;

                                        if (!quiet) {
                                            System.out.println("\nChunk not found: " + chunkHash + " (file: " + file.getPath() + ")");
                                        }
                                    }
                                } catch (IOException e) {
                                    chunksWithErrors.incrementAndGet();
                                    errors.incrementAndGet();
                                    fileIntegrityOk = false;

                                    if (!quiet) {
                                        System.out.println("\nChunk integrity error: " + chunkHash + " (file: " + file.getPath() + ") - " + e.getMessage());
                                    }
                                } catch (Exception e) {
                                    chunksWithErrors.incrementAndGet();
                                    errors.incrementAndGet();
                                    fileIntegrityOk = false;

                                    if (!quiet) {
                                        System.out.println("\nUnexpected chunk error: " + chunkHash + " (file: " + file.getPath() + ") - " + e.getMessage());
                                    }
                                }
                            }
                        }
                    }

                    if (fileIntegrityOk) {
                        filesVerified.incrementAndGet();
                        bytesVerified.addAndGet(file.getSize());
                    } else {
                        filesWithErrors.incrementAndGet();
                        if (!quiet) {
                            System.out.println("\nFile integrity error: " + file.getPath());
                        }
                    }

                } catch (Exception e) {
                    filesWithErrors.incrementAndGet();
                    errors.incrementAndGet();
                    if (!quiet) {
                        System.out.println("\nError verifying file " + file.getPath() + ": " + e.getMessage());
                    }
                }
            }

            if (showProgress && !quiet) {
                System.out.println(); // New line after progress
            }

            // Display results
            System.out.println();
            System.out.println("Verification Results:");
            System.out.println("====================");
            System.out.println("Files verified: " + filesVerified.get() + "/" + files.size());
            System.out.println("Files with errors: " + filesWithErrors.get());
            System.out.println("Chunks verified: " + chunksVerified.get());
            System.out.println("Chunks with errors: " + chunksWithErrors.get());
            System.out.println("Bytes verified: " + formatFileSize(bytesVerified.get()));
            System.out.println();

            boolean overallSuccess = filesWithErrors.get() == 0 && chunksWithErrors.get() == 0;

            if (overallSuccess) {
                System.out.println("✓ Snapshot verification PASSED - No integrity issues found");
            } else {
                System.out.println("✗ Snapshot verification FAILED - " + errors.get() + " error(s) found");
            }

            return overallSuccess;

        } catch (IOException e) {
            System.err.println("Error: Failed to verify snapshot: " + e.getMessage());
            return false;
        } catch (Exception e) {
            System.err.println("Error: Unexpected error during snapshot verification: " + e.getMessage());
            return false;
        } finally {
            // Clean up resources if we created them
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
            // Clean up Blake3Service if we created it (though it typically doesn't need explicit cleanup)
            if (createdBlake3Service && blake3Service != null) {
                try {
                    // Blake3Service doesn't have a close method, but we track it for consistency
                    // If Blake3Service later implements AutoCloseable, this will handle it
                    if (blake3Service instanceof AutoCloseable) {
                        ((AutoCloseable) blake3Service).close();
                    }
                } catch (Exception e) {
                    System.err.println("Warning: Failed to close BLAKE3 service: " + e.getMessage());
                }
            }
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
     * Verifies the file hash by reconstructing the file from chunks and computing its hash.
     *
     * @param file the file metadata to verify
     * @param contentStore the content store to retrieve chunks from
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