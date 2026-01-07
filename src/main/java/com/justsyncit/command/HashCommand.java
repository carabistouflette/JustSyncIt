package com.justsyncit.command;

import com.justsyncit.hash.Blake3Service;
import com.justsyncit.hash.HashingException;
import com.justsyncit.ApplicationInfoDisplay;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Command for hashing files.
 * Follows Single Responsibility Principle by focusing only on file hashing.
 */

public class HashCommand implements Command {

    /** Logger for hash command operations. */
    private static final Logger logger = LoggerFactory.getLogger(HashCommand.class);

    /** BLAKE3 service instance. */
    private final Blake3Service blake3Service;
    private final ApplicationInfoDisplay console;

    /**
     * Creates a new HashCommand.
     *
     * @param blake3Service the BLAKE3 service
     * @param console       the console display
     */
    public HashCommand(Blake3Service blake3Service, ApplicationInfoDisplay console) {
        this.blake3Service = blake3Service;
        this.console = console;
    }

    @Override
    public boolean execute(String[] args, CommandContext context) {
        // Handle help option
        if (args.length > 0 && args[0].equals("--help")) {
            displayHelp();
            return true;
        }

        if (args.length < 1) {
            console.displayError("Error: File path is required");
            console.displayError(getUsage());
            console.displayError("Use '--hash --help' for more information");
            return false;
        }

        String filePath = args[0];
        return hashFile(filePath);
    }

    @Override
    public String getName() {
        return "--hash";
    }

    @Override
    public String getDescription() {
        return "Calculate BLAKE3 hash of a file";
    }

    @Override
    public String getUsage() {
        return "--hash <file>";
    }

    /**
     * Hashes the specified file and displays the result.
     *
     * @param filePath the path to the file to hash
     * @return true if successful, false otherwise
     */
    private boolean hashFile(String filePath) {
        try {
            Path path = Paths.get(filePath);

            if (!validateFile(path, filePath)) {
                return false;
            }

            long startTime = System.currentTimeMillis();
            String hash = blake3Service.hashFile(path);
            long endTime = System.currentTimeMillis();
            long fileSize = Files.size(path);

            displayResult(filePath, fileSize, hash, endTime - startTime);
            return true;

        } catch (IOException | HashingException e) {
            console.displayError("Error hashing file: " + e.getMessage());
            logger.error("Error hashing file: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Validates that the file exists and is a regular file.
     *
     * @param path     the file path
     * @param filePath the file path string for error messages
     * @return true if valid, false otherwise
     */
    private boolean validateFile(Path path, String filePath) {
        if (!Files.exists(path)) {
            console.displayError("Error: File does not exist: " + filePath);
            logger.error("File does not exist: {}", filePath);
            return false;
        }

        if (!Files.isRegularFile(path)) {
            console.displayError("Error: Path is not a regular file: " + filePath);
            logger.error("Path is not a regular file: {}", filePath);
            return false;
        }

        return true;
    }

    /**
     * Displays the hash result.
     *
     * @param filePath   the file path
     * @param fileSize   the file size in bytes
     * @param hash       the computed hash
     * @param timeMillis the time taken in milliseconds
     */
    private void displayResult(String filePath, long fileSize, String hash, long timeMillis) {
        console.displayInfo("File: " + filePath);
        console.displayInfo("Size: " + formatFileSize(fileSize) + " (" + fileSize + " bytes)");
        console.displayInfo("BLAKE3 Hash: " + hash);
        console.displayInfo("Time: " + timeMillis + " ms");
    }

    /**
     * Displays help information for the hash command.
     */
    private void displayHelp() {
        console.displayInfo("Hash Command Help");
        console.displayInfo("=================");
        console.displayInfo("");
        console.displayInfo("Usage: " + getUsage());
        console.displayInfo("");
        console.displayInfo("Description:");
        console.displayInfo("  " + getDescription());
        console.displayInfo("");
        console.displayInfo("Arguments:");
        console.displayInfo("  file    Path to the file to hash");
        console.displayInfo("");
        console.displayInfo("Options:");
        console.displayInfo("  --help  Show this help message");
        console.displayInfo("");
        console.displayInfo("Examples:");
        console.displayInfo("  --hash /path/to/file.txt");
        console.displayInfo("  --hash document.pdf");
    }

    /**
     * Formats file size in human-readable format.
     *
     * @param bytes the size in bytes
     * @return formatted size string
     */
    private String formatFileSize(long bytes) {
        final long KB = 1024;
        final long MB = KB * 1024;
        final long GB = MB * 1024;

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
}