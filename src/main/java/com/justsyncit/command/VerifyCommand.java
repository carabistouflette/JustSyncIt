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

import com.justsyncit.hash.Blake3Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Command for verifying file integrity.
 * Follows Single Responsibility Principle by focusing only on file
 * verification.
 */
public class VerifyCommand implements Command {

    /** Logger for verify command operations. */
    private static final Logger logger = LoggerFactory.getLogger(VerifyCommand.class);

    /** BLAKE3 hash length in characters (64 for 256-bit hash). */
    private static final int BLAKE3_HASH_LENGTH = 64;

    /** Valid characters for hexadecimal hash strings. */
    private static final String VALID_HASH_CHARS = "0123456789abcdefABCDEF";

    /**
     * Creates a new VerifyCommand.
     * Uses the Blake3Service from CommandContext instead of direct injection.
     */
    public VerifyCommand() {
        // Default constructor - service will be obtained from CommandContext
    }

    @Override
    public boolean execute(String[] args, CommandContext context) {
        // Check for help option first
        if (isHelpRequested(args)) {
            displayHelp();
            return true;
        }

        // Validate minimum arguments
        if (!validateMinArgs(args, 2, "--verify command requires a file path and expected hash")) {
            return false;
        }

        // Validate context
        if (context == null) {
            System.err.println("Error: Command context cannot be null");
            return false;
        }

        String filePath = args[0];
        String expectedHash = args[1];

        return verifyFileIntegrity(filePath, expectedHash, context);
    }

    @Override
    public String getName() {
        return "--verify";
    }

    @Override
    public String getDescription() {
        return "Verify file integrity against a hash";
    }

    @Override
    public String getUsage() {
        return "--verify <file> <hash>";
    }

    /**
     * Displays help information for the verify command.
     */
    private void displayHelp() {
        System.out.println("Verify Command - File Integrity Verification");
        System.out.println();
        System.out.println("Usage: " + getUsage());
        System.out.println();
        System.out.println("Description:");
        System.out.println("  " + getDescription());
        System.out.println();
        System.out.println("Arguments:");
        System.out.println("  <file>  Path to the file to verify");
        System.out.println("  <hash>  Expected BLAKE3 hash (64-character hexadecimal string)");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --help, -h  Display this help message");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  --verify /path/to/file.txt a1b2c3d4e5f6...");
        System.out.println("  --verify ./document.pdf 64-character-hash-here");
    }

    /**
     * Validates that the provided hash string is a valid BLAKE3 hash format.
     *
     * @param hash the hash string to validate
     * @return true if valid, false otherwise
     */
    private boolean isValidHashFormat(String hash) {
        if (hash == null || hash.length() != BLAKE3_HASH_LENGTH) {
            return false;
        }

        for (int i = 0; i < hash.length(); i++) {
            if (VALID_HASH_CHARS.indexOf(hash.charAt(i)) == -1) {
                return false;
            }
        }
        return true;
    }

    /**
     * Validates and sanitizes the file path to prevent path traversal attacks.
     *
     * @param filePath the file path to validate
     * @return the normalized Path object, or null if invalid
     */
    private Path validateAndNormalizePath(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            return null;
        }

        try {
            // Normalize the path to resolve any ".." or "." components
            Path normalizedPath = Paths.get(filePath.trim()).normalize();

            // Additional security check: ensure the path doesn't try to escape current
            // directory
            // (This is a basic check - adjust based on your security requirements)
            if (normalizedPath.toString().contains("..") && !filePath.contains("..")) {
                return null; // Suspicious path after normalization
            }

            return normalizedPath;
        } catch (InvalidPathException e) {
            return null;
        }
    }

    /**
     * Compares two hash strings in a timing-attack resistant manner.
     *
     * @param expectedHash the expected hash
     * @param actualHash   the actual hash
     * @return true if hashes are equal, false otherwise
     */
    private boolean constantTimeHashCompare(String expectedHash, String actualHash) {
        if (expectedHash == null || actualHash == null) {
            return expectedHash == actualHash;
        }

        if (expectedHash.length() != actualHash.length()) {
            return false;
        }

        // Use MessageDigest.isEqual for constant-time comparison
        try {
            return MessageDigest.isEqual(expectedHash.getBytes(), actualHash.getBytes());
        } catch (Exception e) {
            // Fallback to manual constant-time comparison
            int result = 0;
            for (int i = 0; i < expectedHash.length(); i++) {
                result |= expectedHash.charAt(i) ^ actualHash.charAt(i);
            }
            return result == 0;
        }
    }

    /**
     * Verifies file integrity against expected hash.
     *
     * @param filePath     path to file to verify
     * @param expectedHash expected hash value
     * @param context      the command context containing services
     * @return true if verification succeeds, false otherwise
     */
    private boolean verifyFileIntegrity(String filePath, String expectedHash, CommandContext context) {
        // Validate inputs
        if (!isValidHashFormat(expectedHash)) {
            System.err.println("Error: Invalid hash format. Expected 64-character hexadecimal string.");
            return false;
        }

        Path path = validateAndNormalizePath(filePath);
        if (path == null) {
            System.err.println("Error: Invalid file path: " + filePath);
            return false;
        }

        Blake3Service blake3Service = context.getBlake3Service();
        if (blake3Service == null) {
            System.err.println("Error: BLAKE3 service not available");
            return false;
        }

        try {
            // Check file existence and type
            if (!Files.exists(path)) {
                System.err.println("Error: File does not exist: " + path);
                return false;
            }

            if (!Files.isRegularFile(path)) {
                System.err.println("Error: Path is not a regular file: " + path);
                return false;
            }

            // Check file readability
            if (!Files.isReadable(path)) {
                System.err.println("Error: File is not readable: " + path);
                return false;
            }

            System.out.println("Verifying integrity of: " + path);
            System.out.println("Expected hash: " + expectedHash);

            long startTime = System.currentTimeMillis();
            String actualHash;

            actualHash = blake3Service.hashFile(path);

            long endTime = System.currentTimeMillis();

            boolean isValid = constantTimeHashCompare(expectedHash, actualHash);

            System.out.println("Actual hash:   " + actualHash);
            System.out.println("Result: " + (isValid ? "VALID" : "INVALID"));
            System.out.println("Time: " + (endTime - startTime) + " ms");

            if (isValid) {
                System.out.println("✓ File integrity is intact");
                logger.info("File integrity verification successful for: {}", path);
                return true;
            } else {
                System.out.println("✗ File integrity check FAILED - file may be corrupted");
                logger.warn("File integrity verification failed for: {}", path);
                return false;
            }

        } catch (IOException e) {
            System.err.println("Error reading file: " + e.getMessage());
            logger.error("IO error during file verification", e);
            return false;
        } catch (SecurityException e) {
            System.err.println("Error: Access denied to file: " + e.getMessage());
            logger.error("Security error during file verification", e);
            return false;
        } catch (Exception e) {
            System.err.println("Unexpected error during verification: " + e.getMessage());
            logger.error("Unexpected error during file verification", e);
            return false;
        }
    }
}