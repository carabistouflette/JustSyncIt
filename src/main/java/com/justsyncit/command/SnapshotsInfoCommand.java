package com.justsyncit.command;

import com.justsyncit.ServiceException;
import com.justsyncit.ServiceFactory;
import com.justsyncit.storage.metadata.FileMetadata;
import com.justsyncit.storage.metadata.MetadataService;
import com.justsyncit.storage.metadata.Snapshot;

import org.slf4j.Logger;
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
 * Follows Single Responsibility Principle by handling only snapshot information
 * display.
 */

public class SnapshotsInfoCommand implements Command {

    private static final long KB = 1024;
    private static final long MB = KB * 1024;
    private static final long GB = MB * 1024;
    private static final int DEFAULT_FILE_LIMIT = 20;
    private static final int PATH_COLUMN_WIDTH = 50;
    private static final int TABLE_WIDTH = 100;
    private static final int ELLIPSIS_LENGTH = 3;

    private static final Logger logger = org.slf4j.LoggerFactory.getLogger(SnapshotsInfoCommand.class);
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
        if (isHelpRequested(args)) {
            displayHelp();
            return true;
        }

        // Check for subcommand and snapshot ID
        // Check for snapshot ID
        if (args.length < 1) {
            logger.error("Missing snapshot ID. Args: {}", java.util.Arrays.toString(args));
            System.err.println("Error: Missing snapshot ID");
            System.err.println(getUsage());
            System.err.println("Use 'help snapshots info' for more information");
            return false;
        }

        String snapshotId = args[0];

        InfoOptions options;
        try {
            options = parseOptions(args);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid arguments provided: {}", e.getMessage());
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
                // Try to get from context first
                if (context != null) {
                    service = context.getMetadataService();
                }

                // If still null, create new one
                if (service == null) {
                    try {
                        localService = serviceFactory.createMetadataService();
                        service = localService;
                    } catch (ServiceException e) {
                        handleError("Failed to initialize metadata service", e, logger);
                        return false;
                    }
                }
            }

            try {
                return displaySnapshotInfo(service, snapshotId, options);
            } catch (IllegalArgumentException e) {
                System.err.println("Error: " + e.getMessage());
                return false;
            }

        } catch (IOException e) {
            handleError("Failed to get snapshot information for ID: " + snapshotId, e, logger);
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
    private InfoOptions parseOptions(String[] args) {
        boolean showFiles = false;
        int fileLimit = DEFAULT_FILE_LIMIT;
        boolean showStatistics = true;

        for (int i = 1; i < args.length; i++) {
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
                                throw new IllegalArgumentException("Error: File limit must be positive");
                            }
                            i++; // Skip the next argument
                        } catch (NumberFormatException e) {
                            throw new IllegalArgumentException("Error: Invalid file limit: " + args[i + 1]);
                        }
                    } else {
                        throw new IllegalArgumentException("Error: --file-limit requires a value");
                    }
                    break;
                case "--no-statistics":
                    showStatistics = false;
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

        return new InfoOptions(showFiles, fileLimit, showStatistics);
    }

    /**
     * Displays snapshot information.
     *
     * @param service    metadata service
     * @param snapshotId snapshot ID
     * @param options    info options
     * @return true if successful
     * @throws IOException if operation fails
     */
    private boolean displaySnapshotInfo(MetadataService service, String snapshotId, InfoOptions options)
            throws IOException {
        // Get snapshot information
        Optional<Snapshot> snapshotOpt = service.getSnapshot(snapshotId);
        if (snapshotOpt.isEmpty()) {
            System.err.println("Error: Snapshot not found: " + snapshotId);
            return false;
        }

        Snapshot snapshot = snapshotOpt.get();

        // Display basic snapshot information
        displayBasicInfo(snapshot);

        // Get files once and reuse
        List<FileMetadata> files = null;
        if (options.showStatistics || options.showFiles) {
            files = service.getFilesInSnapshot(snapshotId);
        }

        // Display statistics if requested
        if (options.showStatistics && files != null) {
            displayStatistics(files);
        }

        // Display file list if requested
        if (options.showFiles && files != null) {
            displayFileList(files, options.fileLimit);
        }

        return true;
    }

    /**
     * Displays basic snapshot information.
     *
     * @param snapshot the snapshot
     */
    private void displayBasicInfo(Snapshot snapshot) {
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
    }

    /**
     * Displays file statistics.
     *
     * @param files list of file metadata
     */
    private void displayStatistics(List<FileMetadata> files) {
        System.out.println();
        System.out.println("Statistics");
        System.out.println("----------");

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
            // In a real implementation, we might need to add file type information to
            // FileMetadata
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
        System.out.println("Average File Size: "
                + (totalRegularFiles > 0 ? formatFileSize(totalSize / totalRegularFiles) : "0 B"));

        if (!largestFile.isEmpty()) {
            System.out.println("Largest File: " + largestFile + " (" + formatFileSize(maxFileSize) + ")");
        }
        if (minFileSize != Long.MAX_VALUE && !smallestFile.isEmpty()) {
            System.out.println("Smallest File: " + smallestFile + " (" + formatFileSize(minFileSize) + ")");
        }
    }

    /**
     * Displays file list.
     *
     * @param files     list of file metadata
     * @param fileLimit maximum number of files to show
     */
    private void displayFileList(List<FileMetadata> files, int fileLimit) {
        System.out.println();
        System.out.println("Files (showing first " + Math.min(fileLimit, files.size()) + "):");
        System.out.println("------");

        int displayCount = Math.min(fileLimit, files.size());

        System.out.printf("%-50s %-8s %-12s %s%n", "Path", "Type", "Size", "Modified");
        System.out.println("-".repeat(TABLE_WIDTH));

        DateTimeFormatter fileFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT);

        for (int i = 0; i < displayCount; i++) {
            FileMetadata file = files.get(i);
            String type = "FILE"; // Since FileMetadata doesn't have type info
            String size = formatFileSize(file.getSize());
            ZonedDateTime fileZdt = ZonedDateTime.ofInstant(file.getModifiedTime(), ZoneId.systemDefault());

            System.out.printf("%-50s %-8s %-12s %s%n",
                    truncateString(file.getPath(), PATH_COLUMN_WIDTH),
                    type,
                    size,
                    fileZdt.format(fileFormatter));
        }

        if (files.size() > fileLimit) {
            System.out.println();
            System.out.println("... and " + (files.size() - fileLimit) + " more files");
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
                logger.warn("Failed to close resource", e);
            }
        }
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
        System.out.println(
                "  --file-limit NUM       Limit number of files to show (default: " + DEFAULT_FILE_LIMIT + ")");
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
        return new DecimalFormat("#,##0.#").format(value);
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
        return str.substring(0, maxLength - ELLIPSIS_LENGTH) + "...";
    }

    /**
     * Simple data class to hold info options.
     */
    private static class InfoOptions {
        final boolean showFiles;
        final int fileLimit;
        final boolean showStatistics;

        InfoOptions(boolean showFiles, int fileLimit, boolean showStatistics) {
            this.showFiles = showFiles;
            this.fileLimit = fileLimit;
            this.showStatistics = showStatistics;
        }
    }
}